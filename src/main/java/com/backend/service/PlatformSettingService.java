package com.backend.service;

import com.backend.dto.PlatformInvoiceDTO;
import com.backend.dto.PlatformSettingDTO;
import com.backend.model.PlatformInvoice;
import com.backend.model.PlatformSetting;
import com.backend.repository.PlatformInvoiceRepository;
import com.backend.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final PlatformSettingRepository platformSettingRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final com.backend.repository.SubscriptionOrderRepository subscriptionOrderRepository;

    @Transactional
    public PlatformSetting getOrCreateSettings() {
        return platformSettingRepository.findAll().stream().findFirst().orElseGet(() -> {
            PlatformSetting initial = PlatformSetting.builder()
                    .platformName("OmniBook Enterprise")
                    .baseTimezone("Asia/Kathmandu")
                    .systemCurrency("NPR")
                    .isWhiteGloveEnabled(true)
                    .auditLogRetention("7 Years")
                    // Stripe
                    .stripeActive(true)
                    .stripePublishableKey("pk_test_51OmniBookLiveStripeKeyExample")
                    .stripeSecretKey("sk_test_••••••••••••••••••••••••")
                    .stripeWebhookSecret("whsec_••••••••••••••••••••••••")
                    .stripeStatus("Active • v2.1.0 • Webhook responding")
                    // eSewa
                    .esewaActive(true)
                    .esewaMerchantCode("EPAYTEST")
                    .esewaSecretKey("8gBm/:&EnhH.1/q")
                    .esewaEnvironment("TEST")
                    .esewaStatus("Active • v2.0.1 • Regional Default")
                    // Twilio
                    .twilioActive(true)
                    .twilioAccountSid("AC••••••••••••••••••••••••••••••••")
                    .twilioAuthToken("••••••••••••••••••••••••••••••••")
                    .twilioSenderNumber("+15005550006")
                    .twilioStatus("Active • 98.4% Delivery Rate")
                    // Security
                    .sessionTimeoutMinutes(15)
                    .enforceGlobalMfa(true)
                    .rateLimitRequestsPerMin(1000)
                    .minPasswordLength(8)
                    // Subscription & Billing
                    .subscriptionTier("Enterprise License")
                    .subscriptionStatus("ACTIVE")
                    .subscriptionAnnualFee(4999.00)
                    .billingCycle("Annually")
                    .nextInvoiceDate("Oct 1, 2026")
                    .billingContactEmail("billing@omnibook.com")
                    .appointmentCommissionRate(10.0)
                    .build();

            return platformSettingRepository.save(initial);
        });
    }

    @PostConstruct
    @Transactional
    public void purgeMockInvoices() {
        List<String> mockNumbers = List.of("INV-2025-001", "INV-2024-001", "INV-2023-001", "INV-2026-7736");
        List<PlatformInvoice> mocks = platformInvoiceRepository.findAll().stream()
                .filter(inv -> mockNumbers.contains(inv.getInvoiceNumber()) 
                        || inv.getOrganizationName() == null 
                        || "Test Apex Clinic".equalsIgnoreCase(inv.getOrganizationName()))
                .toList();
        if (!mocks.isEmpty()) {
            platformInvoiceRepository.deleteAll(mocks);
        }
    }

    @Transactional
    public void deleteInvoice(Long id) {
        platformInvoiceRepository.deleteById(id);
    }

    @Transactional
    public PlatformSettingDTO getPlatformSettings() {
        PlatformSetting setting = getOrCreateSettings();
        List<PlatformInvoice> invoices = platformInvoiceRepository.findAllByOrderByCreatedAtDesc();

        return mapToDTO(setting, invoices);
    }

    @Transactional
    public PlatformSettingDTO updatePlatformSettings(PlatformSettingDTO dto) {
        PlatformSetting setting = getOrCreateSettings();

        // General Preferences
        if (dto.getPlatformName() != null) setting.setPlatformName(dto.getPlatformName());
        if (dto.getBaseTimezone() != null) setting.setBaseTimezone(dto.getBaseTimezone());
        if (dto.getSystemCurrency() != null) setting.setSystemCurrency(dto.getSystemCurrency());
        if (dto.getIsWhiteGloveEnabled() != null) setting.setIsWhiteGloveEnabled(dto.getIsWhiteGloveEnabled());
        if (dto.getAuditLogRetention() != null) setting.setAuditLogRetention(dto.getAuditLogRetention());

        // Stripe
        if (dto.getStripeActive() != null) setting.setStripeActive(dto.getStripeActive());
        if (dto.getStripePublishableKey() != null) setting.setStripePublishableKey(dto.getStripePublishableKey());
        if (dto.getStripeSecretKey() != null && !dto.getStripeSecretKey().contains("•••")) setting.setStripeSecretKey(dto.getStripeSecretKey());
        if (dto.getStripeWebhookSecret() != null && !dto.getStripeWebhookSecret().contains("•••")) setting.setStripeWebhookSecret(dto.getStripeWebhookSecret());
        if (dto.getStripeStatus() != null) setting.setStripeStatus(dto.getStripeStatus());

        // eSewa
        if (dto.getEsewaActive() != null) setting.setEsewaActive(dto.getEsewaActive());
        if (dto.getEsewaMerchantCode() != null) setting.setEsewaMerchantCode(dto.getEsewaMerchantCode());
        if (dto.getEsewaSecretKey() != null && !dto.getEsewaSecretKey().contains("•••")) setting.setEsewaSecretKey(dto.getEsewaSecretKey());
        if (dto.getEsewaEnvironment() != null) setting.setEsewaEnvironment(dto.getEsewaEnvironment());
        if (dto.getEsewaStatus() != null) setting.setEsewaStatus(dto.getEsewaStatus());

        // Twilio
        if (dto.getTwilioActive() != null) setting.setTwilioActive(dto.getTwilioActive());
        if (dto.getTwilioAccountSid() != null && !dto.getTwilioAccountSid().contains("•••")) setting.setTwilioAccountSid(dto.getTwilioAccountSid());
        if (dto.getTwilioAuthToken() != null && !dto.getTwilioAuthToken().contains("•••")) setting.setTwilioAuthToken(dto.getTwilioAuthToken());
        if (dto.getTwilioSenderNumber() != null) setting.setTwilioSenderNumber(dto.getTwilioSenderNumber());
        if (dto.getTwilioStatus() != null) setting.setTwilioStatus(dto.getTwilioStatus());

        // Security Policies
        if (dto.getSessionTimeoutMinutes() != null) setting.setSessionTimeoutMinutes(dto.getSessionTimeoutMinutes());
        if (dto.getEnforceGlobalMfa() != null) setting.setEnforceGlobalMfa(dto.getEnforceGlobalMfa());
        if (dto.getRateLimitRequestsPerMin() != null) setting.setRateLimitRequestsPerMin(dto.getRateLimitRequestsPerMin());
        if (dto.getMinPasswordLength() != null) setting.setMinPasswordLength(dto.getMinPasswordLength());

        // Subscription & Billing
        if (dto.getSubscriptionTier() != null) setting.setSubscriptionTier(dto.getSubscriptionTier());
        if (dto.getSubscriptionStatus() != null) setting.setSubscriptionStatus(dto.getSubscriptionStatus());
        if (dto.getSubscriptionAnnualFee() != null) setting.setSubscriptionAnnualFee(dto.getSubscriptionAnnualFee());
        if (dto.getBillingCycle() != null) setting.setBillingCycle(dto.getBillingCycle());
        if (dto.getNextInvoiceDate() != null) setting.setNextInvoiceDate(dto.getNextInvoiceDate());
        if (dto.getBillingContactEmail() != null) setting.setBillingContactEmail(dto.getBillingContactEmail());
        if (dto.getAppointmentCommissionRate() != null) setting.setAppointmentCommissionRate(dto.getAppointmentCommissionRate());

        platformSettingRepository.save(setting);

        List<PlatformInvoice> invoices = platformInvoiceRepository.findAllByOrderByCreatedAtDesc();
        return mapToDTO(setting, invoices);
    }

    private PlatformSettingDTO mapToDTO(PlatformSetting setting, List<PlatformInvoice> invoices) {
        int activeIntegrations = 0;
        if (Boolean.TRUE.equals(setting.getStripeActive())) activeIntegrations++;
        if (Boolean.TRUE.equals(setting.getEsewaActive())) activeIntegrations++;
        if (Boolean.TRUE.equals(setting.getTwilioActive())) activeIntegrations++;

        String onboardingBadge = Boolean.TRUE.equals(setting.getIsWhiteGloveEnabled()) 
                ? "Strict (White-Glove)" 
                : "Open (Self-Serve)";

        String currencyTimezoneBadge = setting.getSystemCurrency() + " / " + setting.getBaseTimezone().split("/")[setting.getBaseTimezone().split("/").length - 1];

        List<PlatformInvoiceDTO> invoiceDTOs = invoices.stream().map(inv -> {
            var orderOpt = subscriptionOrderRepository.findByInvoiceNumber(inv.getInvoiceNumber());
            String orderNum = inv.getOrderNumber() != null ? inv.getOrderNumber() : orderOpt.map(o -> o.getOrderNumber()).orElse(null);
            String orgType = inv.getOrganizationType() != null ? inv.getOrganizationType() : orderOpt.map(o -> o.getOrganizationType()).orElse("Healthcare Organization");
            String regNum = inv.getRegistrationNumber() != null ? inv.getRegistrationNumber() : orderOpt.map(o -> o.getRegistrationNumber()).orElse("N/A");
            String addr = inv.getAddress() != null ? inv.getAddress() : orderOpt.map(o -> o.getAddress()).orElse("N/A");
            String adminName = inv.getAdminFullName() != null ? inv.getAdminFullName() : orderOpt.map(o -> o.getAdminFullName()).orElse("Administrator");
            String adminPhone = inv.getAdminPhone() != null ? inv.getAdminPhone() : orderOpt.map(o -> o.getAdminPhone()).orElse("N/A");
            String cycle = inv.getBillingCycle() != null ? inv.getBillingCycle() : orderOpt.map(o -> o.getBillingCycle()).orElse("Monthly");
            String verStatus = inv.getVerificationStatus() != null ? inv.getVerificationStatus() : orderOpt.map(o -> o.getVerificationStatus()).orElse("PENDING_REVIEW");
            String txId = inv.getTransactionId() != null ? inv.getTransactionId() : orderOpt.map(o -> o.getTransactionId()).orElse(orderNum);
            String refId = inv.getRefundId() != null ? inv.getRefundId() : orderOpt.map(o -> o.getRefundId()).orElse(null);
            String refReason = inv.getRefundReason() != null ? inv.getRefundReason() : orderOpt.map(o -> o.getRefundReason()).orElse(null);
            String refAt = inv.getRefundedAt() != null ? inv.getRefundedAt().toString() : orderOpt.map(o -> o.getRefundedAt() != null ? o.getRefundedAt().toString() : null).orElse(null);

            String adminEmail = inv.getAdminEmail() != null ? inv.getAdminEmail() : orderOpt.map(o -> o.getAdminEmail()).orElse(null);

            return PlatformInvoiceDTO.builder()
                    .id(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .orderNumber(orderNum)
                    .invoiceDate(inv.getInvoiceDate())
                    .amount(inv.getAmount())
                    .currency(inv.getCurrency())
                    .status(inv.getStatus())
                    .planName(inv.getPlanName())
                    .billingPeriod(inv.getBillingPeriod())
                    .billingCycle(cycle)
                    .organizationName(inv.getOrganizationName())
                    .organizationType(orgType)
                    .registrationNumber(regNum)
                    .address(addr)
                    .adminFullName(adminName)
                    .adminEmail(adminEmail)
                    .adminPhone(adminPhone)
                    .paymentMethod(inv.getPaymentMethod())
                    .verificationStatus(verStatus)
                    .transactionId(txId)
                    .refundId(refId)
                    .refundReason(refReason)
                    .refundedAt(refAt)
                    .build();
        }).collect(Collectors.toList());

        return PlatformSettingDTO.builder()
                .platformName(setting.getPlatformName())
                .baseTimezone(setting.getBaseTimezone())
                .systemCurrency(setting.getSystemCurrency())
                .isWhiteGloveEnabled(setting.getIsWhiteGloveEnabled())
                .auditLogRetention(setting.getAuditLogRetention())
                // Stripe
                .stripeActive(setting.getStripeActive())
                .stripePublishableKey(setting.getStripePublishableKey())
                .stripeSecretKey(maskKey(setting.getStripeSecretKey()))
                .stripeWebhookSecret(maskKey(setting.getStripeWebhookSecret()))
                .stripeStatus(Boolean.TRUE.equals(setting.getStripeActive()) ? setting.getStripeStatus() : "Inactive • Disabled by Admin")
                // eSewa
                .esewaActive(setting.getEsewaActive())
                .esewaMerchantCode(setting.getEsewaMerchantCode())
                .esewaSecretKey(maskKey(setting.getEsewaSecretKey()))
                .esewaEnvironment(setting.getEsewaEnvironment() != null ? setting.getEsewaEnvironment() : "TEST")
                .esewaStatus(Boolean.TRUE.equals(setting.getEsewaActive()) ? setting.getEsewaStatus() : "Inactive • Disabled by Admin")
                // Twilio
                .twilioActive(setting.getTwilioActive())
                .twilioAccountSid(maskKey(setting.getTwilioAccountSid()))
                .twilioAuthToken(maskKey(setting.getTwilioAuthToken()))
                .twilioSenderNumber(setting.getTwilioSenderNumber())
                .twilioStatus(Boolean.TRUE.equals(setting.getTwilioActive()) ? setting.getTwilioStatus() : "Inactive • Disabled by Admin")
                // Security
                .sessionTimeoutMinutes(setting.getSessionTimeoutMinutes() != null ? setting.getSessionTimeoutMinutes() : 15)
                .enforceGlobalMfa(setting.getEnforceGlobalMfa() != null ? setting.getEnforceGlobalMfa() : true)
                .rateLimitRequestsPerMin(setting.getRateLimitRequestsPerMin() != null ? setting.getRateLimitRequestsPerMin() : 1000)
                .minPasswordLength(setting.getMinPasswordLength() != null ? setting.getMinPasswordLength() : 8)
                // Billing
                .subscriptionTier(setting.getSubscriptionTier())
                .subscriptionStatus(setting.getSubscriptionStatus())
                .subscriptionAnnualFee(setting.getSubscriptionAnnualFee())
                .billingCycle(setting.getBillingCycle())
                .nextInvoiceDate(setting.getNextInvoiceDate())
                .billingContactEmail(setting.getBillingContactEmail())
                .appointmentCommissionRate(setting.getAppointmentCommissionRate() != null ? setting.getAppointmentCommissionRate() : 10.0)
                // Computed KPIs
                .activeIntegrationsCount(activeIntegrations)
                .onboardingModeBadge(onboardingBadge)
                .currencyTimezoneBadge(currencyTimezoneBadge)
                .systemVersion("v2.4.0")
                .invoices(invoiceDTOs)
                .build();
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "••••••••";
        return key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4);
    }
}
