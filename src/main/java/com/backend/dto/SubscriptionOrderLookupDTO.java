package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionOrderLookupDTO {
    private Long id;
    private String orderNumber;
    private String invoiceNumber;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String address;
    private String adminFullName;
    private String adminEmail;
    private String adminPhone;
    private String planTier;
    private String billingCycle;
    private Double amount;
    private String currency;
    private String paymentStatus;
    private String verificationStatus;
    private Long tenantId;
    private LocalDateTime createdAt;
}
