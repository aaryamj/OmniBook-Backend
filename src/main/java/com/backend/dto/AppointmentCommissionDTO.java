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
public class AppointmentCommissionDTO {
    private Long id;
    private Long appointmentId;
    private Long tenantId;
    private String organizationName;
    private String transactionId;
    private Double grossAmount;
    private String currency;
    private Double baseAmountNpr;
    private Double exchangeRate;
    private Double commissionRate;
    private Double commissionAmount;
    private Double providerPayout;
    private String paymentStatus;
    private String settlementStatus;
    private Double refundDeductionAmount;
    private Double netRetainedAmount;
    private Double gatewayFeeAmount;
    private Double remainingOrgAmount;
    private Double orgAdminPayout;
    private Double platformCommissionRate;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private LocalDateTime createdAt;
}
