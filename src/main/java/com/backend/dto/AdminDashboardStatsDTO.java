package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsDTO {
    // KPIs
    private int todayVolume;
    private int totalCapacity;
    private int activeInClinic;
    private int waitingPatients;
    private int inConsultPatients;
    private double esewaSettled;
    private double stripeConnect;

    // Dual Ledger
    private double esewaWeeklyVolume;
    private double stripeWeeklyVolume;

    // Arrays
    private List<LivePatientFlowDTO> livePatientFlow;
    private List<Integer> weeklyAppointments; // length 7 (Mon-Sun)
    private List<ProviderStatusDTO> providerMatrix;

    // Branding & Organization
    private String primaryAccentColor;
    private String organizationName;
    private String organizationType;
}
