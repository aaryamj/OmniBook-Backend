package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSubscriptionOverviewDTO {
    private Long tenantId;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String planTier;
    private String currentPlan;
    private String plan;
    private String planDisplayName;
    private String displayName;
    private String billingCycle;
    private Double lastPaidAmount;
    private Double amountPaid;
    private Double monthlyPrice;
    private Double annualPrice;
    private String currency;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionExpiryDate;
    private Long daysRemaining;
    private Long remainingDays;
    private String status; // ACTIVE, EXPIRING_SOON, EXPIRED, SUSPENDED, RENEWAL_PENDING, CANCELLED
    private String subscriptionStatus;
    private Boolean isExpired;
    private Boolean isSuspended;
    private Boolean isExpiringSoon;
    private boolean isGated;
    private String suspendedReason;
    private String lastSuspendedReason;
    private Integer emergencyExtensionDays;
    private Boolean hasPendingExtensionRequest;
    private boolean hasPendingExtension;
    private boolean canRenew;
    private boolean canRequestExtension;
    private SubscriptionExtensionRequestDTO pendingExtensionRequest;
    private List<String> planFeatures;
    private List<String> features;
    private Integer userLimit;
    private Long currentUsers;
    private Integer appointmentLimit;
    private Long currentAppointmentsThisMonth;
    private List<PlatformInvoiceDTO> invoices;
    private List<PlatformInvoiceDTO> recentInvoices;

    public boolean isGated() {
        return isGated || "EXPIRED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(subscriptionStatus) || "SUSPENDED".equalsIgnoreCase(subscriptionStatus);
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus != null ? subscriptionStatus : status;
    }
}
