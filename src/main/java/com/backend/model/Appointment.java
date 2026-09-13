package com.backend.model;

import jakarta.persistence.*;
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
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String patientPhone;

    private String patientEmail;

    @Column(columnDefinition = "TEXT")
    private String reasonForVisit;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime appointmentTime;

    private String appointmentType; // IN_PERSON, VIRTUAL
    
    private String meetingLink; // URL for telehealth

    private Boolean videoCallEnabled; // Whether provider enabled virtual video consultation

    private String serviceName;

    private Double price; // Canonical service price in NPR

    @Builder.Default
    private String baseCurrency = "NPR"; // Base single source-of-truth currency

    private Double basePriceNpr; // Original NPR amount

    private String chargedCurrency; // "NPR" or "USD"

    private Double chargedAmount; // Converted or charged amount

    private Double exchangeRate; // Applicable exchange rate (NPR per USD)

    private LocalDateTime conversionTimestamp; // Timestamp when conversion occurred

    @Column(nullable = false)
    private String paymentStatus; // PENDING, SUCCESS, FAILED

    private String paymentMethod; // ESEWA, STRIPE, CASH

    private String transactionId; // Reference ID from payment gateway
    
    @Column(nullable = false)
    private String appointmentStatus = "PENDING_APPROVAL"; // PENDING_APPROVAL, SCHEDULED, CHECKED_IN, COMPLETED, CANCELLED, REJECTED, NO_SHOW

    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String treatmentSummary;

    @Column(columnDefinition = "TEXT")
    private String internalNotes;

    private LocalDate followUpDate;

    private String feedbackToken;

    private Integer patientRating;

    @Column(columnDefinition = "TEXT")
    private String patientReview;

    private LocalDateTime bookedAt;
    private String bookedByName;
    private String bookedByRole;
    private Long bookedByUserId;

    private LocalDateTime approvedAt;
    private String approvedByName;
    private String approvedByRole;
    private Long approvedByUserId;

    private LocalDateTime checkedInAt;
    private String checkedInByName;
    private String checkedInByRole;
    private Long checkedInByUserId;

    private LocalDateTime completedAt;
    private String completedByName;
    private String completedByRole;
    private Long completedByUserId;

    private String cancelledByName;
    private String cancelledByRole;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    // Provider Rejection tracking & audit
    private String rejectedByName;
    private String rejectedByRole;
    private Long rejectedByUserId;
    private LocalDateTime rejectedAt;
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // Rescheduling tracking
    @Builder.Default
    private Integer rescheduleCount = 0;
    private LocalDate originalAppointmentDate;
    private LocalTime originalAppointmentTime;
    private LocalDateTime rescheduledAt;
    private String rescheduledByName;
    private String rescheduledByRole;

    // Refund lifecycle & audit
    private String refundStatus; // NOT_ELIGIBLE, ELIGIBLE, REFUND_REQUESTED, PROCESSING, REFUNDED, PARTIALLY_REFUNDED, FAILED
    private Double refundEligibilityPercentage; // e.g. 100.0, 50.0, 0.0
    private Double refundAmount; // In refund currency (USD for Stripe, NPR for eSewa)
    private String refundCurrency; // "USD" or "NPR"
    private String refundTransactionId; // Reference ID from payment gateway for refund
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundedAt;
    private String refundFailureReason;
    private String gatewayPaymentRef; // Stripe PaymentIntent ID or session ID

    // No-Show tracking & Settlement
    private LocalDateTime noShowAt;
    private String noShowMarkedByName;
    private String noShowMarkedByRole;
    private String settlementStatus; // PENDING, SETTLED, ADJUSTED_REFUND, FORFEITED
    private Double settlementAmount; // Amount retained / settled to provider (Net Service Provider)
    private Double orgSettlementAmount; // Amount retained / settled to Organization Admin (Net Organization Admin)
    private Double remainingOrgAmount; // Gross Remaining Organization Amount
    private Double netRetainedAmount; // Gross Booking Amount - Refund Amount
    private Double gatewayFeeAmount; // Applicable gateway fee
    private Long dailySettlementId; // ID of the daily settlement batch this appointment belongs to
    private LocalDate settlementBatchDate; // Calendar date of the settlement batch

    @Transient
    private String patientProfilePicture;

    @Transient
    private String doctorProfilePicture;

    @Transient
    private String doctorName;

    @Transient
    private String doctorSpecialty;
    
    @Transient
    private String organizationType;

    @Transient
    private String organizationName;

    @Transient
    private Boolean serviceAllowsVideo;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (bookedAt == null) {
            bookedAt = LocalDateTime.now();
        }
        if (appointmentStatus == null) {
            appointmentStatus = "PENDING_APPROVAL";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
