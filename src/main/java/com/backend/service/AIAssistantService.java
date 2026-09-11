package com.backend.service;

import com.backend.dto.AIChatRequestDTO;
import com.backend.dto.AIChatResponseDTO;
import com.backend.dto.AISlotDTO;
import com.backend.dto.PatientAppointmentPatternDTO;
import com.backend.model.*;
import com.backend.repository.*;
import com.backend.util.OrganizationTerminology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TwilioSmsService twilioSmsService;

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

        // 3. Resolve Target Organization and Service dynamically from User Intent &
        // Context
        ResolvedTarget target = resolveTarget(userText, conversation.getSummary(), request.getSelectedClinic(),
                request.getSelectedService());
        Tenant selectedTenant = target.tenant;
        String activeService = target.service;

        // Persist active tenant/service in conversation summary for multi-turn memory
        if (selectedTenant != null) {
            String tier = selectedTenant.getSubscriptionTier() != null ? selectedTenant.getSubscriptionTier() : "Starter";
            boolean isExpiredOrSuspended = "EXPIRED".equalsIgnoreCase(selectedTenant.getSubscriptionStatus()) 
                    || "SUSPENDED".equalsIgnoreCase(selectedTenant.getSubscriptionStatus())
                    || (selectedTenant.getSubscriptionExpiryDate() != null && LocalDate.now().isAfter(selectedTenant.getSubscriptionExpiryDate()));
            boolean hasAiBooking = !isExpiredOrSuspended && ("Professional".equalsIgnoreCase(tier) || "Enterprise".equalsIgnoreCase(tier));

            if (!hasAiBooking) {
                String orgName = selectedTenant.getOrganizationName();
                String msg = "Smart AI Booking is available for organizations on the Professional & Enterprise tiers. "
                        + orgName + " is currently on the " + tier + " Plan with standard calendar scheduling. Please switch to the Classic Calendar to book your slot.";
                
                AIChatMessage aiMsg = AIChatMessage.builder()
                        .conversation(conversation)
                        .role("ai")
                        .content(msg)
                        .actionSlotId(null)
                        .actionType("PLAN_RESTRICTION")
                        .isSummarized(false)
                        .build();
                chatMessageRepository.save(aiMsg);

                return AIChatResponseDTO.builder()
                        .conversationId(conversation.getConversationId())
                        .responseText(msg)
                        .actionSlotId(null)
                        .actionType("PLAN_RESTRICTION")
                        .recommendedSlots(Collections.emptyList())
                        .build();
            }

            String updatedSummary = updateConversationState(conversation.getSummary(), selectedTenant.getId(),
                    activeService);
            conversation.setSummary(updatedSummary);
        }

        String userEmail = conversation.getUserEmail();

        // 4. Retrieve user's actual database appointments (Ground Truth)
        List<Appointment> userAppointments = Collections.emptyList();
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            userAppointments = appointmentRepository
                    .findByPatientEmailOrderByAppointmentDateDesc(userEmail.trim().toLowerCase());
        }

        // 5. Analyze user appointment history and habits
        PatientAppointmentPatternDTO pattern = historyAnalyticsService.analyzePatientHistory(userEmail);

        // 6. Retrieve strictly verified available, non-conflicting bookable slots for
        // the RESOLVED tenant
        String targetClinicId = selectedTenant != null ? selectedTenant.getId().toString()
                : request.getSelectedClinic();
        List<AISlotDTO> availableSlots = slotAvailabilityService.getVerifiedAvailableSlots(
                pattern,
                targetClinicId,
                request.getSelectedProvider(),
                activeService);

        // 7. Construct multi-type organization human-like prompt with strict
        // anti-hallucination constraints
        String systemInstruction = buildSystemPrompt(conversation, pattern, availableSlots, selectedTenant,
                userAppointments);

        // 8. Retrieve active context (bounded strictly to the last 20 turns)
        List<Map<String, Object>> activeContents = tokenContextManagerService
                .getActiveContextAndMaintainSummary(conversation);

        // 9. Invoke Gemini 3.5 Flash-Lite (or fallback to intelligent conversational
        // state engine)
        Optional<String> geminiResult = geminiClientService.generateChatResponse(systemInstruction, activeContents);

        String responseText;
        String actionSlotId = null;
        String actionType = "INFO_ONLY";
        Object actionPayload = null;
        String matchReason = !availableSlots.isEmpty() ? availableSlots.get(0).getMatchReason() : null;

        if (geminiResult.isPresent() && !geminiResult.get().trim().isEmpty()) {
            responseText = geminiResult.get();

            // Extract slot recommendation action: [ACTION:SELECT_SLOT:X] or legacy
            // [BOOK_SLOT_ID:X]
            Pattern slotPattern = Pattern.compile("\\[(?:ACTION:SELECT_SLOT|BOOK_SLOT_ID):(\\d+)\\]");
            Matcher slotMatcher = slotPattern.matcher(responseText);
            if (slotMatcher.find()) {
                actionSlotId = slotMatcher.group(1);
                actionType = "SELECT_SLOT";
                responseText = responseText.replace(slotMatcher.group(0), "").trim();
            }

            // Extract cancellation action: [ACTION:CANCEL_APPOINTMENT:X]
            Pattern cancelPattern = Pattern.compile("\\[ACTION:CANCEL_APPOINTMENT:(\\d+)\\]");
            Matcher cancelMatcher = cancelPattern.matcher(responseText);
            if (cancelMatcher.find()) {
                String apptIdStr = cancelMatcher.group(1);
                responseText = responseText.replace(cancelMatcher.group(0), "").trim();
                try {
                    Long apptId = Long.parseLong(apptIdStr);
                    boolean cancelled = executeAppointmentCancellation(apptId, userEmail);
                    if (cancelled) {
                        actionType = "CANCEL_APPOINTMENT";
                        actionPayload = Map.of("appointmentId", apptId, "status", "CANCELLED");
                    }
                } catch (Exception e) {
                    log.error("Failed to execute cancellation for appointment {}: {}", apptIdStr, e.getMessage());
                }
            }

            // Extract reschedule action: [ACTION:RESCHEDULE:apptId:slotId]
            Pattern reschedulePattern = Pattern.compile("\\[ACTION:RESCHEDULE:(\\d+):(\\d+)\\]");
            Matcher reschedMatcher = reschedulePattern.matcher(responseText);
            if (reschedMatcher.find()) {
                String apptIdStr = reschedMatcher.group(1);
                String slotIdStr = reschedMatcher.group(2);
                responseText = responseText.replace(reschedMatcher.group(0), "").trim();
                actionType = "RESCHEDULE_APPOINTMENT";
                actionSlotId = slotIdStr;
                actionPayload = Map.of("appointmentId", apptIdStr, "newSlotId", slotIdStr);
            }

        } else {
            // Intelligent Human-Friendly Conversational & Entity-Aware Fallback Engine
            HumanResponse humanResp = buildHumanFriendlyFallbackResponse(
                    userText, pattern, availableSlots, selectedTenant, userAppointments, userEmail, activeService);
            responseText = humanResp.text;
            actionSlotId = humanResp.slotId;
            actionType = humanResp.actionType;
            actionPayload = humanResp.actionPayload;
        }

        // Clean up any remaining action tags or duplicate whitespace
        responseText = responseText.replaceAll("\\[ACTION:[A-Z_]+:[^\\]]+\\]", "")
                .replaceAll("\\[BOOK_SLOT_ID:\\d+\\]", "")
                .trim();

        // 10. Persist Model Response turn
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

        String orgTypeDisplay = selectedTenant != null ? selectedTenant.getOrganizationType() : "General";

        return AIChatResponseDTO.builder()
                .conversationId(conversation.getConversationId())
                .responseText(responseText)
                .actionSlotId(actionSlotId)
                .actionType(actionType)
                .actionPayload(actionPayload)
                .organizationType(orgTypeDisplay)
                .matchReason(matchReason)
                .recommendedSlots(availableSlots)
                .build();
    }

    /**
     * Recommends valid appointment slots tailored to user history.
     */
    public List<AISlotDTO> getRecommendations(String userEmail, String clinicId, String providerId, String service) {
        if (clinicId != null && !clinicId.trim().isEmpty()) {
            try {
                Long tid = Long.parseLong(clinicId.trim());
                Tenant t = tenantRepository.findById(tid).orElse(null);
                if (t != null) {
                    String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter";
                    boolean isExpiredOrSuspended = "EXPIRED".equalsIgnoreCase(t.getSubscriptionStatus()) 
                            || "SUSPENDED".equalsIgnoreCase(t.getSubscriptionStatus())
                            || (t.getSubscriptionExpiryDate() != null && LocalDate.now().isAfter(t.getSubscriptionExpiryDate()));
                    boolean hasAi = !isExpiredOrSuspended && ("Professional".equalsIgnoreCase(tier) || "Enterprise".equalsIgnoreCase(tier));
                    if (!hasAi) {
                        return Collections.emptyList();
                    }
                }
            } catch (Exception ignored) {}
        }
        PatientAppointmentPatternDTO pattern = historyAnalyticsService.analyzePatientHistory(userEmail);
        return slotAvailabilityService.getVerifiedAvailableSlots(pattern, clinicId, providerId, service);
    }

    private static class ResolvedTarget {
        Tenant tenant;
        String service;

        ResolvedTarget(Tenant tenant, String service) {
            this.tenant = tenant;
            this.service = service;
        }
    }

    /**
     * Intelligently discovers organization and service from user text or
     * conversation memory.
     */
    private ResolvedTarget resolveTarget(String userText, String summary, String requestClinicId,
            String requestService) {
        String lower = userText != null ? userText.toLowerCase() : "";
        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");

        Tenant detectedTenant = null;
        String detectedService = null;

        // 1. Direct match on organization name in user text
        for (Tenant t : activeTenants) {
            String orgName = t.getOrganizationName().toLowerCase();
            if (lower.contains(orgName)) {
                detectedTenant = t;
                break;
            }
        }

        // 2. Keyword industry match if name was not explicitly stated
        if (detectedTenant == null) {
            if (lower.contains("haircut") || lower.contains("styling") || lower.contains("hair")
                    || lower.contains("salon") || lower.contains("saloon") || lower.contains("spa")
                    || lower.contains("beard") || lower.contains("shave")) {
                detectedTenant = findTenantByType(activeTenants, "Saloon", "Salon");
                detectedService = "Initial Hair styling";
            } else if (lower.contains("college") || lower.contains("university") || lower.contains("advising")
                    || lower.contains("student") || lower.contains("lecture") || lower.contains("faculty")
                    || lower.contains("lab hour")) {
                detectedTenant = findTenantByType(activeTenants, "College", "Academy");
                detectedService = "Academic Advising";
            } else if (lower.contains("gym") || lower.contains("fitness") || lower.contains("workout")
                    || lower.contains("trainer")) {
                detectedTenant = findTenantByType(activeTenants, "Fitness", "Gym");
            } else if (lower.contains("eye") || lower.contains("lens") || lower.contains("cataract")
                    || lower.contains("vision")) {
                detectedTenant = findTenantByNameKeyword(activeTenants, "Eye");
                detectedService = "Contact Lens Examination";
            }
        }

        // 3. Fallback to conversation memory if user didn't mention an organization
        if (detectedTenant == null && summary != null) {
            Pattern p = Pattern.compile("\\[ACTIVE_TENANT:(\\d+)\\]");
            Matcher m = p.matcher(summary);
            if (m.find()) {
                try {
                    Long tid = Long.parseLong(m.group(1));
                    detectedTenant = tenantRepository.findById(tid).orElse(null);
                } catch (Exception ignored) {
                }
            }
        }

        // 4. Fallback to frontend dropdown selection
        if (detectedTenant == null && requestClinicId != null && !requestClinicId.trim().isEmpty()) {
            try {
                Long tid = Long.parseLong(requestClinicId.trim());
                detectedTenant = tenantRepository.findById(tid).orElse(null);
            } catch (Exception ignored) {
            }
        }

        // If service is not detected yet, check request or default
        if (detectedService == null && requestService != null && !requestService.trim().isEmpty()) {
            detectedService = requestService.trim();
        }

        return new ResolvedTarget(detectedTenant, detectedService);
    }

    private Tenant findTenantByType(List<Tenant> tenants, String... types) {
        for (String type : types) {
            for (Tenant t : tenants) {
                if (t.getOrganizationType() != null && t.getOrganizationType().equalsIgnoreCase(type)) {
                    return t;
                }
            }
        }
        return null;
    }

    private Tenant findTenantByNameKeyword(List<Tenant> tenants, String keyword) {
        for (Tenant t : tenants) {
            if (t.getOrganizationName() != null
                    && t.getOrganizationName().toLowerCase().contains(keyword.toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    private String updateConversationState(String summary, Long tenantId, String service) {
        String base = summary != null ? summary.replaceAll("\\[ACTIVE_TENANT:\\d+\\]", "").trim() : "";
        base += " [ACTIVE_TENANT:" + tenantId + "]";
        if (service != null && !service.isEmpty()) {
            base = base.replaceAll("\\[ACTIVE_SERVICE:[^\\]]+\\]", "").trim();
            base += " [ACTIVE_SERVICE:" + service + "]";
        }
        return base.trim();
    }

    private boolean executeAppointmentCancellation(Long appointmentId, String userEmail) {
        Optional<Appointment> apptOpt = appointmentRepository.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            log.warn("Cancellation failed: appointment #{} not found.", appointmentId);
            return false;
        }
        Appointment appt = apptOpt.get();

        if (userEmail != null && !userEmail.trim().equalsIgnoreCase(appt.getPatientEmail())) {
            log.warn("Cancellation rejected: appointment #{} belongs to {} but requested by {}",
                    appointmentId, appt.getPatientEmail(), userEmail);
            return false;
        }

        appt.setAppointmentStatus("CANCELLED");
        appointmentRepository.save(appt);

        try {
            notificationService.notifyAppointmentCancelled(appt);
            twilioSmsService.sendAppointmentStatusSms(appt, "CANCELLED");
        } catch (Exception e) {
            log.error("Failed to send cancellation notification for appointment #{}: {}", appointmentId,
                    e.getMessage());
        }

        log.info("Appointment #{} cancelled successfully via AI assistant by {}", appointmentId, userEmail);
        return true;
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

    private String buildSystemPrompt(
            AIConversation conversation,
            PatientAppointmentPatternDTO pattern,
            List<AISlotDTO> slots,
            Tenant selectedTenant,
            List<Appointment> userAppointments) {

        StringBuilder sb = new StringBuilder();

        // 1. Role and Core Identity
        sb.append(
                "You are OmniBook's friendly, warm, empathetic, and intelligent Appointment Assistant powered by Gemini 3.5 Flash-Lite.\n");
        sb.append(
                "OmniBook is a unified multi-organization appointment management platform supporting Clinics & Hospitals, Salons & Spas, Colleges & Universities, Fitness Centers, and Professional Consulting Offices.\n\n");

        // 2. Organization Context & Terminology
        if (selectedTenant != null) {
            OrganizationTerminology terms = OrganizationTerminology.from(selectedTenant.getOrganizationType());
            sb.append("CURRENT TARGET ORGANIZATION CONTEXT:\n");
            sb.append("- Organization Name: ").append(selectedTenant.getOrganizationName()).append("\n");
            sb.append("- Organization Type: ").append(selectedTenant.getOrganizationType()).append(" (")
                    .append(terms.getCategory()).append(")\n");
            sb.append("- Address: ")
                    .append(selectedTenant.getAddress() != null ? selectedTenant.getAddress() : "Main Branch")
                    .append("\n");
            sb.append("- Role Terminology: Address service staff as '").append(terms.getProviderTerm())
                    .append("', clients as '").append(terms.getCustomerTerm())
                    .append("', and bookings as '").append(terms.getAppointmentTerm()).append("'.\n\n");
        } else {
            sb.append("CURRENT ORGANIZATION CONTEXT:\n");
            sb.append("- Multi-Organization Portal: The user has not selected a specific organization yet.\n");
            sb.append(
                    "- Available Organization Types: Clinics/Hospitals, Salons & Spas, Colleges & Academies, Fitness Centers, and Consulting Firms.\n");
            sb.append("- Guide the user step-by-step to choose an organization or service.\n\n");
        }

        // 3. Conversational Guidelines
        sb.append("CONVERSATIONAL RULES & HUMAN-LIKE BEHAVIOR:\n");
        sb.append("1. GREETINGS & CASUAL OPENINGS:\n");
        sb.append(
                "   - When the user sends greetings or casual messages ('Hi', 'Hey', 'Good morning', 'Good afternoon', 'Good evening', 'How are you?', 'What's up?', 'Are you there?', 'Can you help me?', 'I need some help'):\n");
        sb.append("   - Respond warmly, naturally, and conversationally like a helpful human receptionist.\n");
        sb.append("   - Match the time of day if they say Good morning/afternoon/evening.\n");
        sb.append(
                "   - Ask how you can assist them with booking, rescheduling, checking, or cancelling an appointment.\n");
        sb.append("   - CRITICAL: NEVER dump a list of slots on a simple greeting or general question!\n\n");

        sb.append("2. STEP-BY-STEP MISSING INFORMATION GATHERING:\n");
        sb.append(
                "   - When a user says 'I need a haircut at Ram Saloon' without giving a date/time, DO NOT dump unrelated slots. Acknowledge Ram Saloon and ask what day and time (morning, afternoon, evening) they prefer!\n");
        sb.append(
                "   - Ask for missing details one step at a time: organization/service -> preferred day/time -> verified open slots.\n\n");

        sb.append("3. APPOINTMENT OPERATIONS (BOOK, RESCHEDULE, CANCEL, STATUS):\n");
        sb.append(
                "   - View Upcoming: If user asks 'What are my appointments?' or 'Check status', summarize their real appointments from the USER'S EXISTING APPOINTMENTS section below.\n");
        sb.append("   - Cancel: If user asks to cancel an appointment:\n");
        sb.append("     - If multiple appointments exist, ask which one they want to cancel.\n");
        sb.append(
                "     - When the user confirms ('Yes, cancel appointment #X'), output '[ACTION:CANCEL_APPOINTMENT:X]' in your response so the backend executes the cancellation.\n");
        sb.append("   - Recommending a slot to book: Include '[ACTION:SELECT_SLOT:X]' where X is the slot number.\n\n");

        sb.append("4. CRITICAL ANTI-HALLUCINATION POLICY:\n");
        sb.append("   - You must NEVER fabricate or assume appointment dates, times, providers, or slots.\n");
        sb.append("   - Real-time database availability and records below are the SOLE source of truth.\n");
        sb.append(
                "   - If a requested time is not in the inventory, inform the user honestly and suggest the closest available verified slot.\n\n");

        // 4. User's Existing Appointments (Real-time DB Ground Truth)
        sb.append("USER'S EXISTING APPOINTMENTS (Real-time database records):\n");
        LocalDate today = LocalDate.now();
        List<Appointment> upcoming = userAppointments.stream()
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()))
                .collect(Collectors.toList());

        if (upcoming.isEmpty()) {
            sb.append("- The user currently has no active upcoming appointments scheduled.\n\n");
        } else {
            for (Appointment a : upcoming) {
                sb.append("- Appointment ID ").append(a.getId())
                        .append(": ").append(a.getServiceName() != null ? a.getServiceName() : "Service")
                        .append(" on ").append(a.getAppointmentDate())
                        .append(" at ")
                        .append(a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, 5)
                                : "TBD")
                        .append(" [Status: ").append(a.getAppointmentStatus()).append("]")
                        .append(" [Ref: ").append(a.getTransactionId() != null ? a.getTransactionId() : a.getId())
                        .append("]\n");
            }
            sb.append("\n");
        }

        // 5. User Background & Patterns
        sb.append("USER APPOINTMENT PATTERNS & HABITS:\n");
        if (pattern.isHasHistory()) {
            sb.append("- Historical Patterns: ").append(pattern.getPatternSummary()).append("\n");
            sb.append(
                    "- When booking is requested, subtly prioritize slots that match their preferred days and times.\n\n");
        } else {
            sb.append("- User has no previous appointment records. Welcome them warmly.\n\n");
        }

        // 6. Memory of Previous Conversation Turns
        if (conversation.getSummary() != null && !conversation.getSummary().trim().isEmpty()) {
            sb.append("PREVIOUS CONVERSATION CONTEXT:\n");
            sb.append(conversation.getSummary()).append("\n\n");
        }

        // 7. Real-Time Verified Available Slots
        sb.append("VERIFIED REAL-TIME AVAILABLE SLOTS (Actual schedule, unbooked slots):\n");
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
        String actionType = "INFO_ONLY";
        Object actionPayload = null;

        HumanResponse(String text, String slotId) {
            this.text = text;
            this.slotId = slotId;
        }

        HumanResponse(String text, String slotId, String actionType, Object actionPayload) {
            this.text = text;
            this.slotId = slotId;
            this.actionType = actionType;
            this.actionPayload = actionPayload;
        }
    }

    /**
     * Highly natural, empathetic, and human-like conversational engine.
     * Accurately parses user intents, gathers missing info step-by-step, and
     * manages booking operations.
     */
    private HumanResponse buildHumanFriendlyFallbackResponse(
            String userMessage,
            PatientAppointmentPatternDTO pattern,
            List<AISlotDTO> slots,
            Tenant selectedTenant,
            List<Appointment> userAppointments,
            String userEmail,
            String targetService) {

        String msg = userMessage != null ? userMessage.trim().toLowerCase() : "";
        String orgName = selectedTenant != null ? selectedTenant.getOrganizationName() : "OmniBook";
        OrganizationTerminology terms = selectedTenant != null
                ? OrganizationTerminology.from(selectedTenant.getOrganizationType())
                : OrganizationTerminology.from("General");

        // 1. Time-of-day greetings
        if (msg.contains("good morning")) {
            return new HumanResponse("Good morning! ☀️ How can I help you with your "
                    + terms.getAppointmentTerm().toLowerCase() + " today?", null);
        }
        if (msg.contains("good afternoon")) {
            return new HumanResponse("Good afternoon! 🌤️ How can I assist you with your "
                    + terms.getAppointmentTerm().toLowerCase() + " today?", null);
        }
        if (msg.contains("good evening")) {
            return new HumanResponse("Good evening! 🌙 How can I assist you with your schedule or bookings today?",
                    null);
        }

        // 2. Casual openings / Greetings
        if (msg.matches("^(hi|hello|hey|heya|howdy|namaste|greetings|sup|what's up|whats up)[!.]?$")) {
            if (selectedTenant != null) {
                return new HumanResponse(
                        "Hello! 👋 Welcome to " + orgName
                                + ". I'm your AI Booking Assistant. Would you like to schedule a new "
                                + terms.getAppointmentTerm().toLowerCase()
                                + ", check upcoming bookings, or reschedule an existing one?",
                        null);
            } else {
                return new HumanResponse(
                        "Hello! 👋 Welcome to OmniBook. I can help you schedule, check, or reschedule appointments across our clinics, salons, colleges, and studios. Which organization or service would you like to book today?",
                        null);
            }
        }

        // 3. Are you there / Can you help me
        if (msg.contains("are you there") || msg.contains("can you help") || msg.contains("need some help")
                || msg.contains("need help")) {
            return new HumanResponse(
                    "Yes, I'm right here and ready to help! 😊 I can help you find available "
                            + terms.getProviderTerm().toLowerCase()
                            + "s, schedule a new " + terms.getAppointmentTerm().toLowerCase()
                            + ", check your upcoming appointments, or cancel and reschedule. What would you like to do?",
                    null);
        }

        // 4. How are you / Politeness
        if (msg.contains("how are you") || msg.contains("how r u") || msg.contains("how are you doing")
                || msg.contains("how's it going")) {
            return new HumanResponse(
                    "I'm doing great, thank you for asking! 😊 How can I help you today? Would you like to check available slots, book an appointment, or review your schedule?",
                    null);
        }

        // 5. Check Upcoming Appointments
        if (msg.contains("upcoming") || msg.contains("my appointment") || msg.contains("check status")
                || msg.contains("my booking")) {
            LocalDate today = LocalDate.now();
            List<Appointment> upcoming = userAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                    .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()))
                    .collect(Collectors.toList());

            if (upcoming.isEmpty()) {
                return new HumanResponse(
                        "You currently have no upcoming appointments scheduled with " + orgName
                                + ". Would you like to schedule one now?",
                        null);
            } else {
                StringBuilder sb = new StringBuilder("Here are your upcoming appointments:\n");
                for (Appointment a : upcoming) {
                    sb.append("• #").append(a.getId()).append(": ").append(a.getServiceName())
                            .append(" on ").append(a.getAppointmentDate())
                            .append(" at ")
                            .append(a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, 5)
                                    : "")
                            .append(" (Status: ").append(a.getAppointmentStatus()).append(")\n");
                }
                sb.append("\nWould you like to reschedule or cancel any of these?");
                return new HumanResponse(sb.toString(), null, "APPOINTMENT_LIST", upcoming);
            }
        }

        // 6. Cancellation Request
        if (msg.contains("cancel")) {
            LocalDate today = LocalDate.now();
            List<Appointment> upcoming = userAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                    .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()))
                    .collect(Collectors.toList());

            Pattern idPat = Pattern.compile("(?:#|id\\s*|appointment\\s*)(\\d+)");
            Matcher idMat = idPat.matcher(msg);
            if (idMat.find()) {
                try {
                    Long targetId = Long.parseLong(idMat.group(1));
                    boolean cancelled = executeAppointmentCancellation(targetId, userEmail);
                    if (cancelled) {
                        return new HumanResponse(
                                "Your appointment #" + targetId
                                        + " has been successfully cancelled. We have updated your schedule and notified the provider.",
                                null, "CANCEL_APPOINTMENT", Map.of("appointmentId", targetId, "status", "CANCELLED"));
                    }
                } catch (Exception ignored) {
                }
            }

            if (upcoming.isEmpty()) {
                return new HumanResponse("You don't have any active appointments to cancel right now.", null);
            } else if (upcoming.size() == 1) {
                Appointment a = upcoming.get(0);
                if (msg.contains("yes") || msg.contains("confirm")) {
                    executeAppointmentCancellation(a.getId(), userEmail);
                    return new HumanResponse(
                            "Your appointment #" + a.getId() + " for " + a.getServiceName() + " on "
                                    + a.getAppointmentDate() + " has been cancelled.",
                            null, "CANCEL_APPOINTMENT", Map.of("appointmentId", a.getId(), "status", "CANCELLED"));
                } else {
                    return new HumanResponse(
                            "You have an upcoming appointment for " + a.getServiceName() + " on "
                                    + a.getAppointmentDate()
                                    + " at "
                                    + (a.getAppointmentTime() != null
                                            ? a.getAppointmentTime().toString().substring(0, 5)
                                            : "")
                                    + ". Are you sure you would like to cancel it?",
                            null);
                }
            } else {
                StringBuilder sb = new StringBuilder("You have " + upcoming.size()
                        + " upcoming appointments. Which one would you like to cancel?\n");
                for (Appointment a : upcoming) {
                    sb.append("• Appointment #").append(a.getId()).append(" for ").append(a.getServiceName())
                            .append(" on ").append(a.getAppointmentDate()).append("\n");
                }
                return new HumanResponse(sb.toString(), null);
            }
        }

        // 7. Affirmation to book (e.g. "yes", "sure", "book it", "confirm", "yes,
        // please book that slot")
        if (msg.matches(
                "^(yes|yeah|sure|ok|okay|sounds good|book it|book that|reserve it|confirm|great|perfect|yes please|select it)[!.]?$")
                || msg.contains("book that slot") || msg.contains("book that") || msg.contains("book this")
                || msg.contains("select that slot") || msg.contains("select this") || msg.contains("yes, please")
                || msg.contains("yes please")) {
            if (!slots.isEmpty()) {
                AISlotDTO top = slots.get(0);
                return new HumanResponse(
                        "Perfect! I've selected the " + top.getDate() + " at " + top.getTime() + " slot with "
                                + top.getProvider()
                                + ". Click 'Select & Book Slot' below to add it directly to your appointment cart!",
                        top.getId(), "SELECT_SLOT", Map.of("slotId", top.getId()));
            } else {
                return new HumanResponse(
                        "I'd be happy to help you book! Which " + terms.getServiceTerm().toLowerCase()
                                + " or " + terms.getProviderTerm().toLowerCase() + " are you looking for?",
                        null);
            }
        }

        // 8. Booking Intent & Step-by-Step Questioning
        boolean mentionsTiming = msg.contains("morning") || msg.contains("afternoon") || msg.contains("evening")
                || msg.contains("monday") || msg.contains("tuesday") || msg.contains("wednesday")
                || msg.contains("thursday") || msg.contains("friday") || msg.contains("saturday")
                || msg.contains("sunday")
                || msg.contains("today") || msg.contains("tomorrow") || msg.contains("next week")
                || msg.contains(" am") || msg.contains(" pm") || msg.matches(".*\\b\\d{1,2}(?::\\d{2})?\\b.*");

        boolean isBookingInquiry = msg.contains("book") || msg.contains("need") || msg.contains("want")
                || msg.contains("schedule") || msg.contains("appointment") || msg.contains("haircut")
                || msg.contains("styling") || msg.contains("session") || msg.contains("advising")
                || msg.contains("exam");

        // STEP: User stated organization or service, but has NOT provided timing:
        if (isBookingInquiry && !mentionsTiming) {
            String servDisplay = "appointment";
            if (msg.contains("haircut") || msg.contains("hair") || msg.contains("styling")) {
                servDisplay = "haircut and styling";
            } else if (targetService != null && !targetService.isEmpty()) {
                servDisplay = targetService.toLowerCase();
            } else {
                servDisplay = terms.getServiceTerm().toLowerCase();
            }
            return new HumanResponse(
                    "Great! " + orgName + " offers " + servDisplay
                            + ". What day and time do you prefer—morning, afternoon, or evening?",
                    null);
        }

        // 9. Timing preference provided: Filter matching slots!
        if (mentionsTiming && !slots.isEmpty()) {
            AISlotDTO matchedSlot = findMatchingSlot(slots, msg);
            if (matchedSlot != null) {
                return new HumanResponse(
                        orgName + " has an opening on " + matchedSlot.getDate() + " at " + matchedSlot.getTime()
                                + " for " + matchedSlot.getTitle() + " with " + matchedSlot.getProvider()
                                + " (" + matchedSlot.getPrice() + "). Would you like to select this time?",
                        matchedSlot.getId(), "SELECT_SLOT", Map.of("slotId", matchedSlot.getId()));
            }
        }

        // 10. Default Slot Suggestion
        if (slots.isEmpty()) {
            return new HumanResponse(
                    "I checked our live schedule for " + orgName
                            + ", but there are no open slots matching your criteria for the next 14 days. Would you like to select a different date or staff member?",
                    null);
        }

        AISlotDTO selectedSlot = slots.get(0);
        StringBuilder response = new StringBuilder();
        response.append(orgName).append(" has an opening on ")
                .append(selectedSlot.getDate()).append(" at ").append(selectedSlot.getTime())
                .append(" for ").append(selectedSlot.getTitle())
                .append(" with ").append(selectedSlot.getProvider())
                .append(" (").append(selectedSlot.getPrice()).append(").");

        if (selectedSlot.getMatchReason() != null && !selectedSlot.getMatchReason().isEmpty()) {
            response.append(" ").append(selectedSlot.getMatchReason());
        }
        response.append(" Would you like to select this time?");

        return new HumanResponse(response.toString(), selectedSlot.getId(), "SELECT_SLOT",
                Map.of("slotId", selectedSlot.getId()));
    }

    private AISlotDTO findMatchingSlot(List<AISlotDTO> slots, String message) {
        String lower = message.toLowerCase();
        for (AISlotDTO s : slots) {
            String day = s.getDate().toLowerCase(); // e.g. "mon, sep 14"
            String time = s.getTime().toLowerCase(); // e.g. "10:00 am"

            // Check day match
            boolean dayMatch = false;
            if (lower.contains("mon") && day.contains("mon"))
                dayMatch = true;
            else if (lower.contains("tue") && day.contains("tue"))
                dayMatch = true;
            else if (lower.contains("wed") && day.contains("wed"))
                dayMatch = true;
            else if (lower.contains("thu") && day.contains("thu"))
                dayMatch = true;
            else if (lower.contains("fri") && day.contains("fri"))
                dayMatch = true;
            else if (lower.contains("sat") && day.contains("sat"))
                dayMatch = true;
            else if (lower.contains("sun") && day.contains("sun"))
                dayMatch = true;
            else if (lower.contains("tomorrow") || lower.contains("today") || lower.contains("next"))
                dayMatch = true;

            // Check time of day match
            boolean timeMatch = false;
            if (lower.contains("morning")
                    && (time.contains("am") || time.startsWith("0") || time.startsWith("10") || time.startsWith("11")))
                timeMatch = true;
            else if (lower.contains("afternoon")
                    && (time.contains("pm") && !time.startsWith("6") && !time.startsWith("7") && !time.startsWith("8")))
                timeMatch = true;
            else if (lower.contains("evening")
                    && (time.startsWith("5:") || time.startsWith("6:") || time.startsWith("7:")))
                timeMatch = true;

            // Or specific hour match
            if (lower.contains("10:00") && time.contains("10:00"))
                timeMatch = true;
            else if (lower.contains("10 am") && time.contains("10:00"))
                timeMatch = true;
            else if (lower.contains("11:00") && time.contains("11:00"))
                timeMatch = true;
            else if (lower.contains("2:00") && time.contains("2:00"))
                timeMatch = true;

            if (dayMatch && timeMatch) {
                return s;
            }
        }
        // Fallback to first slot matching day or time
        for (AISlotDTO s : slots) {
            if (lower.contains("morning") && s.getTime().contains("am"))
                return s;
            if (lower.contains("afternoon") && s.getTime().contains("pm"))
                return s;
        }
        return slots.get(0);
    }
}
