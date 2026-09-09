package com.backend.service;

import com.backend.dto.PatientAppointmentPatternDTO;
import com.backend.model.Appointment;
import com.backend.model.User;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentHistoryAnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    /**
     * Analyzes historical appointment behavior for a patient given their email.
     * Computes preferred days, times of day, frequency, and preferred providers.
     */
    public PatientAppointmentPatternDTO analyzePatientHistory(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            return buildDefaultPattern("Guest user (no historical appointment records).");
        }

        List<Appointment> history = appointmentRepository.findByPatientEmailOrderByAppointmentDateDesc(userEmail.trim().toLowerCase());
        if (history == null || history.isEmpty()) {
            return buildDefaultPattern("New patient with no previous appointment history.");
        }

        int totalCount = history.size();

        // 1. Day of week frequency analysis
        Map<DayOfWeek, Long> dayFrequency = history.stream()
                .filter(a -> a.getAppointmentDate() != null)
                .collect(Collectors.groupingBy(a -> a.getAppointmentDate().getDayOfWeek(), Collectors.counting()));

        List<DayOfWeek> preferredDays = dayFrequency.entrySet().stream()
                .sorted(Map.Entry.<DayOfWeek, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 2. Time-of-day preference analysis (Morning < 12:00, Afternoon 12:00 - 16:00, Evening >= 16:00)
        long morningCount = 0;
        long afternoonCount = 0;
        long eveningCount = 0;
        long totalSeconds = 0;
        int validTimes = 0;

        for (Appointment a : history) {
            LocalTime time = a.getAppointmentTime();
            if (time != null) {
                int hour = time.getHour();
                if (hour < 12) {
                    morningCount++;
                } else if (hour < 16) {
                    afternoonCount++;
                } else {
                    eveningCount++;
                }
                totalSeconds += time.toSecondOfDay();
                validTimes++;
            }
        }

        String preferredTimeOfDay = "MORNING";
        if (afternoonCount > morningCount && afternoonCount >= eveningCount) {
            preferredTimeOfDay = "AFTERNOON";
        } else if (eveningCount > morningCount && eveningCount > afternoonCount) {
            preferredTimeOfDay = "EVENING";
        }

        LocalTime avgTime = validTimes > 0 ? LocalTime.ofSecondOfDay(totalSeconds / validTimes) : LocalTime.of(10, 0);

        // 3. Visit frequency analysis (average gap in days between visits)
        Double avgGapDays = null;
        if (history.size() >= 2) {
            long totalGaps = 0;
            int gapCount = 0;
            for (int i = 0; i < history.size() - 1; i++) {
                LocalDate curr = history.get(i).getAppointmentDate();
                LocalDate prev = history.get(i + 1).getAppointmentDate();
                if (curr != null && prev != null) {
                    long daysBetween = Math.abs(ChronoUnit.DAYS.between(prev, curr));
                    totalGaps += daysBetween;
                    gapCount++;
                }
            }
            if (gapCount > 0) {
                avgGapDays = (double) totalGaps / gapCount;
            }
        }

        // 4. Preferred Provider and Clinic
        Map<Long, Long> providerCount = history.stream()
                .filter(a -> a.getProviderId() != null)
                .collect(Collectors.groupingBy(Appointment::getProviderId, Collectors.counting()));

        Long preferredProviderId = providerCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        String preferredProviderName = null;
        if (preferredProviderId != null) {
            Optional<User> pUser = userRepository.findById(preferredProviderId);
            if (pUser.isPresent()) {
                preferredProviderName = pUser.get().getFullName();
            }
        }

        Map<Long, Long> tenantCount = history.stream()
                .filter(a -> a.getTenantId() != null)
                .collect(Collectors.groupingBy(Appointment::getTenantId, Collectors.counting()));

        Long preferredTenantId = tenantCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // 5. Preferred service
        Map<String, Long> serviceCount = history.stream()
                .filter(a -> a.getServiceName() != null && !a.getServiceName().isEmpty())
                .collect(Collectors.groupingBy(Appointment::getServiceName, Collectors.counting()));

        String preferredService = serviceCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("General Consultation");

        // 6. Build Human-Readable Pattern Summary
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("User has completed ").append(totalCount).append(" previous appointment(s). ");
        if (!preferredDays.isEmpty()) {
            summaryBuilder.append("Frequently books on ").append(preferredDays.get(0).name());
            if (preferredDays.size() > 1) {
                summaryBuilder.append(" and ").append(preferredDays.get(1).name());
            }
            summaryBuilder.append(". ");
        }
        summaryBuilder.append("Prefers ").append(preferredTimeOfDay.toLowerCase()).append(" slots (around ")
                .append(avgTime.toString()).append("). ");
        if (avgGapDays != null && avgGapDays > 0) {
            summaryBuilder.append("Typical booking frequency is every ").append(Math.round(avgGapDays)).append(" days. ");
        }
        if (preferredProviderName != null) {
            summaryBuilder.append("Frequently schedules with ").append(preferredProviderName).append(". ");
        }

        return PatientAppointmentPatternDTO.builder()
                .hasHistory(true)
                .totalAppointments(totalCount)
                .preferredDaysOfWeek(preferredDays)
                .dayFrequencyMap(dayFrequency)
                .preferredTimeOfDay(preferredTimeOfDay)
                .averageAppointmentTime(avgTime)
                .averageDaysBetweenVisits(avgGapDays)
                .preferredProviderId(preferredProviderId)
                .preferredProviderName(preferredProviderName)
                .preferredTenantId(preferredTenantId)
                .preferredServiceName(preferredService)
                .patternSummary(summaryBuilder.toString().trim())
                .build();
    }

    private PatientAppointmentPatternDTO buildDefaultPattern(String reason) {
        return PatientAppointmentPatternDTO.builder()
                .hasHistory(false)
                .totalAppointments(0)
                .preferredDaysOfWeek(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
                .preferredTimeOfDay("MORNING")
                .averageAppointmentTime(LocalTime.of(10, 0))
                .patternSummary(reason)
                .build();
    }
}
