package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivePatientFlowDTO {
    private Long id;
    private String time;
    private String patientName;
    private String patientId;
    private String service;
    private String providerName;
    private String status; // Waiting Room, In-Consultation, etc.
    private String billingStatus; // [eSewa Verified], [Stripe Verified], [Cash Verified], Pending
    private String paymentMethod;
    private String paymentStatus;
    private Double price;
}
