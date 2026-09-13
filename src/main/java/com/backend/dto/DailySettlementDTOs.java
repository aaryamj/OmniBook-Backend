package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class DailySettlementDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long id;
        private Long tenantId;
        private String tenantName;
        private String organizationType;
        private LocalDate settlementDate;
        private Integer totalAppointments;
        private Double grossRevenue;
        private Double totalRefunds;
        private Double netRetained;
        private Double platformCommissionRate;
        private Double platformCommission;
        private Double gatewayFees;
        private Double remainingOrgAmount;
        private Double providerPayoutsTotal;
        private Double orgAdminPayout;
        private String settlementStatus; // "SETTLED", "IN_ESCROW"
        private Boolean isLocked;
        private String settledAt;
        private String settledBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderShare {
        private Long providerId;
        private String providerName;
        private String specialtyOrRole;
        private Integer appointmentsCount;
        private Double attributedGross;
        private Double commissionRate;
        private Double netProviderPayout;
        private Double netOrgShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentItem {
        private Long appointmentId;
        private String time;
        private String customerName;
        private String customerPhone;
        private String serviceName;
        private String providerName;
        private String paymentMethod;
        private Double gross;
        private Double refund;
        private Double netRetained;
        private Double gatewayFee;
        private Double platformFee;
        private Double remainingOrg;
        private Double providerPayout;
        private Double orgAdminPayout;
        private String status;
        private String settlementStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private Summary summary;
        private List<ProviderShare> providerShares;
        private List<AppointmentItem> appointments;
        private Boolean isEligibleForFinalization;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderDailyView {
        private LocalDate date;
        private Long tenantId;
        private String organizationName;
        private String organizationType;
        private Integer appointmentsCount;
        private Double attributedGross;
        private Double netRetained;
        private Double commissionRate;
        private Double netProviderPayout;
        private String status; // "SETTLED", "IN_ESCROW"
        private Boolean isLocked;
        private String settledAt;
        private List<AppointmentItem> appointments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuperAdminSettlementOverview {
        private Double totalSettledRevenue;
        private Double totalPlatformEarnings;
        private Double totalGatewayFees;
        private Long totalSettledBatches;
        private Long pendingEscrowBatches;
        private List<Summary> settlements;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminSettlementOverview {
        private Double totalOrgNetProfit;
        private Double totalProviderPayouts;
        private Double totalPlatformFeesPaid;
        private Double todayInEscrowRevenue;
        private Long completedSettlementsCount;
        private List<Summary> settlements;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSettlementOverview {
        private Double totalNetEarningsAllTime;
        private Double thisMonthNetSettlement;
        private Double todayProjectedEarnings;
        private Double activeCommissionRate;
        private List<ProviderDailyView> settlements;
    }
}
