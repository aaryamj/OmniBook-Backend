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
    private Double mrr;
    private Long activeClinics;
    private Long totalPatientFootfall;
    private Double systemUptime;
    private Long newClinicsThisWeek;
    private Map<String, List<Double>> revenueChartData;
}
