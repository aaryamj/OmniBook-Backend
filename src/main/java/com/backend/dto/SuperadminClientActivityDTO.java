package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminClientActivityDTO {
    private Long appointmentId;
    
    // Organization / Facility
    private Long tenantId;
    private String organizationName;
    private String organizationType;
    
    // Provider
    private Long providerId;
    private String providerName;
    private String providerSpecialty;
    private String providerProfilePicture;
    
    // Service & Schedule
    private String serviceName;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String appointmentType; // IN_PERSON, VIRTUAL
    private String appointmentStatus; // SCHEDULED, IN_CONSULTATION, COMPLETED, CANCELLED, REJECTED, etc.
    
    // Payment Details
    private Double price;
    private String paymentMethod; // CASH, ESEWA, STRIPE, CARD
    private String paymentStatus; // SUCCESS, PAID, PENDING, FAILED
    private String billingStatus; // [eSewa Verified], [Stripe Verified], [Cash Verified], Pending
    private String transactionId;
    
    // Lifecycle Timestamps
    private LocalDateTime bookedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime rejectedAt;
    private String cancellationReason;
    private String rejectionReason;
    private String treatmentSummary;
}
