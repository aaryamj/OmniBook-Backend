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
public class ProviderAnalyticsDTO {

    private Double totalRevenue;
    private String formattedRevenue;
    private Double revenueChangePct;

    private Long totalAppointments;
    private Double appointmentsChangePct;

    private Double noShowRate;
    private Double noShowRateChangePct;

    private List<TrendDataPoint> trends;
    private List<ServiceVolumeDTO> topServices;
    private List<AnalyticsTransactionDTO> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private String name;
        private Double mrr;
        private Long appointments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceVolumeDTO {
        private String name;
        private Long volume;
        private Double revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsTransactionDTO {
        private String id;
        private String patient;
        private String service;
        private Double amount;
        private String amountFormatted;
        private String status; // "Confirmed", "Pending", "Cancelled"
        private String paymentMethod;
        private String appointmentStatus;
        private String date;
        private String rawDate;
    }
}
