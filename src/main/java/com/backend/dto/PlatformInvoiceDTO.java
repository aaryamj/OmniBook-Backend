package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformInvoiceDTO {
    private Long id;
    private String invoiceNumber;
    private String invoiceDate;
    private Double amount;
    private String currency;
    private String status;
    private String planName;
    private String billingPeriod;
    private String organizationName;
    private String organizationType;
    private String registrationNumber;
    private String address;
    private String adminFullName;
    private String adminEmail;
    private String adminPhone;
    private String paymentMethod;
    private String orderNumber;
    private String billingCycle;
    private String verificationStatus;
    private String transactionId;
    private String refundId;
    private String refundReason;
    private String refundedAt;
}
