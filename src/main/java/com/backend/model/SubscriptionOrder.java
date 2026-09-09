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
@Table(name = "subscription_orders")
public class SubscriptionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String organizationType;

    @Column(nullable = false)
    private String registrationNumber;

    private String address;

    @Column(nullable = false)
    private String adminFullName;

    @Column(nullable = false)
    private String adminEmail;

    private String adminPhone;

    @Column(nullable = false)
    private String planTier;

    @Column(nullable = false)
    private String billingCycle;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(nullable = false)
    private String paymentStatus; // e.g. "PAID"

    @Column(nullable = false)
    private String verificationStatus; // "PENDING_REVIEW", "APPROVED", "REJECTED", "VERIFICATION_IN_PROGRESS"

    private String invoiceNumber;

    private String transactionId; // Gateway paymentIntent / session ID / transaction UUID
    private String refundId; // Gateway refund ID
    @Column(columnDefinition = "TEXT")
    private String refundReason;
    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
