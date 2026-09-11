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
@Table(name = "platform_settings")
public class PlatformSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // General Preferences
    @Column(nullable = false)
    private String platformName;

    @Column(nullable = false)
    private String baseTimezone;

    @Column(nullable = false)
    private String systemCurrency;

    @Column(nullable = false)
    private Boolean isWhiteGloveEnabled;

    @Column(nullable = false)
    private String auditLogRetention;

    // Payment Gateway - Stripe
    private Boolean stripeActive;
    private String stripePublishableKey;
    private String stripeSecretKey;
    private String stripeWebhookSecret;
    private String stripeStatus;

    // Payment Gateway - eSewa
    private Boolean esewaActive;
    private String esewaMerchantCode;
    private String esewaSecretKey;
    private String esewaEnvironment; // "TEST" or "LIVE"
    private String esewaStatus;

    // Messaging Provider - Twilio
    private Boolean twilioActive;
    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioSenderNumber;
    private String twilioStatus;

    // Security Policies
    private Integer sessionTimeoutMinutes;
    private Boolean enforceGlobalMfa;
    private Integer rateLimitRequestsPerMin;
    private Integer minPasswordLength;

    // Subscription & Billing
    private String subscriptionTier;
    private String subscriptionStatus;
    private Double subscriptionAnnualFee;
    private String billingCycle;
    private String nextInvoiceDate;
    private String billingContactEmail;

    @Column(columnDefinition = "double default 10.0")
    @Builder.Default
    private Double appointmentCommissionRate = 10.0;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
