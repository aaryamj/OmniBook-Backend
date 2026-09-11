package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "appointment_commissions")
public class AppointmentCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long appointmentId;

    @Column(nullable = false)
    private Long tenantId;

    private String transactionId;

    @Column(nullable = false)
    private Double grossAmount; // Full appointment fee paid (in charged currency)

    private String currency; // "NPR" or "USD"

    private Double baseAmountNpr; // Original NPR amount

    private Double exchangeRate; // Exchange rate applied (NPR per USD)

    @Column(nullable = false)
    private Double commissionRate; // Rate applicable at transaction time (e.g. 10.0%)

    @Column(nullable = false)
    private Double commissionAmount; // Platform revenue (e.g. 100.0)

    @Column(nullable = false)
    private Double providerPayout; // Provider share (e.g. 900.0)

    @Column(nullable = false)
    private String paymentStatus; // "SUCCESS", "PAID", "SETTLED", "REFUNDED", "NO_SHOW_SETTLED"

    private String settlementStatus; // "SETTLED", "PENDING", "ADJUSTED_REFUND", "FORFEITED"

    private Double refundDeductionAmount; // Amount refunded / deducted from gross

    private Double netRetainedAmount; // Gross Booking Amount - Refund Amount

    private Double gatewayFeeAmount; // Applicable gateway processing fee (e.g. Stripe / eSewa)

    private Double remainingOrgAmount; // Gross Remaining Organization Amount (Net Retained - Platform Fee - Gateway Fee)

    private Double orgAdminPayout; // Net Organization Admin share (Remaining Org Amount - Net Service Provider)

    private Double platformCommissionRate; // Platform fee percentage rate applied (e.g. 10.0%)

    private String paymentMethod; // "ESEWA", "STRIPE", "CASH"

    private LocalDateTime paymentDate;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }
}
