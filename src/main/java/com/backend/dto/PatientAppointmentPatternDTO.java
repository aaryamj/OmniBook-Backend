package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientAppointmentPatternDTO {
    private boolean hasHistory;
    private int totalAppointments;
    
    // Day of week preferences (e.g., TUESDAY, THURSDAY)
    private List<DayOfWeek> preferredDaysOfWeek;
    private Map<DayOfWeek, Long> dayFrequencyMap;
    
    // Time slot preferences
    private String preferredTimeOfDay; // "MORNING", "AFTERNOON", "EVENING"
    private LocalTime averageAppointmentTime;
    
    // Visit frequency
    private Double averageDaysBetweenVisits; // e.g. 30.5 days
    private String projectedNextVisitWindow;
    
    // Preferred providers and clinics
    private Long preferredProviderId;
    private String preferredProviderName;
    private Long preferredTenantId;
    private String preferredServiceName;
    
    // Human-readable summary for AI prompt and badges
    private String patternSummary;
}
