package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionExtensionRequestDTO {
    private Long id;
    private Long tenantId;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String currentPlanTier;
    private String phone;
    private Integer requestedDays;
    private String reason;
    private String requestedByEmail;
    private String requestedByName;
    private String status;
    private LocalDate previousExpiryDate;
    private LocalDate newExpiryDate;
    private Integer approvedDays;
    private String processedByEmail;
    private String processedByName;
    private LocalDateTime processedAt;
    private String adminNotes;
    private LocalDateTime createdAt;

    // Aliases & convenience getters for frontend JSON serialization
    public String getTenantName() {
        return organizationName;
    }

    public String getCurrentPlan() {
        return currentPlanTier != null ? currentPlanTier : "Starter";
    }

    public String getCurrentExpiryDate() {
        return previousExpiryDate != null ? previousExpiryDate.toString() : null;
    }

    public String getRequestedBy() {
        if (requestedByName != null && !requestedByName.trim().isEmpty()) {
            return requestedByName;
        }
        return requestedByEmail != null ? requestedByEmail : "N/A";
    }

    public String getReviewedBy() {
        if (processedByName != null && !processedByName.trim().isEmpty()) {
            return processedByName;
        }
        return processedByEmail;
    }
}
