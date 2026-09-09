package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettingDTO {
    // General Preferences
    private String platformName;
    private String baseTimezone;
    private String systemCurrency;
    private Boolean isWhiteGloveEnabled;
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
    private String esewaEnvironment;
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

    // Computed KPIs
    private Integer activeIntegrationsCount;
    private String onboardingModeBadge;
    private String currencyTimezoneBadge;
    private String systemVersion;

    // Invoices list
    private List<PlatformInvoiceDTO> invoices;
}
