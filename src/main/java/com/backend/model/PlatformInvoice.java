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
@Table(name = "platform_invoices")
public class PlatformInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private String invoiceDate;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status; // "Paid", "Pending", "Failed"

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

    private Long tenantId;
    private java.time.LocalDate subscriptionStartDate;
    private java.time.LocalDate subscriptionExpiryDate;
    private LocalDateTime transactionDate;

    private String billingCycle;
    private String verificationStatus; // "PENDING_REVIEW", "APPROVED", "REJECTED"
    private String transactionId;
    private String refundId;
    @Column(columnDefinition = "TEXT")
    private String refundReason;
    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
