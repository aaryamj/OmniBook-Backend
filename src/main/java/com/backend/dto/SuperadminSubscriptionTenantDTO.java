package com.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminSubscriptionTenantDTO {
    private Long tenantId;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String adminEmail;
    private String adminName;
    private String planTier;
    private String billingCycle;
    private Double lastPaidAmount;
    private Double currentMrr;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private Long daysRemaining;
    private String subscriptionStatus; // ACTIVE, EXPIRING_SOON, EXPIRED, SUSPENDED, RENEWAL_PENDING, CANCELLED
    private String tenantStatus; // ACTIVE, SUSPENDED, PENDING_SETUP
    private Integer emergencyExtensionDays;
    private String lastSuspendedReason;
    private Boolean hasPendingExtension;
    private Long latestOrderId;
    private String latestOrderNumber;

    // Aliases for seamless frontend compatibility
    @JsonProperty("id")
    public Long getId() {
        return tenantId;
    }

    @JsonProperty("name")
    public String getName() {
        return organizationName;
    }

    @JsonProperty("subscriptionTier")
    public String getSubscriptionTier() {
        return planTier;
    }

    @JsonProperty("mrrContribution")
    public Double getMrrContribution() {
        return currentMrr;
    }

    @JsonProperty("subscriptionStartDate")
    public LocalDate getSubscriptionStartDate() {
        return startDate;
    }

    @JsonProperty("subscriptionExpiryDate")
    public LocalDate getSubscriptionExpiryDate() {
        return expiryDate;
    }

    @JsonProperty("remainingDays")
    public Long getRemainingDays() {
        return daysRemaining;
    }
}

