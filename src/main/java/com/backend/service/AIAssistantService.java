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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

        // 3. Resolve Target Organization and Service dynamically from User Intent & Multi-Turn Context
        ResolvedTarget target = resolveTarget(userText, conversation.getSummary(), request.getSelectedClinic(),
                request.getSelectedService());
        Tenant selectedTenant = target.tenant;
        String activeService = target.service;

        // Persist active category/tenant/service/provider, date, time and current stage in conversation summary
        String updatedSummary = updateConversationState(
                conversation.getSummary(),
                target.category,
                selectedTenant != null ? selectedTenant.getId() : null,
                activeService,
                target.provider != null ? target.provider.getId() : null,
                target.date,
                target.time,
                target.step);
        conversation.setSummary(updatedSummary);

        // Check plan restriction if tenant is selected
        if (selectedTenant != null && !isAiEnabled(selectedTenant) && target.step >= 3) {
            String orgName = selectedTenant.getOrganizationName();
            String tier = selectedTenant.getSubscriptionTier() != null ? selectedTenant.getSubscriptionTier() : "Starter";
            String msg = "This organization (" + orgName + ") is currently on the " + tier 
                    + " Plan (Classic Calendar scheduling only). Please switch to the Classic Calendar tab to book with " + orgName 
                    + ", or I can help you find available appointments at our partner organizations that support Smart AI Booking.";
            
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
                    .currentStep(3)
                    .quickReplies(List.of("Switch to Classic Calendar", "Choose another organization", "Start Over"))
                    .build();
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

        // 6. Retrieve strictly verified available, non-conflicting bookable slots ONLY if at step 4 or 5
        ZoneId ZONE_KATHMANDU = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(ZONE_KATHMANDU);

        String targetProviderId = target.provider != null ? target.provider.getId().toString()
                : request.getSelectedProvider();

        List<AISlotDTO> availableSlots = Collections.emptyList();
        if (target.step >= 4 && selectedTenant != null && isAiEnabled(selectedTenant)) {
            availableSlots = slotAvailabilityService.getVerifiedAvailableSlots(
                    pattern,
                    selectedTenant.getId().toString(),
                    targetProviderId,
                    activeService,
                    target.category,
                    target.date != null ? target.date : (target.isAskingToday ? today : null));
        }

        // 7. Construct multi-type organization human-like prompt with strict anti-hallucination constraints
        String systemInstruction = buildSystemPrompt(conversation, pattern, availableSlots, selectedTenant,
                userAppointments, target);

        // 8. Retrieve active context (bounded strictly to the last 20 turns)
        List<Map<String, Object>> activeContents = tokenContextManagerService
                .getActiveContextAndMaintainSummary(conversation);

        // 9. Invoke Gemini 3.5 Flash-Lite (or fallback to intelligent conversational state engine)
        Optional<String> geminiResult = geminiClientService.generateChatResponse(systemInstruction, activeContents);

        String responseText;
        String actionSlotId = null;
        String actionType = "INFO_ONLY";
        Object actionPayload = null;
        String matchReason = !availableSlots.isEmpty() ? availableSlots.get(0).getMatchReason() : null;
        int finalStep = target.step;
        List<String> finalQuickReplies = target.quickReplies;

        if (geminiResult.isPresent() && !geminiResult.get().trim().isEmpty()) {
            responseText = geminiResult.get();

            // Extract slot recommendation action: [ACTION:SELECT_SLOT:X] or legacy [BOOK_SLOT_ID:X]
            Pattern slotPattern = Pattern.compile("\\[(?:ACTION:SELECT_SLOT|BOOK_SLOT_ID):(\\d+)\\]");
            Matcher slotMatcher = slotPattern.matcher(responseText);
            if (slotMatcher.find()) {
                actionSlotId = slotMatcher.group(1);
                actionType = "SELECT_SLOT";
                finalStep = 5;
                finalQuickReplies = List.of("Select & Book Slot", "Choose another slot");
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
                    userText, pattern, availableSlots, selectedTenant, userAppointments, userEmail, activeService, target);
            responseText = humanResp.text;
            actionSlotId = humanResp.slotId;
            actionType = humanResp.actionType;
            actionPayload = humanResp.actionPayload;
            finalStep = humanResp.currentStep;
            finalQuickReplies = humanResp.quickReplies;
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

        String orgTypeDisplay = selectedTenant != null ? selectedTenant.getOrganizationType() 
                : (target.category != null ? target.category : "General");

        List<AISlotDTO> slotsToReturn = availableSlots;
        if (finalStep < 5 || isGreetingOrCasual(userText) || "PLAN_RESTRICTION".equals(actionType)) {
            slotsToReturn = Collections.emptyList();
        }

        return AIChatResponseDTO.builder()
                .conversationId(conversation.getConversationId())
                .responseText(responseText)
                .actionSlotId(actionSlotId)
                .actionType(actionType)
                .actionPayload(actionPayload)
                .organizationType(orgTypeDisplay)
                .matchReason(matchReason)
                .recommendedSlots(slotsToReturn)
                .currentStep(finalStep)
                .quickReplies(finalQuickReplies)
                .build();
    }

    /**
     * Recommends valid appointment slots tailored to user history.
     */
    public boolean isAiEnabled(Tenant t) {
        if (t == null) return false;
        String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter";
        boolean isExpiredOrSuspended = "EXPIRED".equalsIgnoreCase(t.getSubscriptionStatus()) 
                || "SUSPENDED".equalsIgnoreCase(t.getSubscriptionStatus())
                || (t.getSubscriptionExpiryDate() != null && LocalDate.now().isAfter(t.getSubscriptionExpiryDate()));
        return !isExpiredOrSuspended && ("Professional".equalsIgnoreCase(tier) || "Enterprise".equalsIgnoreCase(tier));
    }

    private boolean isGreetingOrCasual(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.matches(".*\\b(hi|hello|hey|heya|howdy|namaste|greetings|good\\s+(morning|afternoon|evening)|sup|what's up|whats up)\\b.*")
                || t.contains("how are you") || t.contains("are you there") || t.contains("can you help");
    }

    /**
     * Recommends valid appointment slots tailored to user history.
     */
    public List<AISlotDTO> getRecommendations(String userEmail, String clinicId, String providerId, String service) {
        if (clinicId != null && !clinicId.trim().isEmpty()) {
            try {
                Long tid = Long.parseLong(clinicId.trim());
                Tenant t = tenantRepository.findById(tid).orElse(null);
                if (t != null && !isAiEnabled(t)) {
                    return Collections.emptyList();
                }
            } catch (Exception ignored) {}
        }
        PatientAppointmentPatternDTO pattern = historyAnalyticsService.analyzePatientHistory(userEmail);
        return slotAvailabilityService.getVerifiedAvailableSlots(pattern, clinicId, providerId, service);
    }

    private static class ResolvedTarget {
        String category; // "College", "Clinic", "Saloon", "Fitness"
        Tenant tenant;
        String location;
        String service;
        User provider;
        LocalDate date;
        String time;
        boolean isAskingToday;
        int step;
        List<String> quickReplies;

        ResolvedTarget(String category, Tenant tenant, String location, String service, User provider, LocalDate date, String time, boolean isAskingToday, int step, List<String> quickReplies) {
            this.category = category;
            this.tenant = tenant;
            this.location = location;
            this.service = service;
            this.provider = provider;
            this.date = date;
            this.time = time;
            this.isAskingToday = isAskingToday;
            this.step = step;
            this.quickReplies = quickReplies;
        }
    }

    private static boolean matchOrgType(String tType, String cat) {
        if (tType == null || cat == null) return false;
        String t = tType.trim().toLowerCase();
        String c = cat.trim().toLowerCase();
        if (c.contains("college") || c.contains("acad") || c.contains("educ") || c.contains("school")) {
            return t.contains("college") || t.contains("acad") || t.contains("educ") || t.contains("school");
        }
        if (c.contains("salon") || c.contains("saloon") || c.contains("spa") || c.contains("beauty")) {
            return t.contains("salon") || t.contains("saloon") || t.contains("spa") || t.contains("beauty");
        }
        if (c.contains("gym") || c.contains("fitness")) {
            return t.contains("gym") || t.contains("fitness");
        }
        if (c.contains("clinic") || c.contains("hosp") || c.contains("health") || c.contains("medic")) {
            return t.contains("clinic") || t.contains("hosp") || t.contains("health") || t.contains("medic");
        }
        return t.equalsIgnoreCase(c);
    }

    private static String getCategoryLabel(String cat) {
        if (cat == null) return "Organization";
        String c = cat.toLowerCase();
        if (c.contains("college") || c.contains("acad") || c.contains("educ") || c.contains("school")) return "Education / College";
        if (c.contains("salon") || c.contains("saloon") || c.contains("spa") || c.contains("beauty")) return "Beauty / Salon & Spa";
        if (c.contains("gym") || c.contains("fitness")) return "Fitness & Gym";
        if (c.contains("clinic") || c.contains("hosp") || c.contains("health") || c.contains("medic")) return "Healthcare / Clinic";
        return cat;
    }

    private String findMatchingService(List<String> services, String... keywords) {
        for (String kw : keywords) {
            for (String s : services) {
                if (s.toLowerCase().contains(kw.toLowerCase())) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Resolves conversational booking state strictly across 5 sequential steps:
     * Step 1: Select Organization Type (Category)
     * Step 2: Select Organization or Location
     * Step 3: Select Service
     * Step 4: Display Available Dates & Time Slots
     * Step 5: Pick Slot & Proceed to Payment
     *
     * Guarantees ZERO cross-organization interference.
     */
    private ResolvedTarget resolveTarget(String userText, String summary, String requestClinicId,
            String requestService) {
        String lower = userText != null ? userText.toLowerCase() : "";

        boolean isAskingToday = lower.matches(".*\\b(today|tonight|right now|this morning|this afternoon|this evening)\\b.*");

        String prevCategory = null;
        Long prevTenantId = null;
        String prevService = null;
        Long prevProviderId = null;
        LocalDate prevDate = null;
        String prevTime = null;
        int prevStage = 1;

        if (summary != null) {
            Matcher mc = Pattern.compile("\\[ACTIVE_CATEGORY:([^\\]]+)\\]").matcher(summary);
            if (mc.find()) prevCategory = mc.group(1).trim();

            Matcher mt = Pattern.compile("\\[ACTIVE_TENANT:(\\d+)\\]").matcher(summary);
            if (mt.find()) {
                try { prevTenantId = Long.parseLong(mt.group(1)); } catch (Exception ignored) {}
            }

            Matcher ms = Pattern.compile("\\[ACTIVE_SERVICE:([^\\]]+)\\]").matcher(summary);
            if (ms.find()) prevService = ms.group(1).trim();

            Matcher mp = Pattern.compile("\\[ACTIVE_PROVIDER:(\\d+)\\]").matcher(summary);
            if (mp.find()) {
                try { prevProviderId = Long.parseLong(mp.group(1)); } catch (Exception ignored) {}
            }

            Matcher md = Pattern.compile("\\[ACTIVE_DATE:([^\\]]+)\\]").matcher(summary);
            if (md.find()) {
                try { prevDate = LocalDate.parse(md.group(1).trim()); } catch (Exception ignored) {}
            }

            Matcher mtime = Pattern.compile("\\[ACTIVE_TIME:([^\\]]+)\\]").matcher(summary);
            if (mtime.find()) {
                prevTime = mtime.group(1).trim();
            }

            Matcher mst = Pattern.compile("\\[STAGE:(\\d+)\\]").matcher(summary);
            if (mst.find()) {
                try { prevStage = Integer.parseInt(mst.group(1)); } catch (Exception ignored) {}
            }
        }

        // Check if user wants to reset / start over
        if (lower.contains("start over") || lower.contains("restart") || lower.contains("reset") || lower.contains("book another")) {
            return new ResolvedTarget(null, null, null, null, null, null, null, false, 1, 
                    List.of("Education / College", "Healthcare / Clinic", "Beauty / Salon & Spa", "Fitness & Gym"));
        }

        // Check if user wants to change date or time
        if (lower.contains("change date") || lower.contains("different date") || lower.contains("another date") || lower.contains("pick another date") || lower.contains("choose another date")) {
            prevDate = null;
            prevTime = null;
        }
        if (lower.contains("change time") || lower.contains("different time") || lower.contains("another time") || lower.contains("choose another time")) {
            prevTime = null;
        }

        // 1. Detect Category in user text
        String detectedCategory = null;
        if (lower.contains("college") || lower.contains("university") || lower.contains("academic") 
                || lower.contains("education") || lower.contains("student") || lower.contains("school") || lower.contains("academy")) {
            detectedCategory = "College";
        } else if (lower.contains("salon") || lower.contains("saloon") || lower.contains("haircut") 
                || lower.contains("styling") || lower.contains("spa") || lower.contains("beauty") 
                || lower.contains("beard") || lower.contains("shave") || lower.contains("facial")) {
            detectedCategory = "Saloon";
        } else if (lower.contains("gym") || lower.contains("fitness") || lower.contains("workout") 
                || lower.contains("trainer") || lower.contains("coach")) {
            detectedCategory = "Fitness";
        } else if (lower.contains("clinic") || lower.contains("hospital") || lower.contains("doctor") 
                || lower.contains("healthcare") || lower.contains("medical") || lower.contains("patient") 
                || lower.contains("eye hospital") || lower.contains("dentist")) {
            detectedCategory = "Clinic";
        }

        // Handle category switching: If user explicitly switches category, clear previous tenant, service, date, time!
        if (detectedCategory != null && prevCategory != null && !matchOrgType(prevCategory, detectedCategory)) {
            prevTenantId = null;
            prevService = null;
            prevProviderId = null;
            prevDate = null;
            prevTime = null;
        }

        String activeCategory = detectedCategory != null ? detectedCategory : prevCategory;

        // 2. Detect location in user text
        String detectedLocation = null;
        String[] locations = {"lalitpur", "kathmandu", "bhaktapur", "biratnagar", "siraha", "janakpur", "saptari", "khotang", "lamjung", "surkhet", "pokhara", "chitwan"};
        for (String loc : locations) {
            if (lower.contains(loc)) {
                detectedLocation = loc;
                break;
            }
        }

        // 3. Detect organization name
        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");
        Tenant detectedTenant = null;

        // Direct name match on active tenants (strictly filtered by active category if known)
        for (Tenant t : activeTenants) {
            if (activeCategory != null && !matchOrgType(t.getOrganizationType(), activeCategory)) {
                continue; // Prevent cross-category interference!
            }
            String orgName = t.getOrganizationName().toLowerCase().trim();
            if (lower.contains(orgName)) {
                detectedTenant = t;
                break;
            }
        }

        if (detectedTenant == null && prevTenantId != null) {
            Tenant prevT = tenantRepository.findById(prevTenantId).orElse(null);
            if (prevT != null) {
                if (activeCategory == null || matchOrgType(prevT.getOrganizationType(), activeCategory)) {
                    detectedTenant = prevT;
                }
            }
        }

        if (detectedTenant != null && prevTenantId != null && !detectedTenant.getId().equals(prevTenantId)) {
            prevService = null;
            prevProviderId = null;
            prevDate = null;
            prevTime = null;
        }

        if (detectedTenant != null && activeCategory == null) {
            activeCategory = detectedTenant.getOrganizationType();
        }

        // 4. Detect provider
        User detectedProvider = null;
        if (detectedTenant != null) {
            List<User> providers = userRepository.findByTenantIdAndRole(detectedTenant.getId(), "service_provider");
            for (User p : providers) {
                String pName = p.getFullName().toLowerCase();
                String cleaned = pName.replaceAll("^(dr\\.?|prof\\.?|mr\\.?|ms\\.?|mrs\\.?)\\s+", "").trim();
                if (lower.contains(pName) || (cleaned.length() >= 3 && lower.contains(cleaned))) {
                    detectedProvider = p;
                    break;
                }
            }
        }
        if (detectedProvider == null && prevProviderId != null && detectedTenant != null) {
            detectedProvider = userRepository.findById(prevProviderId).orElse(null);
        }

        // 5. Detect service
        String detectedService = null;
        if (detectedTenant != null) {
            List<String> distinctServices = providerServiceRepository.findDistinctServiceNamesByTenantId(detectedTenant.getId());
            for (String sName : distinctServices) {
                if (lower.contains(sName.toLowerCase())) {
                    detectedService = sName;
                    break;
                }
            }

            if (detectedService == null) {
                String orgType = detectedTenant.getOrganizationType() != null ? detectedTenant.getOrganizationType() : "";
                if (matchOrgType(orgType, "College")) {
                    if (lower.contains("advising") || lower.contains("academic")) {
                        detectedService = findMatchingService(distinctServices, "advising", "academic");
                    } else if (lower.contains("consultation") || lower.contains("faculty")) {
                        detectedService = findMatchingService(distinctServices, "consultation", "faculty");
                    } else if (lower.contains("career") || lower.contains("counseling")) {
                        detectedService = findMatchingService(distinctServices, "career", "counseling");
                    }
                } else if (matchOrgType(orgType, "Saloon")) {
                    if (lower.contains("haircut") || lower.contains("hair") || lower.contains("styling")) {
                        detectedService = findMatchingService(distinctServices, "hair", "styling");
                    } else if (lower.contains("facial") || lower.contains("spa")) {
                        detectedService = findMatchingService(distinctServices, "facial", "spa");
                    }
                } else if (matchOrgType(orgType, "Clinic")) {
                    if (lower.contains("checkup") || lower.contains("joint") || lower.contains("check")) {
                        detectedService = findMatchingService(distinctServices, "checkup", "joint", "check");
                    }
                }
            }
        }

        if (detectedService == null && prevService != null) {
            detectedService = prevService;
        }

        if (detectedService != null && prevService != null && !detectedService.equalsIgnoreCase(prevService)) {
            prevDate = null;
            prevTime = null;
        }

        // 6. Detect Date and Time
        ZoneId ZONE_KATHMANDU = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(ZONE_KATHMANDU);

        LocalDate detectedDate = parseUserDate(userText, today);
        if (detectedDate == null) {
            detectedDate = prevDate;
        }

        String detectedTime = parseUserTime(userText);
        if (detectedTime == null) {
            detectedTime = prevTime;
        }

        // Determine Conversational Step (1 to 5)
        int step = 1;
        List<String> quickReplies = new ArrayList<>();

        if (activeCategory == null && detectedTenant == null) {
            step = 1;
            quickReplies = List.of("Education / College", "Healthcare / Clinic", "Beauty / Salon & Spa", "Fitness & Gym");
        } else if (detectedTenant == null) {
            step = 2;
            final String finalCat = activeCategory;
            final String finalLoc = detectedLocation;
            List<Tenant> catTenants = tenantRepository.findByStatus("ACTIVE").stream()
                    .filter(t -> matchOrgType(t.getOrganizationType(), finalCat))
                    .filter(t -> finalLoc == null || (t.getAddress() != null && t.getAddress().toLowerCase().contains(finalLoc)))
                    .collect(Collectors.toList());
            quickReplies = catTenants.stream().map(Tenant::getOrganizationName).collect(Collectors.toList());
            if (quickReplies.isEmpty()) {
                quickReplies = tenantRepository.findByStatus("ACTIVE").stream()
                        .filter(t -> matchOrgType(t.getOrganizationType(), finalCat))
                        .map(Tenant::getOrganizationName)
                        .collect(Collectors.toList());
            }
        } else if (detectedService == null) {
            step = 3;
            quickReplies = providerServiceRepository.findDistinctServiceNamesByTenantId(detectedTenant.getId());
            if (quickReplies.isEmpty()) {
                quickReplies = List.of("General Consultation");
            }
        } else if (detectedDate == null) {
            // STEP 4: Choose Date (Allow any future date, list real upcoming open operating dates)
            step = 4;
            List<LocalDate> openDates = slotAvailabilityService.getAvailableDatesForService(
                    detectedTenant.getId(),
                    detectedProvider != null ? detectedProvider.getId() : null,
                    detectedService);
            DateTimeFormatter chipFmt = DateTimeFormatter.ofPattern("EEE, MMM d");
            quickReplies = new ArrayList<>();
            for (int i = 0; i < Math.min(6, openDates.size()); i++) {
                LocalDate d = openDates.get(i);
                if (d.isEqual(today)) {
                    quickReplies.add("Today (" + d.format(DateTimeFormatter.ofPattern("MMM d")) + ")");
                } else if (d.isEqual(today.plusDays(1))) {
                    quickReplies.add("Tomorrow (" + d.format(DateTimeFormatter.ofPattern("MMM d")) + ")");
                } else {
                    quickReplies.add(d.format(chipFmt));
                }
            }
            if (quickReplies.isEmpty()) {
                quickReplies = List.of("Today (" + today.format(DateTimeFormatter.ofPattern("MMM d")) + ")",
                        "Tomorrow (" + today.plusDays(1).format(DateTimeFormatter.ofPattern("MMM d")) + ")");
            }
        } else {
            // STEP 5: Date selected -> check time and move to payment
            step = 5;
            quickReplies = List.of("Proceed to Booking & Payment", "Change time", "Change date");
        }

        return new ResolvedTarget(activeCategory, detectedTenant, detectedLocation, detectedService, detectedProvider, detectedDate, detectedTime, isAskingToday, step, quickReplies);
    }

    private String updateConversationState(String summary, String category, Long tenantId, String service, Long providerId, LocalDate date, String time, int stage) {
        String base = summary != null ? summary : "";
        if (category != null) {
            base = base.replaceAll("\\[ACTIVE_CATEGORY:[^\\]]+\\]", "").trim();
            base += " [ACTIVE_CATEGORY:" + category + "]";
        }
        if (tenantId != null) {
            base = base.replaceAll("\\[ACTIVE_TENANT:\\d+\\]", "").trim();
            base += " [ACTIVE_TENANT:" + tenantId + "]";
        }
        if (service != null && !service.isEmpty()) {
            base = base.replaceAll("\\[ACTIVE_SERVICE:[^\\]]+\\]", "").trim();
            base += " [ACTIVE_SERVICE:" + service + "]";
        }
        if (providerId != null) {
            base = base.replaceAll("\\[ACTIVE_PROVIDER:\\d+\\]", "").trim();
            base += " [ACTIVE_PROVIDER:" + providerId + "]";
        }
        if (date != null) {
            base = base.replaceAll("\\[ACTIVE_DATE:[^\\]]+\\]", "").trim();
            base += " [ACTIVE_DATE:" + date.toString() + "]";
        }
        if (time != null && !time.isEmpty()) {
            base = base.replaceAll("\\[ACTIVE_TIME:[^\\]]+\\]", "").trim();
            base += " [ACTIVE_TIME:" + time + "]";
        }
        base = base.replaceAll("\\[STAGE:\\d+\\]", "").trim();
        base += " [STAGE:" + stage + "]";
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
            List<Appointment> userAppointments,
            ResolvedTarget target) {

        StringBuilder sb = new StringBuilder();

        // 1. Role and Core Identity
        sb.append(
                "You are OmniBook's friendly, warm, empathetic, and intelligent Appointment Assistant powered by Gemini 3.5 Flash-Lite.\n");
        sb.append(
                "OmniBook is a unified multi-organization appointment management platform supporting Clinics & Hospitals, Salons & Spas, Colleges & Universities, Fitness Centers, and Professional Consulting Offices.\n\n");

        // 2. Strict 5-Step Flow & Anti-Interference Isolation
        sb.append("MANDATORY 5-STEP CONVERSATIONAL WORKFLOW RULES (CRITICAL ANTI-INTERFERENCE):\n");
        sb.append("Current Flow Step: ").append(target.step).append(" of 5\n");
        sb.append("Active Category: ").append(target.category != null ? target.category : "Not chosen yet").append("\n");
        sb.append("STRICT ISOLATION: Zero organization interference allowed. If user selected or asked about College, NEVER mention Clinic or Salon! Never mix organizations.\n");
        sb.append("- Step 1: If no organization type is selected, warmly greet and ask user to choose Organization Type (Education / College, Healthcare / Clinic, Beauty / Salon & Spa, Fitness & Gym).\n");
        sb.append("- Step 2: Once Organization Type is chosen, ask for preferred Location or Organization Name and present organizations matching that category.\n");
        sb.append("- Step 3: Once a specific organization is chosen, present its available services and ask user to choose.\n");
        sb.append("- Step 4: Once service is chosen, present verified available dates and time slots strictly for that organization and service.\n");
        sb.append("- Step 5: When user picks a slot, confirm the appointment details and output '[ACTION:SELECT_SLOT:X]' to proceed to checkout and payment.\n\n");

        // 3. Organization Context & Terminology
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
            sb.append("- Multi-Organization Portal: User has not selected a specific organization yet.\n");
            sb.append("- Follow the 5-step flow to guide the user step-by-step.\n\n");
        }

        // 4. User's Existing Appointments
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
                        .append(a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, 5) : "TBD")
                        .append(" [Status: ").append(a.getAppointmentStatus()).append("]\n");
            }
            sb.append("\n");
        }

        // 5. Verified Real-Time Available Slots
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
        int currentStep = 1;
        List<String> quickReplies = Collections.emptyList();

        HumanResponse(String text, String slotId, String actionType, Object actionPayload, int currentStep, List<String> quickReplies) {
            this.text = text;
            this.slotId = slotId;
            this.actionType = actionType;
            this.actionPayload = actionPayload;
            this.currentStep = currentStep;
            this.quickReplies = quickReplies != null ? quickReplies : Collections.emptyList();
        }
    }

    /**
     * Highly natural, empathetic, and human-like conversational engine implementing
     * the strict 5-step sequential flow with zero organization interference.
     */
    private HumanResponse buildHumanFriendlyFallbackResponse(
            String userMessage,
            PatientAppointmentPatternDTO pattern,
            List<AISlotDTO> slots,
            Tenant selectedTenant,
            List<Appointment> userAppointments,
            String userEmail,
            String targetService,
            ResolvedTarget target) {

        String msg = userMessage != null ? userMessage.trim().toLowerCase() : "";
        OrganizationTerminology terms = selectedTenant != null
                ? OrganizationTerminology.from(selectedTenant.getOrganizationType())
                : OrganizationTerminology.from("General");

        String patientName = "";
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            patientName = userRepository.findByEmail(userEmail.trim().toLowerCase())
                    .map(User::getFullName)
                    .orElse("");
        }
        String salutation = (!patientName.isEmpty()) ? " " + patientName.split(" ")[0] : "";

        // Global Operation: Check Upcoming Appointments
        if (msg.contains("upcoming") || msg.contains("my appointment") || msg.contains("check status")
                || msg.contains("my booking")) {
            LocalDate today = LocalDate.now();
            List<Appointment> upcoming = userAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                    .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()))
                    .collect(Collectors.toList());

            if (upcoming.isEmpty()) {
                return new HumanResponse(
                        "You currently have no upcoming appointments scheduled. Would you like to schedule one now?",
                        null, "INFO_ONLY", null, 1, List.of("Education / College", "Healthcare / Clinic", "Beauty / Salon & Spa", "Fitness & Gym"));
            } else {
                StringBuilder sb = new StringBuilder("Here are your upcoming appointments:\n");
                for (Appointment a : upcoming) {
                    sb.append("• #").append(a.getId()).append(": ").append(a.getServiceName())
                            .append(" on ").append(a.getAppointmentDate())
                            .append(" at ")
                            .append(a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, 5) : "")
                            .append(" (Status: ").append(a.getAppointmentStatus()).append(")\n");
                }
                sb.append("\nWould you like to reschedule or cancel any of these?");
                return new HumanResponse(sb.toString(), null, "APPOINTMENT_LIST", upcoming, target.step, List.of("Cancel an appointment", "Start new booking"));
            }
        }

        // Global Operation: Cancellation Request
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
                                null, "CANCEL_APPOINTMENT", Map.of("appointmentId", targetId, "status", "CANCELLED"), target.step, List.of("Book new appointment"));
                    }
                } catch (Exception ignored) {}
            }

            if (upcoming.isEmpty()) {
                return new HumanResponse("You don't have any active appointments to cancel right now.", null, "INFO_ONLY", null, target.step, List.of("Book new appointment"));
            } else if (upcoming.size() == 1) {
                Appointment a = upcoming.get(0);
                if (msg.contains("yes") || msg.contains("confirm")) {
                    executeAppointmentCancellation(a.getId(), userEmail);
                    return new HumanResponse(
                            "Your appointment #" + a.getId() + " for " + a.getServiceName() + " on "
                                    + a.getAppointmentDate() + " has been cancelled.",
                            null, "CANCEL_APPOINTMENT", Map.of("appointmentId", a.getId(), "status", "CANCELLED"), target.step, List.of("Book new appointment"));
                } else {
                    return new HumanResponse(
                            "You have an upcoming appointment for " + a.getServiceName() + " on "
                                    + a.getAppointmentDate() + " at "
                                    + (a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, 5) : "")
                                    + ". Are you sure you would like to cancel it?",
                            null, "INFO_ONLY", null, target.step, List.of("Yes, cancel it", "No, keep it"));
                }
            }
        }

        // Reset / Start Over
        if (msg.contains("start over") || msg.contains("restart") || msg.contains("reset") || msg.contains("book another")) {
            return buildStep1Response(salutation);
        }

        // STEP 1: Select Organization Type (Category)
        if (target.step == 1) {
            return buildStep1Response(salutation);
        }

        // STEP 2: Select Organization or Location
        if (target.step == 2) {
            return buildStep2Response(target, salutation);
        }

        // STEP 3: Select Service
        if (target.step == 3 && selectedTenant != null) {
            return buildStep3Response(target, selectedTenant, terms);
        }

        // STEP 4: Choose Date
        if (target.step == 4 && selectedTenant != null) {
            return buildStep4Response(target, selectedTenant, terms);
        }

        // STEP 5: Date Check, Time Selection & Proceed to Payment
        if (target.step == 5 && selectedTenant != null) {
            return buildStep5Response(target, selectedTenant, terms, slots, userMessage);
        }

        // Default Fallback to Step 1
        return buildStep1Response(salutation);
    }

    private HumanResponse buildStep1Response(String salutation) {
        String text = "Hello" + salutation + "! 👋 Welcome to OmniBook AI Booking Assistant.\n\n"
                + "Please choose your **Organization Type** to get started:\n\n"
                + "1. 🎓 **Education / College** (Academic Advising, Faculty Consultation)\n"
                + "2. 🏥 **Healthcare / Clinic** (Doctors, Specialists, Medical Checkup)\n"
                + "3. 💇 **Beauty / Salon & Spa** (Haircut, Styling, Spa)\n"
                + "4. 🏋️ **Fitness & Gym** (Personal Training, Workouts)\n\n"
                + "Which organization type would you like to book with?";
        List<String> quickReplies = List.of(
                "Education / College",
                "Healthcare / Clinic",
                "Beauty / Salon & Spa",
                "Fitness & Gym"
        );
        return new HumanResponse(text, null, "INFO_ONLY", null, 1, quickReplies);
    }

    private HumanResponse buildStep2Response(ResolvedTarget target, String salutation) {
        String cat = target.category != null ? target.category : "Organization";
        String catLabel = getCategoryLabel(cat);

        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE").stream()
                .filter(t -> matchOrgType(t.getOrganizationType(), cat))
                .collect(Collectors.toList());

        if (target.location != null && !target.location.trim().isEmpty()) {
            List<Tenant> locFiltered = activeTenants.stream()
                    .filter(t -> t.getAddress() != null && t.getAddress().toLowerCase().contains(target.location.toLowerCase()))
                    .collect(Collectors.toList());
            if (!locFiltered.isEmpty()) {
                activeTenants = locFiltered;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You selected **").append(catLabel).append("**.\n\n");
        if (activeTenants.isEmpty()) {
            sb.append("Currently, there are no active organizations registered under this category. Would you like to check another category?");
            return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 1, 
                    List.of("Education / College", "Healthcare / Clinic", "Beauty / Salon & Spa", "Fitness & Gym"));
        }

        sb.append("Please select an organization or tell me your preferred location:\n\n");
        List<String> orgQuickReplies = new ArrayList<>();
        for (Tenant t : activeTenants) {
            boolean hasAi = isAiEnabled(t);
            sb.append("• **").append(t.getOrganizationName()).append("**");
            if (t.getAddress() != null && !t.getAddress().trim().isEmpty()) {
                sb.append(" — ").append(t.getAddress());
            }
            if (hasAi) {
                sb.append(" *(Smart AI Booking Available)*");
            } else {
                sb.append(" *(Classic Calendar)*");
            }
            sb.append("\n");
            orgQuickReplies.add(t.getOrganizationName());
        }

        sb.append("\nWhich organization would you like to book with?");
        return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 2, orgQuickReplies);
    }

    private HumanResponse buildStep3Response(ResolvedTarget target, Tenant selectedTenant, OrganizationTerminology terms) {
        String orgName = selectedTenant.getOrganizationName();
        boolean hasAi = isAiEnabled(selectedTenant);
        String tier = selectedTenant.getSubscriptionTier() != null ? selectedTenant.getSubscriptionTier() : "Starter";

        if (!hasAi) {
            String text = "**" + orgName + "** is currently on the **" + tier + " Plan** (Classic Calendar scheduling only).\n\n"
                    + "Please switch to the **Classic Calendar** tab to view available slots and book your appointment with " + orgName 
                    + ", or choose another organization that supports Smart AI Booking.";
            return new HumanResponse(text, null, "PLAN_RESTRICTION", null, 3, 
                    List.of("Switch to Classic Calendar", "Choose another organization", "Start Over"));
        }

        List<ProviderService> services = providerServiceRepository.findByTenantId(selectedTenant.getId()).stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .collect(Collectors.toList());

        if (services.isEmpty()) {
            String text = "**" + orgName + "** currently has no active services listed in the system. Would you like to choose another organization?";
            return new HumanResponse(text, null, "INFO_ONLY", null, 2, List.of("Choose another organization", "Start Over"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Welcome to **").append(orgName).append("**! Please choose a service:\n\n");
        List<String> serviceReplies = new ArrayList<>();
        for (ProviderService s : services) {
            sb.append("• **").append(s.getServiceName()).append("**");
            if (s.getFee() != null) {
                sb.append(" (रू ").append(String.format("%,d", s.getFee().longValue())).append(")");
            }
            if (s.getDurationMinutes() != null) {
                sb.append(" — ").append(s.getDurationMinutes()).append(" mins");
            }
            sb.append("\n");
            serviceReplies.add(s.getServiceName());
        }
        sb.append("\nWhich service would you like to schedule?");
        return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 3, serviceReplies);
    }

    private HumanResponse buildStep4Response(ResolvedTarget target, Tenant selectedTenant, OrganizationTerminology terms) {
        String orgName = selectedTenant.getOrganizationName();
        String serviceName = target.service != null ? target.service : "Appointment";

        Long providerId = target.provider != null ? target.provider.getId() : null;
        List<LocalDate> availableDates = slotAvailabilityService.getAvailableDatesForService(selectedTenant.getId(), providerId, serviceName);

        if (availableDates.isEmpty()) {
            String text = "I checked the live schedule for **" + serviceName + "** at **" + orgName + "**, but there are no open operating days available for the upcoming 14 days.\n\n"
                    + "Would you like to select a different service or check another organization?";
            return new HumanResponse(text, null, "INFO_ONLY", null, 3, 
                    List.of("Select another service", "Choose another organization", "Start Over"));
        }

        DateTimeFormatter dateDisplayFmt = DateTimeFormatter.ofPattern("EEE, MMM d");
        ZoneId ZONE_KATHMANDU = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(ZONE_KATHMANDU);

        StringBuilder sb = new StringBuilder();
        sb.append("You have selected **").append(serviceName).append("** at **").append(orgName).append("**! 🎓\n\n");
        sb.append("Please choose your preferred appointment date (or enter any upcoming date like Sep 13, Sep 14, Sep 15):\n\n");

        List<String> dateReplies = new ArrayList<>();
        for (int i = 0; i < Math.min(7, availableDates.size()); i++) {
            LocalDate d = availableDates.get(i);
            String label = d.format(dateDisplayFmt);
            if (d.isEqual(today)) {
                label = "Today (" + label + ")";
            } else if (d.isEqual(today.plusDays(1))) {
                label = "Tomorrow (" + label + ")";
            }
            sb.append("• 📅 **").append(label).append("**\n");
            dateReplies.add(d.isEqual(today) ? "Today (" + d.format(DateTimeFormatter.ofPattern("MMM d")) + ")" :
                    (d.isEqual(today.plusDays(1)) ? "Tomorrow (" + d.format(DateTimeFormatter.ofPattern("MMM d")) + ")" : d.format(dateDisplayFmt)));
        }

        sb.append("\nWhich date works best for you?");
        return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 4, dateReplies);
    }

    private HumanResponse buildStep5Response(ResolvedTarget target, Tenant selectedTenant, OrganizationTerminology terms, List<AISlotDTO> slots, String userMessage) {
        String orgName = selectedTenant.getOrganizationName();
        String serviceName = target.service != null ? target.service : "Appointment";
        Long providerId = target.provider != null ? target.provider.getId() : null;

        // 1. Check Date Availability
        if (target.date == null) {
            return buildStep4Response(target, selectedTenant, terms);
        }

        List<LocalDate> availableDates = slotAvailabilityService.getAvailableDatesForService(selectedTenant.getId(), providerId, serviceName);
        boolean isDateAvailable = availableDates.contains(target.date);

        if (!isDateAvailable) {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
            StringBuilder sb = new StringBuilder();
            sb.append("I checked the schedule for **").append(orgName).append("** on **").append(target.date.format(dateFmt)).append("**.\n\n");
            sb.append("⚠️ The clinic / ").append(terms.getProviderTerm().toLowerCase()).append(" is **not available on this date** (it is a non-working day or closed).\n\n");
            sb.append("Available upcoming dates are:\n");

            DateTimeFormatter chipFmt = DateTimeFormatter.ofPattern("EEE, MMM d");
            List<String> altDateReplies = new ArrayList<>();
            for (int i = 0; i < Math.min(6, availableDates.size()); i++) {
                LocalDate d = availableDates.get(i);
                sb.append("• 📅 **").append(d.format(chipFmt)).append("**\n");
                altDateReplies.add(d.format(chipFmt));
            }
            sb.append("\nPlease choose one of the available dates above:");
            return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 4, altDateReplies);
        }

        // 2. If user hasn't chosen a time or is asking for available times:
        AISlotDTO chosenSlot = findMatchingSlotForTime(slots, target.time, userMessage);

        String lower = userMessage != null ? userMessage.toLowerCase() : "";
        if (chosenSlot == null && !slots.isEmpty() && (lower.contains("proceed") || lower.contains("confirm") || lower.contains("book it") || lower.contains("select & book") || lower.contains("book appointment"))) {
            chosenSlot = slots.get(0);
        }

        if (chosenSlot == null) {
            if (slots.isEmpty()) {
                String text = "All time slots for **" + serviceName + "** at **" + orgName + "** on **"
                        + target.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")) + "** are currently booked or passed.\n\n"
                        + "Please choose another date:";
                DateTimeFormatter chipFmt = DateTimeFormatter.ofPattern("EEE, MMM d");
                List<String> dateReplies = availableDates.stream()
                        .filter(d -> !d.equals(target.date))
                        .limit(5)
                        .map(d -> d.format(chipFmt))
                        .collect(Collectors.toList());
                return new HumanResponse(text, null, "INFO_ONLY", null, 4, dateReplies);
            }

            StringBuilder sb = new StringBuilder();
            String dateLabel = target.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
            sb.append("Great news! **").append(orgName).append("** is **available on ").append(dateLabel).append("**! 📅\n\n");
            sb.append("Here are the available time slots for **").append(serviceName).append("** on this date:\n\n");

            List<String> timeReplies = new ArrayList<>();
            for (int i = 0; i < Math.min(10, slots.size()); i++) {
                AISlotDTO s = slots.get(i);
                sb.append("• **").append(s.getTime()).append("** — ").append(s.getProviderTitle())
                        .append(" [").append(s.getPrice()).append("]\n");
                timeReplies.add(s.getTime());
            }

            sb.append("\nPlease enter or select your preferred time:");
            timeReplies.add("Change date");
            return new HumanResponse(sb.toString(), null, "INFO_ONLY", null, 5, timeReplies);
        }

        // 3. Time slot matched and verified! Proceed to payment!
        String text = "🎉 **Slot Confirmed!**\n\n"
                + "• **Service**: " + chosenSlot.getTitle() + "\n"
                + "• **" + terms.getProviderTerm() + "**: " + chosenSlot.getProviderTitle() + "\n"
                + "• **Organization**: " + orgName + "\n"
                + "• **Date**: " + chosenSlot.getDate() + "\n"
                + "• **Time**: " + chosenSlot.getTime() + "\n"
                + "• **Fee**: " + chosenSlot.getPrice() + "\n\n"
                + "Click **'Proceed to Booking & Payment'** below to confirm your details and complete payment via **eSewa** or **Stripe**!";

        Map<String, Object> payload = Map.of(
                "slotId", chosenSlot.getId(),
                "date", chosenSlot.getRawDate(),
                "time", chosenSlot.getRawTime(),
                "price", chosenSlot.getPrice(),
                "service", chosenSlot.getTitle(),
                "provider", chosenSlot.getProvider()
        );

        return new HumanResponse(text, chosenSlot.getId(), "SELECT_SLOT", payload, 5, 
                List.of("Proceed to Booking & Payment", "Change time", "Change date"));
    }

    private AISlotDTO findMatchingSlotForTime(List<AISlotDTO> slots, String targetTime, String rawMessage) {
        if (slots == null || slots.isEmpty()) return null;

        String lowerMsg = rawMessage != null ? rawMessage.toLowerCase() : "";

        // Check if user specified "Slot X" or "#X"
        Matcher slotNumMat = Pattern.compile("(?:slot\\s*#?|#)(\\d+)").matcher(lowerMsg);
        if (slotNumMat.find()) {
            String sId = slotNumMat.group(1);
            for (AISlotDTO s : slots) {
                if (s.getId().equals(sId)) return s;
            }
        }

        // Check exact targetTime (HH:mm) against rawTime
        if (targetTime != null) {
            for (AISlotDTO s : slots) {
                if (s.getRawTime() != null && s.getRawTime().startsWith(targetTime)) {
                    return s;
                }
            }
        }

        // Check if rawMessage contains slot's display time (e.g. "9:30" or "09:30 am")
        for (AISlotDTO s : slots) {
            if (s.getTime() != null && lowerMsg.contains(s.getTime().toLowerCase())) {
                return s;
            }
            if (s.getRawTime() != null && lowerMsg.contains(s.getRawTime().substring(0, 5))) {
                return s;
            }
        }

        return null;
    }

    public static LocalDate parseUserDate(String text, LocalDate today) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.trim().toLowerCase();

        if (lower.matches(".*\\b(today|tonight|right now|this morning|this afternoon|this evening)\\b.*")) {
            return today;
        }
        if (lower.matches(".*\\b(tomorrow|tmrw)\\b.*")) {
            return today.plusDays(1);
        }
        if (lower.contains("day after tomorrow")) {
            return today.plusDays(2);
        }

        // Match ISO date YYYY-MM-DD
        Matcher isoMat = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b").matcher(lower);
        if (isoMat.find()) {
            try {
                return LocalDate.of(Integer.parseInt(isoMat.group(1)), Integer.parseInt(isoMat.group(2)), Integer.parseInt(isoMat.group(3)));
            } catch (Exception ignored) {}
        }

        // Match Month Day (e.g. Sep 13, Sep 14, Sep 15, September 14, Sept 15, 14 Sep, 15 Sept, 14th Sep)
        Pattern monthDayPat = Pattern.compile("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s*(\\d{1,2})(?:st|nd|rd|th)?\\b");
        Matcher m1 = monthDayPat.matcher(lower);
        if (m1.find()) {
            String mStr = m1.group(1);
            int day = Integer.parseInt(m1.group(2));
            int month = parseMonth(mStr);
            if (month > 0) {
                int year = today.getYear();
                LocalDate d = LocalDate.of(year, month, day);
                if (d.isBefore(today)) {
                    d = d.plusYears(1);
                }
                return d;
            }
        }

        // Match Day Month (e.g. 14 Sep, 15 September, 14th of September)
        Pattern dayMonthPat = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\b");
        Matcher m2 = dayMonthPat.matcher(lower);
        if (m2.find()) {
            int day = Integer.parseInt(m2.group(1));
            String mStr = m2.group(2);
            int month = parseMonth(mStr);
            if (month > 0) {
                int year = today.getYear();
                LocalDate d = LocalDate.of(year, month, day);
                if (d.isBefore(today)) {
                    d = d.plusYears(1);
                }
                return d;
            }
        }

        // Match Day of Week: (this/next) monday, tuesday, wednesday, thursday, friday, saturday, sunday
        Pattern dowPat = Pattern.compile("\\b(this\\s+|next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b");
        Matcher m3 = dowPat.matcher(lower);
        if (m3.find()) {
            String dowStr = m3.group(2);
            DayOfWeek dow = parseDayOfWeek(dowStr);
            if (dow != null) {
                for (int i = 0; i <= 7; i++) {
                    LocalDate candidate = today.plusDays(i);
                    if (candidate.getDayOfWeek() == dow) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private static int parseMonth(String mStr) {
        if (mStr.startsWith("jan")) return 1;
        if (mStr.startsWith("feb")) return 2;
        if (mStr.startsWith("mar")) return 3;
        if (mStr.startsWith("apr")) return 4;
        if (mStr.startsWith("may")) return 5;
        if (mStr.startsWith("jun")) return 6;
        if (mStr.startsWith("jul")) return 7;
        if (mStr.startsWith("aug")) return 8;
        if (mStr.startsWith("sep")) return 9;
        if (mStr.startsWith("oct")) return 10;
        if (mStr.startsWith("nov")) return 11;
        if (mStr.startsWith("dec")) return 12;
        return 0;
    }

    private static DayOfWeek parseDayOfWeek(String dowStr) {
        if (dowStr.startsWith("mon")) return DayOfWeek.MONDAY;
        if (dowStr.startsWith("tue")) return DayOfWeek.TUESDAY;
        if (dowStr.startsWith("wed")) return DayOfWeek.WEDNESDAY;
        if (dowStr.startsWith("thu")) return DayOfWeek.THURSDAY;
        if (dowStr.startsWith("fri")) return DayOfWeek.FRIDAY;
        if (dowStr.startsWith("sat")) return DayOfWeek.SATURDAY;
        if (dowStr.startsWith("sun")) return DayOfWeek.SUNDAY;
        return null;
    }

    public static String parseUserTime(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.trim().toLowerCase();

        // 1. Matches "10:30 AM", "9:00 AM", "09:00am", "2:30 pm", "14:00"
        Matcher timeWithPeriod = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b").matcher(lower);
        if (timeWithPeriod.find()) {
            int hour = Integer.parseInt(timeWithPeriod.group(1));
            int min = Integer.parseInt(timeWithPeriod.group(2));
            String period = timeWithPeriod.group(3);
            if ("pm".equalsIgnoreCase(period) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(period) && hour == 12) hour = 0;
            return String.format("%02d:%02d", hour, min);
        }

        // 2. Matches "10 am", "9 am", "2 pm", "3pm"
        Matcher hourOnly = Pattern.compile("\\b(\\d{1,2})\\s*(am|pm)\\b").matcher(lower);
        if (hourOnly.find()) {
            int hour = Integer.parseInt(hourOnly.group(1));
            String period = hourOnly.group(2);
            if ("pm".equalsIgnoreCase(period) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(period) && hour == 12) hour = 0;
            return String.format("%02d:00", hour);
        }

        return null;
    }
}
