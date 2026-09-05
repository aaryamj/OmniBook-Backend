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
    private final AppointmentRepository appointmentRepository;

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter TIME_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Finds strictly verified, available bookable slots over the next 14 days.
     * Evaluates against the user's historical appointment patterns and ranks them.
     */
    public List<AISlotDTO> getVerifiedAvailableSlots(
            PatientAppointmentPatternDTO pattern,
            String targetClinicId,
            String targetProviderId,
            String targetService) {

        List<AISlotDTO> allAvailableSlots = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 1. Determine which clinics to inspect
        List<Tenant> clinics = tenantRepository.findAll().stream()
                .filter(t -> !"SUSPENDED".equalsIgnoreCase(t.getStatus()) && !"INACTIVE".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        if (targetClinicId != null && !targetClinicId.trim().isEmpty()) {
            try {
                Long cId = Long.parseLong(targetClinicId.trim());
                clinics = clinics.stream().filter(t -> t.getId().equals(cId)).collect(Collectors.toList());
            } catch (Exception ignored) {}
        }

        // Cache existing booked appointments for the upcoming 14-day window to prevent double-booking
        List<Appointment> activeAppointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()))
                .collect(Collectors.toList());

        // Set of "providerId_date_time" to quickly check for booking conflicts
        Set<String> bookedSlots = new HashSet<>();
        for (Appointment a : activeAppointments) {
            if (a.getProviderId() != null && a.getAppointmentDate() != null && a.getAppointmentTime() != null) {
                bookedSlots.add(a.getProviderId() + "_" + a.getAppointmentDate() + "_" + a.getAppointmentTime().toString().substring(0, 5));
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

                if (!services.isEmpty()) {
                    ProviderService matchingService = services.stream()
                            .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                            .filter(s -> targetService == null || targetService.trim().isEmpty() || s.getServiceName().equalsIgnoreCase(targetService.trim()))
                            .findFirst()
                            .orElse(services.get(0));

                    serviceName = matchingService.getServiceName();
                    if (matchingService.getFee() != null) fee = matchingService.getFee();
                    if (matchingService.getDurationMinutes() != null) durationMinutes = matchingService.getDurationMinutes();
                }

                // Check upcoming 14 days
                for (int dayOffset = 1; dayOffset <= 14; dayOffset++) {
                    LocalDate checkDate = today.plusDays(dayOffset);
                    String dayOfWeek = checkDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

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

                        // Check break time
                        boolean isInBreak = false;
                        if (breakStart != null && breakEnd != null) {
                            if (slotTime.isBefore(breakEnd) && slotEnd.isAfter(breakStart)) {
                                isInBreak = true;
                            }
                        }

                        // Check conflict with booked appointments
                        String conflictKey = provider.getId() + "_" + checkDate + "_" + slotTime.toString().substring(0, 5);
                        boolean isAlreadyBooked = bookedSlots.contains(conflictKey);

                        if (!isInBreak && !isAlreadyBooked) {
                            String priceStr = "रू " + String.format("%,d", (long) fee);
                            String rawName = provider.getFullName() != null ? provider.getFullName().trim() : "Specialist";
                            String providerTitle = (rawName.toLowerCase().startsWith("dr.") || rawName.toLowerCase().startsWith("dr "))
                                    ? rawName
                                    : "Dr. " + rawName;
                            if (profile != null && profile.getPrimarySpecialty() != null) {
                                providerTitle += " (" + profile.getPrimarySpecialty() + ")";
                            }

                            AISlotDTO slotDTO = AISlotDTO.builder()
                                    .id(String.valueOf(slotIdCounter))
                                    .title(serviceName)
                                    .date(checkDate.format(DATE_DISPLAY_FORMAT))
                                    .time(slotTime.format(TIME_DISPLAY_FORMAT))
                                    .price(priceStr)
                                    .provider(providerTitle + " at " + clinic.getOrganizationName())
                                    .rawDate(checkDate.toString())
                                    .rawTime(slotTime.toString().substring(0, 5))
                                    .tenantId(clinic.getId())
                                    .providerId(provider.getId())
                                    .serviceName(serviceName)
                                    .topMatch(false)
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
        return rankSlotsByPattern(allAvailableSlots, pattern);
    }

    private List<AISlotDTO> rankSlotsByPattern(List<AISlotDTO> slots, PatientAppointmentPatternDTO pattern) {
        if (slots.isEmpty()) {
            return slots;
        }

        List<DayOfWeek> preferredDays = pattern != null && pattern.getPreferredDaysOfWeek() != null
                ? pattern.getPreferredDaysOfWeek() : Collections.emptyList();
        String preferredTimeOfDay = pattern != null ? pattern.getPreferredTimeOfDay() : "MORNING";
        Long preferredProviderId = pattern != null ? pattern.getPreferredProviderId() : null;

        for (AISlotDTO slot : slots) {
            int score = 0;
            StringBuilder matchReason = new StringBuilder();

            try {
                LocalDate date = LocalDate.parse(slot.getRawDate());
                LocalTime time = LocalTime.parse(slot.getRawTime());

                // Day of week match
                if (!preferredDays.isEmpty() && preferredDays.contains(date.getDayOfWeek())) {
                    score += 40;
                    if (preferredDays.get(0) == date.getDayOfWeek()) {
                        score += 15;
                        matchReason.append("Matches your most frequent visit day (").append(date.getDayOfWeek().name()).append("). ");
                    }
                }

                // Time of day match
                int hour = time.getHour();
                boolean matchesTimeOfDay = ("MORNING".equalsIgnoreCase(preferredTimeOfDay) && hour < 12) ||
                        ("AFTERNOON".equalsIgnoreCase(preferredTimeOfDay) && hour >= 12 && hour < 16) ||
                        ("EVENING".equalsIgnoreCase(preferredTimeOfDay) && hour >= 16);

                if (matchesTimeOfDay) {
                    score += 35;
                    matchReason.append("Fits your typical ").append(preferredTimeOfDay.toLowerCase()).append(" preference. ");
                }

                // Provider match
                if (preferredProviderId != null && preferredProviderId.equals(slot.getProviderId())) {
                    score += 30;
                    matchReason.append("With your preferred doctor. ");
                }

                // Early date bonus (prefer sooner within next 7 days)
                long daysAway = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
                if (daysAway <= 3) {
                    score += 10;
                }

            } catch (Exception ignored) {}

            slot.setMatchReason(matchReason.toString().trim());
        }

        // Sort by score descending (we'll re-index IDs so 1, 2, 3... represent top recommended slots)
        slots.sort((a, b) -> {
            int scoreA = calculateSlotScore(a, preferredDays, preferredTimeOfDay, preferredProviderId);
            int scoreB = calculateSlotScore(b, preferredDays, preferredTimeOfDay, preferredProviderId);
            return Integer.compare(scoreB, scoreA);
        });

        // Mark the top 1 as topMatch
        if (!slots.isEmpty()) {
            slots.get(0).setTopMatch(true);
            if (slots.get(0).getMatchReason() == null || slots.get(0).getMatchReason().isEmpty()) {
                slots.get(0).setMatchReason("Top recommended slot based on clinic availability.");
            }
        }

        // Re-assign IDs 1 to N so prompt referencing [BOOK_SLOT_ID:1] matches exactly
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setId(String.valueOf(i + 1));
        }

        // Return top 8 best candidate slots
        return slots.stream().limit(8).collect(Collectors.toList());
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
