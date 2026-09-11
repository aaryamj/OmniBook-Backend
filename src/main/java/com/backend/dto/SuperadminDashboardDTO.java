package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminDashboardDTO {
    private Double mrr; // Subscription MRR (normalized)
    private Double subscriptionMrr; // Explicit Subscription MRR
    private Double subscriptionRevenue; // Period subscription cash inflow
    private Double appointmentCommissionRevenue; // Period platform commission earnings
    private Double totalPlatformRevenue; // Combined platform revenue (subscription + commission)
    private Double commissionRate; // Current platform commission rate % (e.g. 10.0)
    private Long activeClinics;
    private Long activeSubscribers;
    private Long expiringSoonCount;
    private Long expiredCount;
    private Long pendingExtensionsCount;
    private Long totalPatientFootfall;
    private Double systemUptime;
    private Long newClinicsThisWeek;
    private Map<String, List<Double>> revenueChartData;
}
