package com.backend.service;

import com.backend.dto.AIChatRequestDTO;
import com.backend.dto.AIChatResponseDTO;
import com.backend.dto.AISlotDTO;
import com.backend.dto.PatientAppointmentPatternDTO;
import com.backend.model.AIChatMessage;
import com.backend.model.AIConversation;
import com.backend.repository.AIChatMessageRepository;
import com.backend.repository.AIConversationRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAssistantService {

    private final AIConversationRepository conversationRepository;
    private final AIChatMessageRepository chatMessageRepository;
    private final AppointmentHistoryAnalyticsService historyAnalyticsService;
    private final SlotAvailabilityService slotAvailabilityService;
    private final TokenContextManagerService tokenContextManagerService;
    private final GeminiClientService geminiClientService;
    private final UserRepository userRepository;

    @Transactional
    public AIChatResponseDTO getChatResponse(AIChatRequestDTO request) {
        // 1. Resolve or initialize persistent AI Conversation session
        AIConversation conversation = resolveConversation(request);

        String userText = request.getMessage() != null ? request.getMessage().trim() : "";

        // 2. Persist current incoming User message turn
        AIChatMessage userMsg = AIChatMessage.builder()
                .conversation(conversation)
                .role("user")
                .content(userText)
                .isSummarized(false)
                .build();
        chatMessageRepository.save(userMsg);

        // 3. Analyze patient appointment history and habits
        String patientEmail = conversation.getUserEmail();
        PatientAppointmentPatternDTO pattern = historyAnalyticsService.analyzePatientHistory(patientEmail);

        // 4. Retrieve strictly verified available, non-conflicting bookable slots
        List<AISlotDTO> availableSlots = slotAvailabilityService.getVerifiedAvailableSlots(
                pattern,
                request.getSelectedClinic(),
                request.getSelectedProvider(),
                request.getSelectedService());

        // 5. Construct human-like prompt with strict anti-hallucination constraints
        String systemInstruction = buildSystemPrompt(conversation, pattern, availableSlots);

        // 6. Retrieve active context (bounded strictly to the last 20 turns) and
        // maintain older summary
        List<Map<String, Object>> activeContents = tokenContextManagerService
                .getActiveContextAndMaintainSummary(conversation);

        // 7. Invoke Gemini 3.5 Flash-Lite
        Optional<String> geminiResult = geminiClientService.generateChatResponse(systemInstruction, activeContents);

        String responseText;
        String actionSlotId = null;
        String matchReason = !availableSlots.isEmpty() ? availableSlots.get(0).getMatchReason() : null;

        if (geminiResult.isPresent() && !geminiResult.get().trim().isEmpty()) {
            responseText = geminiResult.get();

            // Extract recommended slot tag if present: [BOOK_SLOT_ID:X]
            Pattern slotPattern = Pattern.compile("\\[BOOK_SLOT_ID:(\\d+)\\]");
            Matcher matcher = slotPattern.matcher(responseText);
            if (matcher.find()) {
                String slotIndexStr = matcher.group(1);
                actionSlotId = slotIndexStr; // Matches slot ID 1, 2, 3...
                responseText = responseText.replace(matcher.group(0), "").trim();
            }
        } else {
            // Intelligent Human-Friendly Fallback
            HumanResponse humanResp = buildHumanFriendlyFallbackResponse(userText, pattern, availableSlots);
            responseText = humanResp.text;
            actionSlotId = humanResp.slotId;
        }

        // Clean up any remaining tags or duplicate spaces
        responseText = responseText.replaceAll("\\[BOOK_SLOT_ID:\\d+\\]", "").trim();

        // 8. Persist Model Response turn
        AIChatMessage modelMsg = AIChatMessage.builder()
                .conversation(conversation)
                .role("model")
                .content(responseText)
                .actionSlotId(actionSlotId)
                .isSummarized(false)
                .build();
        chatMessageRepository.save(modelMsg);

        // Update conversation turn count & timestamp
        conversation.setTotalTurns(conversation.getTotalTurns() + 1);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return AIChatResponseDTO.builder()
                .conversationId(conversation.getConversationId())
                .responseText(responseText)
                .actionSlotId(actionSlotId)
                .matchReason(matchReason)
                .recommendedSlots(availableSlots)
                .build();
    }

    /**
     * Recommends valid appointment slots tailored to patient history.
     */
    public List<AISlotDTO> getRecommendations(String userEmail, String clinicId, String providerId, String service) {
        PatientAppointmentPatternDTO pattern = historyAnalyticsService.analyzePatientHistory(userEmail);
        return slotAvailabilityService.getVerifiedAvailableSlots(pattern, clinicId, providerId, service);
    }

    private AIConversation resolveConversation(AIChatRequestDTO request) {
        String convId = request.getConversationId();
        if (convId != null && !convId.trim().isEmpty()) {
            Optional<AIConversation> existing = conversationRepository.findByConversationId(convId.trim());
            if (existing.isPresent()) {
                AIConversation c = existing.get();
                if (request.getUserEmail() != null && (c.getUserEmail() == null || c.getUserEmail().isEmpty())) {
                    c.setUserEmail(request.getUserEmail());
                }
                return c;
            }
        }

        String newConvId = UUID.randomUUID().toString();
        AIConversation newConv = AIConversation.builder()
                .conversationId(newConvId)
                .userEmail(request.getUserEmail())
                .userId(request.getUserId())
                .totalTurns(0)
                .build();

        if (newConv.getUserEmail() != null && newConv.getUserId() == null) {
            userRepository.findByEmail(newConv.getUserEmail()).ifPresent(u -> newConv.setUserId(u.getId()));
        }

        return conversationRepository.save(newConv);
    }

    private String buildSystemPrompt(AIConversation conversation, PatientAppointmentPatternDTO pattern,
            List<AISlotDTO> slots) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "You are OmniBook's warm, friendly, empathetic, and human-like Medical & Appointment Scheduling Assistant powered by Gemini 3.5 Flash-Lite.\n");
        sb.append(
                "You chat naturally and conversationally, just like a kind, helpful healthcare receptionist at a top medical clinic.\n\n");

        sb.append("CONVERSATIONAL RULES & HUMAN-LIKE BEHAVIOR:\n");
        sb.append(
                "1. GREETINGS & CASUAL CHAT: When the patient says 'hi', 'hello', 'good morning', 'hey', or asks 'how are you', respond warmly, pleasantly, and conversationally like a human. Greet them back, ask how they are feeling, and invite them to share what they need help with. NEVER immediately dump a rigid slot recommendation on a simple greeting!\n");
        sb.append(
                "2. APPOINTMENT INQUIRIES: When the patient expresses a health concern, asks to book an appointment, inquires about doctors, or asks for recommended times, offer a thoughtful suggestion from the verified slot inventory.\n");
        sb.append(
                "3. NATURAL LANGUAGE: Keep your answers fluid, warm, and concise. Avoid robotic formulas or rigid bullet lists unless asked.\n");
        sb.append("4. CRITICAL ANTI-HALLUCINATION POLICY:\n");
        sb.append("   - You must NEVER invent, fabricate, or assume appointment dates, times, doctors, or slots.\n");
        sb.append(
                "   - You must ONLY recommend appointment slots from the VERIFIED AVAILABLE SLOTS inventory below.\n");
        sb.append(
                "   - When you recommend a slot, include '[BOOK_SLOT_ID:X]' in your answer, where X is the slot ID (e.g. [BOOK_SLOT_ID:1]).\n\n");

        // Patient background & behavioral patterns
        sb.append("PATIENT PROFILE & APPOINTMENT HABITS:\n");
        if (pattern.isHasHistory()) {
            sb.append("- Historical Habits: ").append(pattern.getPatternSummary()).append("\n");
            sb.append(
                    "- When booking is requested, subtly prioritize slots that match their preferred days and times.\n\n");
        } else {
            sb.append("- Patient has no previous appointment records. Welcome them warmly.\n\n");
        }

        // Summary of older conversation turns (if any)
        if (conversation.getSummary() != null && !conversation.getSummary().trim().isEmpty()) {
            sb.append("PREVIOUS CONVERSATION CONTEXT (Memory of earlier sessions):\n");
            sb.append(conversation.getSummary()).append("\n\n");
        }

        // Real-time verified available inventory
        sb.append("VERIFIED REAL-TIME AVAILABLE SLOTS (Actual clinic hours, unbooked slots):\n");
        if (slots.isEmpty()) {
            sb.append("Currently, there are no open slots matching the filter criteria for the upcoming 14 days.\n");
        } else {
            for (AISlotDTO slot : slots) {
                sb.append("Slot ").append(slot.getId()).append(": ")
                        .append(slot.getDate()).append(" at ").append(slot.getTime())
                        .append(" | ").append(slot.getTitle())
                        .append(" | ").append(slot.getProvider())
                        .append(" | ").append(slot.getPrice());
                if (slot.getMatchReason() != null && !slot.getMatchReason().isEmpty()) {
                    sb.append(" [Match: ").append(slot.getMatchReason()).append("]");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static class HumanResponse {
        String text;
        String slotId;

        HumanResponse(String text, String slotId) {
            this.text = text;
            this.slotId = slotId;
        }
    }

    /**
     * Highly natural, empathetic, and human-like fallback engine.
     * Accurately handles greetings, pleasantries, questions, affirmations, and
     * appointment bookings.
     */
    private HumanResponse buildHumanFriendlyFallbackResponse(String userMessage, PatientAppointmentPatternDTO pattern,
            List<AISlotDTO> slots) {
        String msg = userMessage != null ? userMessage.trim().toLowerCase() : "";

        // 1. Greetings
        if (msg.matches(
                "^(hi|hello|hey|heya|howdy|namaste|greetings|good morning|good afternoon|good evening|sup|what's up|whats up)[!.]?$")) {
            return new HumanResponse(
                    "Hello there! 👋 Welcome to OmniBook. I'm your AI health and booking assistant. How are you feeling today, or how can I help you with an appointment?",
                    null);
        }

        // 2. How are you / Politeness
        if (msg.contains("how are you") || msg.contains("how r u") || msg.contains("how are you doing")
                || msg.contains("how's it going")) {
            return new HumanResponse(
                    "I'm doing well, thank you so much for asking! 😊 How are you feeling today? Are you looking to schedule a consultation or check doctor availability?",
                    null);
        }

        // 3. Who are you / Capabilities
        if (msg.contains("who are you") || msg.contains("what can you do") || msg.contains("what are you")
                || msg.equals("help")) {
            return new HumanResponse(
                    "I'm your personal healthcare assistant on OmniBook! I can help you find specialist doctors, check real-time clinic openings, recommend appointment times based on your typical schedule, and guide you through booking. What can I help you with?",
                    null);
        }

        // 4. Gratitude / Thanks
        if (msg.contains("thank you") || msg.contains("thanks") || msg.contains("thx") || msg.contains("appreciate")) {
            return new HumanResponse(
                    "You're very welcome! If you need anything else or have questions about your appointment, feel free to ask anytime. Wishing you great health! 🌟",
                    null);
        }

        // 5. Goodbyes
        if (msg.contains("bye") || msg.contains("goodbye") || msg.contains("see you") || msg.contains("cya")) {
            return new HumanResponse(
                    "Goodbye! Take care of yourself, and have a wonderful day ahead! 👋",
                    null);
        }

        // 6. Affirmation to book (e.g. "yes", "sure", "book it", "sounds good", "okay")
        if (msg.matches(
                "^(yes|yeah|sure|ok|okay|sounds good|book it|book that|reserve it|confirm|great|perfect|yes please)[!.]?$")) {
            if (!slots.isEmpty()) {
                AISlotDTO top = slots.get(0);
                return new HumanResponse(
                        "Great! I've selected the " + top.getDate() + " at " + top.getTime() + " slot with "
                                + top.getProvider()
                                + ". You can click 'Book Now' below to add it directly to your appointment cart.",
                        top.getId());
            } else {
                return new HumanResponse(
                        "I'd love to help you book, but I don't see any open slots matching that right now. Could you let me know which doctor or service you are looking for?",
                        null);
            }
        }

        // 7. Appointment / Doctor / Booking request
        if (slots.isEmpty()) {
            return new HumanResponse(
                    "I checked our live schedule across the clinics, but there are no open appointments matching your criteria for the next two weeks. Would you like to try selecting a different doctor or clinic location?",
                    null);
        }

        // Find best slot matching user query if specific keywords are present
        AISlotDTO selectedSlot = slots.get(0);
        for (AISlotDTO s : slots) {
            String combined = (s.getTitle() + " " + s.getProvider() + " " + s.getDate()).toLowerCase();
            if (msg.contains("dental") && combined.contains("dental")) {
                selectedSlot = s;
                break;
            } else if (msg.contains("blood pressure") && combined.contains("blood pressure")) {
                selectedSlot = s;
                break;
            } else if (msg.contains("consultation") && combined.contains("consultation")) {
                selectedSlot = s;
                break;
            }
        }

        StringBuilder response = new StringBuilder();
        response.append("I'd be happy to help with that! ");
        response.append(selectedSlot.getProvider()).append(" has an opening on ")
                .append(selectedSlot.getDate()).append(" at ").append(selectedSlot.getTime())
                .append(" for ").append(selectedSlot.getTitle()).append(" (").append(selectedSlot.getPrice())
                .append(").");

        if (selectedSlot.getMatchReason() != null && !selectedSlot.getMatchReason().isEmpty()) {
            response.append(" ").append(selectedSlot.getMatchReason());
        }

        response.append(" Would you like to reserve this time?");

        return new HumanResponse(response.toString(), selectedSlot.getId());
    }
}
