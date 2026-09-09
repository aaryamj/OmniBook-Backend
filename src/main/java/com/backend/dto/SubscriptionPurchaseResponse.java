package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPurchaseResponse {
    private boolean success;
    private String message;
    private String orderNumber;
    private String invoiceNumber;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String address;
    private String adminFullName;
    private String adminEmail;
    private String adminPhone;
    private String transactionId;
    private String verificationStatus;
    private String planTier;
    private Double amount;
    private String currency;
    private String billingPeriod;
    private String paymentMethod;
    private String status;
}
