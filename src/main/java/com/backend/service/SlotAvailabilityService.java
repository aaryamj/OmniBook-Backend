package com.backend.service;

import com.backend.dto.AISlotDTO;
import com.backend.dto.PatientAppointmentPatternDTO;
import com.backend.model.*;
import com.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotAvailabilityService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;
    private final AppointmentRepository appointmentRepository;

    private static final ZoneId ZONE_KATHMANDU = ZoneId.of("Asia/Kathmandu");
    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter TIME_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Finds strictly verified, available bookable slots over the next 14 days or for a specific date.
     * Evaluates against the user's historical appointment patterns and ranks them.
     */
    public List<AISlotDTO> getVerifiedAvailableSlots(
            PatientAppointmentPatternDTO pattern,
            String targetClinicId,
            String targetProviderId,
            String targetService) {
        return getVerifiedAvailableSlots(pattern, targetClinicId, targetProviderId, targetService, null, null);
    }

    public List<AISlotDTO> getVerifiedAvailableSlots(
            PatientAppointmentPatternDTO pattern,
            String targetClinicId,
            String targetProviderId,
            String targetService,
            LocalDate filterDate) {
        return getVerifiedAvailableSlots(pattern, targetClinicId, targetProviderId, targetService, null, filterDate);
    }

    public List<AISlotDTO> getVerifiedAvailableSlots(
            PatientAppointmentPatternDTO pattern,
            String targetClinicId,
            String targetProviderId,
            String targetService,
            String targetOrgType,
            LocalDate filterDate) {

        List<AISlotDTO> allAvailableSlots = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE_KATHMANDU);
        LocalTime nowTime = LocalTime.now(ZONE_KATHMANDU);

        if (filterDate != null && filterDate.isBefore(today)) {
            return Collections.emptyList();
        }

        // Strict isolation: If neither targetClinicId nor targetOrgType is specified,
        // do not return random slots to avoid confusing the user across organizations.
        if ((targetClinicId == null || targetClinicId.trim().isEmpty())
                && (targetOrgType == null || targetOrgType.trim().isEmpty())) {
            return Collections.emptyList();
        }

        // 1. Determine which clinics to inspect (Strictly AI-enabled organizations)
        List<Tenant> clinics = tenantRepository.findAll().stream()
                .filter(t -> !"SUSPENDED".equalsIgnoreCase(t.getStatus()) && !"INACTIVE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> {
                    String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter";
                    boolean isExpiredOrSuspended = "EXPIRED".equalsIgnoreCase(t.getSubscriptionStatus())
                            || "SUSPENDED".equalsIgnoreCase(t.getSubscriptionStatus())
                            || (t.getSubscriptionExpiryDate() != null && LocalDate.now().isAfter(t.getSubscriptionExpiryDate()));
                    return !isExpiredOrSuspended && ("Professional".equalsIgnoreCase(tier) || "Enterprise".equalsIgnoreCase(tier));
                })
                .collect(Collectors.toList());

        if (targetClinicId != null && !targetClinicId.trim().isEmpty()) {
            try {
                Long cId = Long.parseLong(targetClinicId.trim());
                clinics = clinics.stream().filter(t -> t.getId().equals(cId)).collect(Collectors.toList());
            } catch (Exception ignored) {}
        } else if (targetOrgType != null && !targetOrgType.trim().isEmpty()) {
            String normType = targetOrgType.trim().toLowerCase();
            clinics = clinics.stream().filter(t -> {
                String tType = (t.getOrganizationType() != null ? t.getOrganizationType() : "").toLowerCase();
                if (normType.contains("college") || normType.contains("acad") || normType.contains("educ") || normType.contains("school")) {
                    return tType.contains("college") || tType.contains("acad") || tType.contains("educ") || tType.contains("school");
                }
                if (normType.contains("salon") || normType.contains("saloon") || normType.contains("spa") || normType.contains("beauty")) {
                    return tType.contains("salon") || tType.contains("saloon") || tType.contains("spa") || tType.contains("beauty");
                }
                if (normType.contains("gym") || normType.contains("fitness")) {
                    return tType.contains("gym") || tType.contains("fitness");
                }
                if (normType.contains("clinic") || normType.contains("hosp") || normType.contains("health") || normType.contains("medic")) {
                    return tType.contains("clinic") || tType.contains("hosp") || tType.contains("health") || tType.contains("medic");
                }
                return tType.equalsIgnoreCase(normType);
            }).collect(Collectors.toList());
        }

        // Count existing booked appointments for the upcoming 14-day window to prevent double-booking
        List<Appointment> activeAppointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> a.getAppointmentStatus() == null || (!a.getAppointmentStatus().equalsIgnoreCase("CANCELLED") && !a.getAppointmentStatus().equalsIgnoreCase("REJECTED") && !a.getAppointmentStatus().equalsIgnoreCase("EXPIRED")))
                .collect(Collectors.toList());

        // Map of "providerId_date_time" to active booking counts
        Map<String, Integer> bookedSlotCounts = new HashMap<>();
        for (Appointment a : activeAppointments) {
            if (a.getProviderId() != null && a.getAppointmentDate() != null && a.getAppointmentTime() != null) {
                String key = a.getProviderId() + "_" + a.getAppointmentDate() + "_" + a.getAppointmentTime().toString().substring(0, 5);
                bookedSlotCounts.put(key, bookedSlotCounts.getOrDefault(key, 0) + 1);
            }
        }

        int slotIdCounter = 1;

        for (Tenant clinic : clinics) {
            List<User> providers = userRepository.findByTenantIdAndRole(clinic.getId(), "service_provider");
            if (targetProviderId != null && !targetProviderId.trim().isEmpty()) {
                try {
                    Long pId = Long.parseLong(targetProviderId.trim());
                    providers = providers.stream().filter(u -> u.getId().equals(pId)).collect(Collectors.toList());
                } catch (Exception ignored) {}
            }

            for (User provider : providers) {
                ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
                List<ProviderService> services = profile != null ? providerServiceRepository.findByProviderProfile(profile) : Collections.emptyList();

                String serviceName = "General Consultation";
                double fee = 1500.0;
                int durationMinutes = 30;
                int maxCapacity = 1;

                if (!services.isEmpty()) {
                    ProviderService matchingService = services.stream()
                            .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                            .filter(s -> targetService == null || targetService.trim().isEmpty() || s.getServiceName().equalsIgnoreCase(targetService.trim()))
                            .findFirst()
                            .orElse(services.get(0));

                    serviceName = matchingService.getServiceName();
                    if (matchingService.getFee() != null) fee = matchingService.getFee();
                    if (matchingService.getDurationMinutes() != null) durationMinutes = matchingService.getDurationMinutes();
                    if (matchingService.getMaxCapacity() != null && matchingService.getMaxCapacity() > 0) maxCapacity = matchingService.getMaxCapacity();
                }

                // Check days to inspect: if filterDate is provided, check only that date; otherwise next 14 days starting from today (0)
                int startOffset = 0;
                int endOffset = 14;
                if (filterDate != null) {
                    long diff = java.time.temporal.ChronoUnit.DAYS.between(today, filterDate);
                    startOffset = (int) diff;
                    endOffset = (int) diff;
                }

                for (int dayOffset = startOffset; dayOffset <= endOffset; dayOffset++) {
                    LocalDate checkDate = today.plusDays(dayOffset);
                    String dayOfWeek = checkDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

                    // Check clinic-level schedule constraint: if facility is closed on this day, no slots are available
                    Optional<TenantSchedule> tenantScheduleOpt = tenantScheduleRepository.findByTenantIdAndDayOfWeek(clinic.getId(), dayOfWeek);
                    if (tenantScheduleOpt.isPresent() && Boolean.FALSE.equals(tenantScheduleOpt.get().getIsActive())) {
                        continue;
                    }

                    Optional<ProviderSchedule> scheduleOpt = providerScheduleRepository.findByProviderAndDayOfWeek(provider, dayOfWeek);
                    if (scheduleOpt.isEmpty()) continue;

                    ProviderSchedule schedule = scheduleOpt.get();
                    if (!Boolean.TRUE.equals(schedule.getIsActive())) continue;

                    LocalTime open = schedule.getOpeningTime();
                    LocalTime close = schedule.getClosingTime();
                    if (open == null || close == null) continue;

                    LocalTime breakStart = schedule.getBreakStartTime();
                    LocalTime breakEnd = schedule.getBreakEndTime();

                    LocalTime slotTime = open;
                    while (slotTime.plusMinutes(durationMinutes).isBefore(close) || slotTime.plusMinutes(durationMinutes).equals(close)) {
                        LocalTime slotEnd = slotTime.plusMinutes(durationMinutes);

                        // If slot is for today, strictly filter out past times
                        if (checkDate.isEqual(today) && !slotTime.isAfter(nowTime)) {
                            slotTime = slotEnd;
                            continue;
                        }

                        // Check break time
                        boolean isInBreak = false;
                        if (breakStart != null && breakEnd != null) {
                            if (slotTime.isBefore(breakEnd) && slotEnd.isAfter(breakStart)) {
                                isInBreak = true;
                            }
                        }

                        // Check capacity conflict
                        String conflictKey = provider.getId() + "_" + checkDate + "_" + slotTime.toString().substring(0, 5);
                        int currentBookings = bookedSlotCounts.getOrDefault(conflictKey, 0);
                        boolean isAlreadyBooked = currentBookings >= maxCapacity;
                        int availableSeats = Math.max(0, maxCapacity - currentBookings);

                        if (!isInBreak && !isAlreadyBooked) {
                            String priceStr = "\u0930\u0942 " + String.format("%,d", (long) fee);
                            String providerDisplay = com.backend.util.OrganizationTerminology.formatProviderDisplay(
                                    provider.getFullName(),
                                    profile != null ? profile.getPrimarySpecialty() : null,
                                    clinic.getOrganizationType()
                            );

                            AISlotDTO slotDTO = AISlotDTO.builder()
                                    .id(String.valueOf(slotIdCounter))
                                    .title(serviceName)
                                    .date(checkDate.format(DATE_DISPLAY_FORMAT))
                                    .time(slotTime.format(TIME_DISPLAY_FORMAT))
                                    .price(priceStr)
                                    .provider(providerDisplay + " at " + clinic.getOrganizationName())
                                    .providerTitle(providerDisplay)
                                    .organizationType(clinic.getOrganizationType())
                                    .organizationName(clinic.getOrganizationName())
                                    .rawDate(checkDate.toString())
                                    .rawTime(slotTime.toString().substring(0, 5))
                                    .tenantId(clinic.getId())
                                    .providerId(provider.getId())
                                    .serviceName(serviceName)
                                    .topMatch(false)
                                    .maxCapacity(maxCapacity)
                                    .availableSeats(availableSeats)
                                    .isFull(isAlreadyBooked)
                                    .build();

                            allAvailableSlots.add(slotDTO);
                            slotIdCounter++;
                        }

                        slotTime = slotEnd;
                    }
                }
            }
        }

        // Rank and score the available slots against patient's appointment patterns
        return rankSlotsByPattern(allAvailableSlots, pattern, filterDate);
    }

    public List<LocalDate> getAvailableDatesForService(Long tenantId, Long providerId, String serviceName) {
        LocalDate today = LocalDate.now(ZONE_KATHMANDU);
        LocalTime nowTime = LocalTime.now(ZONE_KATHMANDU);

        if (tenantId == null) return Collections.emptyList();
        Tenant clinic = tenantRepository.findById(tenantId).orElse(null);
        if (clinic == null) return Collections.emptyList();

        List<User> providers = userRepository.findByTenantIdAndRole(clinic.getId(), "service_provider");
        if (providerId != null) {
            providers = providers.stream().filter(u -> u.getId().equals(providerId)).collect(Collectors.toList());
        }
        if (providers.isEmpty()) return Collections.emptyList();

        List<LocalDate> availableDates = new ArrayList<>();

        for (int dayOffset = 0; dayOffset <= 14; dayOffset++) {
            LocalDate checkDate = today.plusDays(dayOffset);
            String dayOfWeek = checkDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            Optional<TenantSchedule> tenantScheduleOpt = tenantScheduleRepository.findByTenantIdAndDayOfWeek(clinic.getId(), dayOfWeek);
            if (tenantScheduleOpt.isPresent() && Boolean.FALSE.equals(tenantScheduleOpt.get().getIsActive())) {
                continue;
            }

            boolean anyProviderAvailable = false;
            for (User provider : providers) {
                Optional<ProviderSchedule> scheduleOpt = providerScheduleRepository.findByProviderAndDayOfWeek(provider, dayOfWeek);
                if (scheduleOpt.isPresent() && Boolean.TRUE.equals(scheduleOpt.get().getIsActive())) {
                    LocalTime open = scheduleOpt.get().getOpeningTime();
                    LocalTime close = scheduleOpt.get().getClosingTime();
                    if (open != null && close != null) {
                        if (!checkDate.isEqual(today) || close.isAfter(nowTime)) {
                            anyProviderAvailable = true;
                            break;
                        }
                    }
                }
            }

            if (anyProviderAvailable) {
                availableDates.add(checkDate);
            }
        }
        return availableDates;
    }

    private List<AISlotDTO> rankSlotsByPattern(List<AISlotDTO> slots, PatientAppointmentPatternDTO pattern, LocalDate filterDate) {
        if (slots.isEmpty()) {
            return slots;
        }

        List<DayOfWeek> preferredDays = pattern != null && pattern.getPreferredDaysOfWeek() != null
                ? pattern.getPreferredDaysOfWeek() : Collections.emptyList();
        String preferredTimeOfDay = pattern != null ? pattern.getPreferredTimeOfDay() : "MORNING";
        Long preferredProviderId = pattern != null ? pattern.getPreferredProviderId() : null;

        for (AISlotDTO slot : slots) {
            StringBuilder matchReason = new StringBuilder();

            try {
                LocalDate date = LocalDate.parse(slot.getRawDate());
                LocalTime time = LocalTime.parse(slot.getRawTime());

                if (!preferredDays.isEmpty() && preferredDays.contains(date.getDayOfWeek())) {
                    matchReason.append("Matches preferred day (").append(date.getDayOfWeek().name()).append("). ");
                }

                int hour = time.getHour();
                boolean matchesTimeOfDay = ("MORNING".equalsIgnoreCase(preferredTimeOfDay) && hour < 12) ||
                        ("AFTERNOON".equalsIgnoreCase(preferredTimeOfDay) && hour >= 12 && hour < 16) ||
                        ("EVENING".equalsIgnoreCase(preferredTimeOfDay) && hour >= 16);

                if (matchesTimeOfDay) {
                    matchReason.append("Fits ").append(preferredTimeOfDay.toLowerCase()).append(" hours. ");
                }

                if (preferredProviderId != null && preferredProviderId.equals(slot.getProviderId())) {
                    com.backend.util.OrganizationTerminology terms = com.backend.util.OrganizationTerminology.from(slot.getOrganizationType());
                    matchReason.append("With preferred ").append(terms.getProviderTerm().toLowerCase()).append(". ");
                }
            } catch (Exception ignored) {}

            slot.setMatchReason(matchReason.toString().trim());
        }

        // Strictly sort chronologically by date and time so earlier dates appear first (No artificial day-of-week bias)
        slots.sort(Comparator.comparing(AISlotDTO::getRawDate).thenComparing(AISlotDTO::getRawTime));

        // Re-assign IDs 1 to N so prompt referencing [BOOK_SLOT_ID:1] or "Slot X" matches chronological order
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setId(String.valueOf(i + 1));
        }

        // Return up to 30 slots if filtering by date, or 24 slots across dates
        int limit = filterDate != null ? 30 : 24;
        return slots.stream().limit(limit).collect(Collectors.toList());
    }

    private int calculateSlotScore(AISlotDTO slot, List<DayOfWeek> preferredDays, String preferredTimeOfDay, Long preferredProviderId) {
        int score = 0;
        try {
            LocalDate date = LocalDate.parse(slot.getRawDate());
            LocalTime time = LocalTime.parse(slot.getRawTime());

            if (!preferredDays.isEmpty() && preferredDays.contains(date.getDayOfWeek())) {
                score += 40;
                if (preferredDays.get(0) == date.getDayOfWeek()) score += 15;
            }

            int hour = time.getHour();
            boolean matchesTimeOfDay = ("MORNING".equalsIgnoreCase(preferredTimeOfDay) && hour < 12) ||
                    ("AFTERNOON".equalsIgnoreCase(preferredTimeOfDay) && hour >= 12 && hour < 16) ||
                    ("EVENING".equalsIgnoreCase(preferredTimeOfDay) && hour >= 16);

            if (matchesTimeOfDay) score += 35;

            if (preferredProviderId != null && preferredProviderId.equals(slot.getProviderId())) {
                score += 30;
            }
        } catch (Exception ignored) {}
        return score;
    }
}
