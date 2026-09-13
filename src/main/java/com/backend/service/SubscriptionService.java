package com.backend.service;

import com.backend.dto.*;
import com.backend.model.*;
import com.backend.repository.*;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionExtensionRequestRepository subscriptionExtensionRequestRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final RevenueAnalyticsService revenueAnalyticsService;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final InvitationRepository invitationRepository;
    private final AuditLogService auditLogService;

    public static boolean isAnnualCycle(String billingCycle) {
        if (billingCycle == null) return false;
        String normalized = billingCycle.trim().toLowerCase();
        return normalized.contains("annual") || normalized.contains("year");
    }

    @Transactional
    public Map<String, Object> initiateSubscription(SubscriptionPurchaseRequest request) {
        // Check if this is an upgrade/renewal for an existing organization
        Tenant existingTenant = null;
        if (request.getRegistrationNumber() != null && !request.getRegistrationNumber().isBlank()) {
            existingTenant = tenantRepository.findByRegistrationNumber(request.getRegistrationNumber().trim()).orElse(null);
        }
        if (existingTenant == null && request.getAdminEmail() != null && !request.getAdminEmail().isBlank()) {
            User existingAdmin = userRepository.findByEmail(request.getAdminEmail().trim()).orElse(null);
            if (existingAdmin != null && existingAdmin.getTenant() != null) {
                existingTenant = existingAdmin.getTenant();
            }
        }
        if (existingTenant == null && request.getOrganizationName() != null && !request.getOrganizationName().isBlank()) {
            existingTenant = tenantRepository.findByOrganizationName(request.getOrganizationName().trim()).orElse(null);
        }

        Random random = new Random();
        int suffix = 1000 + random.nextInt(9000);
        String orderNumber = "ORD-2026-" + suffix;
        String invoiceNumber = "INV-2026-" + suffix;
        String currency = request.getCurrency() != null ? request.getCurrency() : "NPR";
        String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod().trim() : "eSewa";

        // Retrieve dynamic price from catalog
        SubscriptionPlan plan = subscriptionPlanService.getPlanEntityByName(request.getPlanTier());
        boolean isAnnual = isAnnualCycle(request.getBillingCycle());
        double dynamicPrice;
        if (plan != null) {
            dynamicPrice = isAnnual ? plan.getAnnualPrice() : plan.getMonthlyPrice();
        } else {
            dynamicPrice = isAnnual 
                    ? subscriptionPlanService.getFallbackAnnualPriceForTier(request.getPlanTier())
                    : subscriptionPlanService.getFallbackMonthlyPriceForTier(request.getPlanTier());
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today;
        LocalDate expiryDate;

        if (existingTenant != null && existingTenant.getSubscriptionExpiryDate() != null && !today.isAfter(existingTenant.getSubscriptionExpiryDate())) {
            startDate = existingTenant.getSubscriptionStartDate() != null ? existingTenant.getSubscriptionStartDate() : today;
            expiryDate = isAnnual ? existingTenant.getSubscriptionExpiryDate().plusYears(1) : existingTenant.getSubscriptionExpiryDate().plusMonths(1);
        } else {
            expiryDate = isAnnual ? startDate.plusYears(1) : startDate.plusMonths(1);
        }

        // Save initial SubscriptionOrder with PENDING_REVIEW status
        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber(orderNumber)
                .tenantId(existingTenant != null ? existingTenant.getId() : null)
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType())
                .registrationNumber(request.getRegistrationNumber())
                .address(request.getAddress())
                .adminFullName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .planTier(plan != null ? plan.getName() : request.getPlanTier())
                .planId(plan != null ? plan.getId() : null)
                .billingCycle(request.getBillingCycle())
                .amount(dynamicPrice)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .paymentStatus("Bank Transfer".equalsIgnoreCase(paymentMethod) ? "PAID" : "PENDING_PAYMENT")
                .verificationStatus("PENDING_REVIEW")
                .invoiceNumber(invoiceNumber)
                .transactionId(orderNumber)
                .subscriptionStartDate(startDate)
                .subscriptionExpiryDate(expiryDate)
                .build();

        subscriptionOrderRepository.save(order);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderNumber", orderNumber);
        response.put("invoiceNumber", invoiceNumber);
        response.put("paymentMethod", paymentMethod);
        response.put("amount", dynamicPrice);

        if ("eSewa".equalsIgnoreCase(paymentMethod)) {
            // Build ePay v2 payload for eSewa
            String amountStr = String.valueOf(dynamicPrice);
            if (amountStr.endsWith(".0")) {
                amountStr = amountStr.substring(0, amountStr.length() - 2);
            }
            String productCode = "EPAYTEST";

            Map<String, Object> esewaParams = new HashMap<>();
            esewaParams.put("amount", amountStr);
            esewaParams.put("tax_amount", "0");
            esewaParams.put("total_amount", amountStr);
            esewaParams.put("transaction_uuid", orderNumber);
            esewaParams.put("product_code", productCode);
            esewaParams.put("product_service_charge", "0");
            esewaParams.put("product_delivery_charge", "0");
            esewaParams.put("success_url", "http://localhost:8080/api/v1/subscriptions/verify-esewa");
            esewaParams.put("failure_url", "http://localhost:5173/pricing?payment=failed");
            esewaParams.put("signed_field_names", "total_amount,transaction_uuid,product_code");

            try {
                String secretKey = "8gBm/:&EnhH.1/q";
                String message = "total_amount=" + amountStr + ",transaction_uuid=" + orderNumber + ",product_code=" + productCode;
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hash = mac.doFinal(message.getBytes("UTF-8"));
                String signature = Base64.getEncoder().encodeToString(hash);
                esewaParams.put("signature", signature);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate eSewa payment signature", e);
            }

            response.put("gatewayUrl", "https://rc-epay.esewa.com.np/api/epay/main/v2/form");
            response.put("formData", esewaParams);
            return response;

        } else if ("Card".equalsIgnoreCase(paymentMethod) || "Stripe".equalsIgnoreCase(paymentMethod)) {
            // Build Stripe Checkout Session
            try {
                Stripe.apiKey = stripeApiKey;

                String successUrl = "http://localhost:8080/api/v1/subscriptions/verify-stripe?session_id={CHECKOUT_SESSION_ID}&order_number=" + orderNumber;
                String cancelUrl = "http://localhost:5173/pricing?payment=cancelled";

                SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setClientReferenceId(orderNumber)
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd") // Test mode in USD cents
                                        .setUnitAmount((long) (dynamicPrice * 100))
                                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(order.getPlanTier() + " Plan (" + order.getBillingCycle() + ") - OmniBook")
                                                .setDescription("Organization: " + request.getOrganizationName())
                                                .build())
                                        .build())
                                .build())
                        .build();

                Session session = Session.create(params);
                order.setTransactionId(session.getId());
                subscriptionOrderRepository.save(order);
                response.put("gatewayUrl", session.getUrl());
                return response;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initiate Stripe payment checkout: " + e.getMessage(), e);
            }
        } else {
            // Direct offline / Bank Transfer settlement
            createOrUpdatePlatformInvoice(order);
            try {
                notificationService.notifySuperAdmin(
                        "New Subscription Order (" + order.getPlanTier() + ")",
                        request.getOrganizationName() + " placed order via Bank Transfer. Pending bank verification & invitation.",
                        "SUBSCRIPTION_ORDER",
                        "/superadmin/settings"
                );
            } catch (Exception ignored) {}

            response.put("directSuccess", true);
            response.put("message", "Subscription order placed successfully! Awaiting bank transfer verification.");
            return response;
        }
    }

    @Transactional
    public String verifyEsewaSubscription(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        try {
            String cleanData = data.trim().replace(" ", "+");
            byte[] decodedBytes;
            try {
                decodedBytes = Base64.getDecoder().decode(cleanData);
            } catch (Exception ex) {
                decodedBytes = Base64.getUrlDecoder().decode(cleanData);
            }
            String decodedData = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

            String status = "";
            java.util.regex.Matcher statusMatcher = java.util.regex.Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"").matcher(decodedData);
            if (statusMatcher.find()) {
                status = statusMatcher.group(1);
            }

            String transactionUuid = "";
            java.util.regex.Matcher uuidMatcher = java.util.regex.Pattern.compile("\"transaction_uuid\"\\s*:\\s*\"([^\"]+)\"").matcher(decodedData);
            if (uuidMatcher.find()) {
                transactionUuid = uuidMatcher.group(1);
            }

            String transactionCode = "";
            java.util.regex.Matcher codeMatcher = java.util.regex.Pattern.compile("\"transaction_code\"\\s*:\\s*\"([^\"]+)\"").matcher(decodedData);
            if (codeMatcher.find()) {
                transactionCode = codeMatcher.group(1);
            }

            if (!transactionUuid.isEmpty()) {
                SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(transactionUuid).orElse(null);
                if (order != null) {
                    // IDEMPOTENCY CHECK: If order already processed and paid, return without duplicate extension/invoicing
                    if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                        log.info("SubscriptionOrder {} already processed and marked PAID. Idempotent return.", transactionUuid);
                        return transactionUuid;
                    }

                    if ("COMPLETE".equalsIgnoreCase(status)) {
                        order.setPaymentStatus("PAID");
                        order.setVerificationStatus("APPROVED"); // Payment verified
                        if (!transactionCode.isEmpty()) {
                            order.setTransactionId(transactionCode);
                        } else if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                            order.setTransactionId(transactionUuid);
                        }
                        subscriptionOrderRepository.save(order);

                        createOrUpdatePlatformInvoice(order);

                        // Sync tenant if already created or existing
                        syncTenantSubscription(order);

                        try {
                            notificationService.notifySuperAdmin(
                                    "Subscription Paid via eSewa (" + order.getPlanTier() + ")",
                                    order.getOrganizationName() + " completed payment of Rs. " + order.getAmount() + " for " + order.getPlanTier() + " Plan via eSewa. Order #" + order.getOrderNumber(),
                                    "SUBSCRIPTION_PAID",
                                    "/superadmin/settings"
                            );
                        } catch (Exception ignored) {}

                        return transactionUuid;
                    } else {
                        // Payment status from gateway is not COMPLETE (failed or cancelled)
                        order.setPaymentStatus("FAILED");
                        order.setVerificationStatus("FAILED");
                        subscriptionOrderRepository.save(order);
                        log.warn("SubscriptionOrder {} failed with eSewa status: {}", transactionUuid, status);
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to verify eSewa subscription payload: " + e.getMessage(), e);
        }
        return null;
    }

    @Transactional
    public String verifyStripeSubscription(String sessionId, String orderNumber) {
        try {
            Stripe.apiKey = stripeApiKey;
            Session session = Session.retrieve(sessionId);

            String targetOrderNumber = (orderNumber != null && !orderNumber.isEmpty()) ? orderNumber : session.getClientReferenceId();
            if (targetOrderNumber != null) {
                SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(targetOrderNumber).orElse(null);
                if (order != null) {
                    // IDEMPOTENCY CHECK: If order already processed and paid, return without duplicate extension/invoicing
                    if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                        log.info("SubscriptionOrder {} already processed and marked PAID. Idempotent return.", targetOrderNumber);
                        return targetOrderNumber;
                    }

                    if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
                        order.setPaymentStatus("PAID");
                        order.setVerificationStatus("APPROVED"); // Verified
                        if (session.getPaymentIntent() != null && !session.getPaymentIntent().isBlank()) {
                            order.setTransactionId(session.getPaymentIntent());
                        } else if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                            order.setTransactionId(sessionId);
                        }
                        subscriptionOrderRepository.save(order);

                        createOrUpdatePlatformInvoice(order);

                        // Sync tenant if already created
                        syncTenantSubscription(order);

                        try {
                            notificationService.notifySuperAdmin(
                                    "Subscription Paid via Stripe (" + order.getPlanTier() + ")",
                                    order.getOrganizationName() + " completed payment of Rs. " + order.getAmount() + " for " + order.getPlanTier() + " Plan via Stripe Card. Order #" + order.getOrderNumber(),
                                    "SUBSCRIPTION_PAID",
                                    "/superadmin/settings"
                            );
                        } catch (Exception ignored) {}

                        return targetOrderNumber;
                    } else {
                        // Payment not completed
                        order.setPaymentStatus("FAILED");
                        order.setVerificationStatus("FAILED");
                        subscriptionOrderRepository.save(order);
                        log.warn("SubscriptionOrder {} Stripe payment status was: {}", targetOrderNumber, session.getPaymentStatus());
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to verify Stripe subscription: " + e.getMessage(), e);
        }
        return null;
    }

    @Transactional
    public void markOrderCancelled(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) return;
        SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(orderNumber).orElse(null);
        if (order != null && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            order.setPaymentStatus("CANCELLED");
            order.setVerificationStatus("CANCELLED");
            subscriptionOrderRepository.save(order);
            log.info("SubscriptionOrder {} marked as CANCELLED", orderNumber);
        }
    }

    private void syncTenantSubscription(SubscriptionOrder order) {
        if (order == null) return;
        Tenant tenant = null;
        if (order.getTenantId() != null) {
            tenant = tenantRepository.findById(order.getTenantId()).orElse(null);
        } 
        if (tenant == null && order.getRegistrationNumber() != null && !order.getRegistrationNumber().isBlank()) {
            tenant = tenantRepository.findByRegistrationNumber(order.getRegistrationNumber().trim()).orElse(null);
        }
        if (tenant == null && order.getAdminEmail() != null && !order.getAdminEmail().isBlank()) {
            User adminUser = userRepository.findByEmail(order.getAdminEmail().trim()).orElse(null);
            if (adminUser != null && adminUser.getTenant() != null) {
                tenant = adminUser.getTenant();
            }
        }
        if (tenant == null && order.getOrganizationName() != null && !order.getOrganizationName().isBlank()) {
            tenant = tenantRepository.findByOrganizationName(order.getOrganizationName().trim()).orElse(null);
        }

        if (tenant != null) {
            order.setTenantId(tenant.getId());
            subscriptionOrderRepository.save(order);

            tenant.setSubscriptionTier(order.getPlanTier());
            tenant.setBillingCycle(order.getBillingCycle());
            tenant.setSubscriptionStartDate(order.getSubscriptionStartDate() != null ? order.getSubscriptionStartDate() : LocalDate.now());
            tenant.setSubscriptionExpiryDate(order.getSubscriptionExpiryDate() != null 
                    ? order.getSubscriptionExpiryDate() 
                    : (isAnnualCycle(order.getBillingCycle()) ? LocalDate.now().plusYears(1) : LocalDate.now().plusMonths(1)));
            tenant.setSubscriptionStatus("ACTIVE");
            tenant.setStatus("ACTIVE");
            tenant.setLastSuspendedReason(null);
            tenant.setLastReminderDaysSent("");
            tenantRepository.save(tenant);
            log.info("syncTenantSubscription: Upgraded Tenant '{}' (ID: {}) to tier '{}' with status ACTIVE and expiry {}",
                    tenant.getOrganizationName(), tenant.getId(), tenant.getSubscriptionTier(), tenant.getSubscriptionExpiryDate());
        } else {
            log.warn("syncTenantSubscription: No existing Tenant found for order #{}. Invitation/onboarding required.", order.getOrderNumber());
        }
    }

    public PlatformInvoice createOrUpdatePlatformInvoice(SubscriptionOrder order) {
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String invoiceDate = now.format(fmt);
        LocalDate start = order.getSubscriptionStartDate() != null ? order.getSubscriptionStartDate() : now;
        LocalDate endDate = order.getSubscriptionExpiryDate() != null 
                ? order.getSubscriptionExpiryDate() 
                : (isAnnualCycle(order.getBillingCycle()) ? start.plusYears(1) : start.plusMonths(1));
        String billingPeriod = start.format(fmt) + " - " + endDate.format(fmt);

        PlatformInvoice invoice = platformInvoiceRepository.findByOrderNumber(order.getOrderNumber()).orElse(null);

        if (invoice == null) {
            invoice = PlatformInvoice.builder()
                    .invoiceNumber(order.getInvoiceNumber())
                    .orderNumber(order.getOrderNumber())
                    .invoiceDate(invoiceDate)
                    .amount(order.getAmount())
                    .currency(order.getCurrency())
                    .status("Paid")
                    .planName(order.getPlanTier() + " Plan (" + order.getBillingCycle() + ")")
                    .billingPeriod(billingPeriod)
                    .billingCycle(order.getBillingCycle())
                    .organizationName(order.getOrganizationName())
                    .organizationType(order.getOrganizationType())
                    .registrationNumber(order.getRegistrationNumber())
                    .address(order.getAddress())
                    .adminFullName(order.getAdminFullName())
                    .adminEmail(order.getAdminEmail())
                    .adminPhone(order.getAdminPhone())
                    .paymentMethod(order.getPaymentMethod())
                    .verificationStatus(order.getVerificationStatus() != null ? order.getVerificationStatus() : "APPROVED")
                    .transactionId(order.getTransactionId())
                    .tenantId(order.getTenantId())
                    .subscriptionStartDate(start)
                    .subscriptionExpiryDate(endDate)
                    .transactionDate(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now())
                    .build();
        } else {
            invoice.setStatus("Paid");
            invoice.setInvoiceDate(invoiceDate);
            if (order.getVerificationStatus() != null) {
                invoice.setVerificationStatus(order.getVerificationStatus());
            }
            if (order.getTransactionId() != null) {
                invoice.setTransactionId(order.getTransactionId());
            }
            if (order.getTenantId() != null) {
                invoice.setTenantId(order.getTenantId());
            }
            if (order.getSubscriptionStartDate() != null) {
                invoice.setSubscriptionStartDate(order.getSubscriptionStartDate());
            }
            if (order.getSubscriptionExpiryDate() != null) {
                invoice.setSubscriptionExpiryDate(order.getSubscriptionExpiryDate());
            }
        }

        return platformInvoiceRepository.save(invoice);
    }

    public SubscriptionPurchaseResponse getOrderResponse(String orderNumber) {
        SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Subscription order not found: " + orderNumber));

        PlatformInvoice invoice = platformInvoiceRepository.findByOrderNumber(orderNumber).orElse(null);

        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        LocalDate endDate = order.getSubscriptionExpiryDate() != null 
                ? order.getSubscriptionExpiryDate() 
                : (isAnnualCycle(order.getBillingCycle()) ? now.plusYears(1) : now.plusMonths(1));
        String billingPeriod = (invoice != null && invoice.getBillingPeriod() != null)
                ? invoice.getBillingPeriod()
                : (now.format(fmt) + " - " + endDate.format(fmt));

        String orgType = order.getOrganizationType() != null ? order.getOrganizationType() : (invoice != null ? invoice.getOrganizationType() : "Clinic");
        String regNumber = order.getRegistrationNumber() != null ? order.getRegistrationNumber() : (invoice != null ? invoice.getRegistrationNumber() : "");
        String address = order.getAddress() != null ? order.getAddress() : (invoice != null ? invoice.getAddress() : "");
        String adminName = order.getAdminFullName() != null ? order.getAdminFullName() : (invoice != null ? invoice.getAdminFullName() : "");
        String adminEmail = order.getAdminEmail() != null ? order.getAdminEmail() : (invoice != null ? invoice.getAdminEmail() : "");
        String adminPhone = order.getAdminPhone() != null ? order.getAdminPhone() : (invoice != null ? invoice.getAdminPhone() : "");
        String txId = order.getTransactionId() != null ? order.getTransactionId() : (invoice != null ? invoice.getTransactionId() : order.getOrderNumber());
        String verStatus = order.getVerificationStatus() != null ? order.getVerificationStatus() : (invoice != null && invoice.getVerificationStatus() != null ? invoice.getVerificationStatus() : "APPROVED");

        return SubscriptionPurchaseResponse.builder()
                .success(true)
                .message("Subscription order verified successfully!")
                .orderNumber(order.getOrderNumber())
                .invoiceNumber(order.getInvoiceNumber())
                .organizationName(order.getOrganizationName())
                .organizationType(orgType)
                .registrationNumber(regNumber)
                .address(address)
                .adminFullName(adminName)
                .adminEmail(adminEmail)
                .adminPhone(adminPhone)
                .transactionId(txId)
                .verificationStatus(verStatus)
                .planTier(order.getPlanTier())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .billingPeriod(billingPeriod)
                .paymentMethod(order.getPaymentMethod())
                .status("PAID".equalsIgnoreCase(order.getPaymentStatus()) ? "Paid" : order.getPaymentStatus())
                .build();
    }

    @Transactional
    public SubscriptionPurchaseResponse purchaseSubscription(SubscriptionPurchaseRequest request) {
        if (tenantRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new RuntimeException("An organization with registration/PAN number " + request.getRegistrationNumber() + " already exists.");
        }

        Random random = new Random();
        int suffix = 1000 + random.nextInt(9000);
        String orderNumber = "ORD-2026-" + suffix;
        String invoiceNumber = "INV-2026-" + suffix;

        // Dynamic price resolution
        SubscriptionPlan plan = subscriptionPlanService.getPlanEntityByName(request.getPlanTier());
        boolean isAnnual = isAnnualCycle(request.getBillingCycle());
        double dynamicPrice;
        if (plan != null) {
            dynamicPrice = isAnnual ? plan.getAnnualPrice() : plan.getMonthlyPrice();
        } else {
            dynamicPrice = isAnnual 
                    ? subscriptionPlanService.getFallbackAnnualPriceForTier(request.getPlanTier())
                    : subscriptionPlanService.getFallbackMonthlyPriceForTier(request.getPlanTier());
        }

        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String invoiceDate = now.format(fmt);

        LocalDate endDate = isAnnual ? now.plusYears(1) : now.plusMonths(1);
        String billingPeriod = now.format(fmt) + " - " + endDate.format(fmt);

        String currency = request.getCurrency() != null ? request.getCurrency() : "NPR";

        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber(orderNumber)
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType())
                .registrationNumber(request.getRegistrationNumber())
                .address(request.getAddress())
                .adminFullName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .planTier(plan != null ? plan.getName() : request.getPlanTier())
                .planId(plan != null ? plan.getId() : null)
                .billingCycle(request.getBillingCycle())
                .amount(dynamicPrice)
                .currency(currency)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PAID")
                .verificationStatus("APPROVED")
                .invoiceNumber(invoiceNumber)
                .transactionId(orderNumber)
                .subscriptionStartDate(now)
                .subscriptionExpiryDate(endDate)
                .build();

        subscriptionOrderRepository.save(order);

        PlatformInvoice invoice = PlatformInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .orderNumber(orderNumber)
                .invoiceDate(invoiceDate)
                .amount(dynamicPrice)
                .currency(currency)
                .status("Paid")
                .planName((plan != null ? plan.getName() : request.getPlanTier()) + " Plan (" + request.getBillingCycle() + ")")
                .billingPeriod(billingPeriod)
                .billingCycle(request.getBillingCycle())
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType())
                .registrationNumber(request.getRegistrationNumber())
                .address(request.getAddress())
                .adminFullName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .paymentMethod(request.getPaymentMethod())
                .verificationStatus("APPROVED")
                .transactionId(orderNumber)
                .subscriptionStartDate(now)
                .subscriptionExpiryDate(endDate)
                .transactionDate(LocalDateTime.now())
                .build();

        platformInvoiceRepository.save(invoice);

        try {
            notificationService.notifySuperAdmin(
                    "New Subscription Order (" + order.getPlanTier() + ")",
                    request.getOrganizationName() + " purchased " + order.getPlanTier() + " Plan via " + request.getPaymentMethod() + ". Order #" + orderNumber,
                    "SUBSCRIPTION_ORDER",
                    "/superadmin/settings"
            );
        } catch (Exception ignored) {}

        return SubscriptionPurchaseResponse.builder()
                .success(true)
                .message("Subscription purchased successfully! Workspace access activated.")
                .orderNumber(orderNumber)
                .invoiceNumber(invoiceNumber)
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType())
                .registrationNumber(request.getRegistrationNumber())
                .address(request.getAddress())
                .adminFullName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .transactionId(orderNumber)
                .verificationStatus("APPROVED")
                .planTier(order.getPlanTier())
                .amount(dynamicPrice)
                .currency(currency)
                .billingPeriod(billingPeriod)
                .paymentMethod(request.getPaymentMethod())
                .status("Paid")
                .build();
    }

    /**
     * RENEWAL WORKFLOW:
     * - If renewing before expiry (early renewal): preserves remaining valid days (currentExpiry + cycle).
     * - If renewing after expiry (late renewal): starts new subscription cycle today (today + cycle).
     * - Creates immutable new SubscriptionOrder and PlatformInvoice permanently storing actual amount paid.
     * - Works seamlessly for both Monthly and Annual cycles.
     */
    @Transactional
    public Map<String, Object> renewSubscription(Long tenantId, String planTier, String billingCycle, String paymentMethod, String adminEmail) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));

        SubscriptionPlan plan = subscriptionPlanService.getPlanEntityByName(planTier != null ? planTier : tenant.getSubscriptionTier());
        if (plan == null) {
            plan = subscriptionPlanService.getPlanEntityByName("Starter");
        }

        String cycle = billingCycle != null ? billingCycle : (tenant.getBillingCycle() != null ? tenant.getBillingCycle() : "Monthly");
        boolean isAnnual = isAnnualCycle(cycle);
        double amount = isAnnual ? plan.getAnnualPrice() : plan.getMonthlyPrice();

        LocalDate today = LocalDate.now();
        LocalDate newStartDate;
        LocalDate newExpiryDate;

        // Early renewal preserves remaining valid subscription period
        if (tenant.getSubscriptionExpiryDate() != null && !today.isAfter(tenant.getSubscriptionExpiryDate())) {
            newStartDate = tenant.getSubscriptionStartDate() != null ? tenant.getSubscriptionStartDate() : today;
            newExpiryDate = isAnnual 
                    ? tenant.getSubscriptionExpiryDate().plusYears(1) 
                    : tenant.getSubscriptionExpiryDate().plusMonths(1);
        } else {
            // Late renewal after expiration starts new subscription cycle today
            newStartDate = today;
            newExpiryDate = isAnnual 
                    ? today.plusYears(1) 
                    : today.plusMonths(1);
        }

        Random random = new Random();
        int suffix = 1000 + random.nextInt(9000);
        String orderNumber = "REN-2026-" + suffix;
        String invoiceNumber = "INV-2026-" + suffix;
        String method = paymentMethod != null ? paymentMethod.trim() : "eSewa";

        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber(orderNumber)
                .tenantId(tenant.getId())
                .planId(plan != null ? plan.getId() : null)
                .organizationName(tenant.getOrganizationName())
                .organizationType(tenant.getOrganizationType())
                .registrationNumber(tenant.getRegistrationNumber())
                .address(tenant.getAddress())
                .adminFullName(admin.getFullName())
                .adminEmail(admin.getEmail())
                .adminPhone(admin.getPhone())
                .planTier(plan != null ? plan.getName() : "Starter")
                .billingCycle(cycle)
                .amount(amount)
                .currency("NPR")
                .paymentMethod(method)
                .paymentStatus("PENDING_PAYMENT")
                .verificationStatus("PENDING_VERIFICATION")
                .invoiceNumber(invoiceNumber)
                .transactionId(orderNumber)
                .subscriptionStartDate(newStartDate)
                .subscriptionExpiryDate(newExpiryDate)
                .build();

        subscriptionOrderRepository.save(order);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("orderNumber", orderNumber);
        resp.put("invoiceNumber", invoiceNumber);
        resp.put("newExpiryDate", newExpiryDate.toString());
        resp.put("amount", amount);
        resp.put("paymentMethod", method);

        if ("eSewa".equalsIgnoreCase(method) || "ESEWA".equalsIgnoreCase(method)) {
            String amountStr = String.valueOf(amount);
            if (amountStr.endsWith(".0")) {
                amountStr = amountStr.substring(0, amountStr.length() - 2);
            }
            String productCode = "EPAYTEST";

            Map<String, Object> esewaParams = new HashMap<>();
            esewaParams.put("amount", amountStr);
            esewaParams.put("tax_amount", "0");
            esewaParams.put("total_amount", amountStr);
            esewaParams.put("transaction_uuid", orderNumber);
            esewaParams.put("product_code", productCode);
            esewaParams.put("product_service_charge", "0");
            esewaParams.put("product_delivery_charge", "0");
            esewaParams.put("success_url", "http://localhost:8080/api/v1/subscriptions/verify-esewa");
            esewaParams.put("failure_url", "http://localhost:8080/api/v1/subscriptions/cancel?order_number=" + orderNumber + "&origin=admin");
            esewaParams.put("signed_field_names", "total_amount,transaction_uuid,product_code");

            try {
                String secretKey = "8gBm/:&EnhH.1/q";
                String message = "total_amount=" + amountStr + ",transaction_uuid=" + orderNumber + ",product_code=" + productCode;
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hash = mac.doFinal(message.getBytes("UTF-8"));
                String signature = Base64.getEncoder().encodeToString(hash);
                esewaParams.put("signature", signature);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate eSewa payment signature", e);
            }

            resp.put("gatewayUrl", "https://rc-epay.esewa.com.np/api/epay/main/v2/form");
            resp.put("formData", esewaParams);
            return resp;

        } else if ("Card".equalsIgnoreCase(method) || "Stripe".equalsIgnoreCase(method) || "STRIPE".equalsIgnoreCase(method)) {
            try {
                Stripe.apiKey = stripeApiKey;

                String successUrl = "http://localhost:8080/api/v1/subscriptions/verify-stripe?session_id={CHECKOUT_SESSION_ID}&order_number=" + orderNumber + "&origin=admin";
                String cancelUrl = "http://localhost:8080/api/v1/subscriptions/cancel?order_number=" + orderNumber + "&origin=admin";

                SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setClientReferenceId(orderNumber)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency("npr")
                                                        .setUnitAmount((long) (amount * 100))
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("OmniBook Subscription - " + (plan != null ? plan.getDisplayName() : "Starter") + " (" + cycle + ")")
                                                                        .setDescription("Clinic: " + tenant.getOrganizationName())
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

                Session session = Session.create(params);
                resp.put("gatewayUrl", session.getUrl());
                resp.put("sessionId", session.getId());
                return resp;
            } catch (Exception e) {
                log.error("Stripe session creation failed", e);
                throw new RuntimeException("Stripe Checkout failed: " + e.getMessage(), e);
            }
        }

        resp.put("message", "Subscription order initiated. Proceeding to payment...");
        return resp;
    }

    /**
     * EMERGENCY EXTENSION WORKFLOW:
     * - Admin submits request with requested days and mandatory reason.
     */
    @Transactional
    public SubscriptionExtensionRequestDTO submitExtensionRequest(Long tenantId, Integer requestedDays, String reason, String adminEmail) {
        if (requestedDays == null || requestedDays <= 0) {
            throw new RuntimeException("Requested extension days must be greater than 0.");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new RuntimeException("A valid reason/justification is required for emergency extension requests.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));

        // Check if there is already a pending extension request
        if (subscriptionExtensionRequestRepository.existsByTenantIdAndStatus(tenantId, "PENDING")) {
            throw new RuntimeException("You already have an emergency extension request pending review by the Super Admin.");
        }

        LocalDate previousExpiry = tenant.getSubscriptionExpiryDate() != null ? tenant.getSubscriptionExpiryDate() : LocalDate.now();

        SubscriptionExtensionRequest req = SubscriptionExtensionRequest.builder()
                .tenantId(tenant.getId())
                .organizationName(tenant.getOrganizationName())
                .requestedDays(requestedDays)
                .reason(reason.trim())
                .requestedByEmail(admin.getEmail())
                .requestedByName(admin.getFullName())
                .status("PENDING")
                .previousExpiryDate(previousExpiry)
                .build();

        SubscriptionExtensionRequest saved = subscriptionExtensionRequestRepository.save(req);

        try {
            notificationService.notifySuperAdmin(
                    "Emergency Subscription Extension Request",
                    tenant.getOrganizationName() + " requested a " + requestedDays + "-day extension. Reason: " + reason.trim(),
                    "SUBSCRIPTION_EXTENSION_REQUEST",
                    "/superadmin/settings"
            );
        } catch (Exception ignored) {}

        return mapExtensionDTO(saved);
    }

    /**
     * SUPER ADMIN REVIEW OF EXTENSION REQUEST:
     * - Approves or Rejects with full audit trail and updates tenant expiry.
     */
    @Transactional
    public SubscriptionExtensionRequestDTO reviewExtensionRequest(Long requestId, boolean approved, Integer approvedDays, String adminNotes, String superAdminEmail) {
        SubscriptionExtensionRequest req = subscriptionExtensionRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Extension request not found with ID: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new RuntimeException("This extension request has already been processed with status: " + req.getStatus());
        }

        User superAdmin = userRepository.findByEmail(superAdminEmail)
                .orElseGet(() -> userRepository.findByRole("super_admin").stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Super Admin not found: " + superAdminEmail)));

        Tenant tenant = tenantRepository.findById(req.getTenantId())
                .orElseThrow(() -> new RuntimeException("Associated tenant not found with ID: " + req.getTenantId()));

        LocalDate previousExpiry = tenant.getSubscriptionExpiryDate() != null ? tenant.getSubscriptionExpiryDate() : LocalDate.now();
        LocalDate baseDate = LocalDate.now().isAfter(previousExpiry) ? LocalDate.now() : previousExpiry;

        LocalDateTime now = LocalDateTime.now();
        req.setProcessedByEmail(superAdmin.getEmail());
        req.setProcessedByName(superAdmin.getFullName());
        req.setProcessedAt(now);
        req.setAdminNotes(adminNotes != null ? adminNotes.trim() : "");
        req.setPreviousExpiryDate(previousExpiry);

        if (approved) {
            int days = (approvedDays != null && approvedDays > 0) ? approvedDays : req.getRequestedDays();
            LocalDate newExpiry = baseDate.plusDays(days);
            req.setStatus("APPROVED");
            req.setApprovedDays(days);
            req.setNewExpiryDate(newExpiry);

            // Update Tenant
            tenant.setSubscriptionExpiryDate(newExpiry);
            tenant.setSubscriptionStatus("ACTIVE");
            tenant.setStatus("ACTIVE");
            tenant.setEmergencyExtensionDays(tenant.getEmergencyExtensionDays() != null ? tenant.getEmergencyExtensionDays() + days : days);
            tenant.setLastSuspendedReason(null);
            tenant.setLastReminderDaysSent("");
            tenantRepository.save(tenant);

            try {
                auditLogService.logAction(superAdmin, tenant, "APPROVED_EMERGENCY_EXTENSION_" + tenant.getOrganizationName().toUpperCase() + "_" + days + "_DAYS", "127.0.0.1");
            } catch (Exception ignored) {}
        } else {
            req.setStatus("REJECTED");
            req.setApprovedDays(0);
            try {
                auditLogService.logAction(superAdmin, tenant, "REJECTED_EMERGENCY_EXTENSION_" + tenant.getOrganizationName().toUpperCase(), "127.0.0.1");
            } catch (Exception ignored) {}
        }

        SubscriptionExtensionRequest updated = subscriptionExtensionRequestRepository.save(req);

        // Notify tenant admins
        try {
            List<User> admins = userRepository.findByTenantIdAndRole(tenant.getId(), "admin");
            for (User a : admins) {
                notificationService.createNotification(
                        a.getId(),
                        "admin",
                        tenant.getId(),
                        approved ? "Extension Request Approved" : "Extension Request Declined",
                        approved ? ("Your emergency extension request was approved! " + req.getApprovedDays() + " days granted. New expiry date: " + req.getNewExpiryDate())
                                 : ("Your emergency extension request was declined. Notes: " + (adminNotes != null ? adminNotes : "None")),
                        approved ? "EXTENSION_APPROVED" : "EXTENSION_REJECTED",
                        "/admin/subscription"
                );
            }
        } catch (Exception ignored) {}

        return mapExtensionDTO(updated);
    }

    /**
     * SUPER ADMIN DIRECT EXTEND ACTION:
     * - Grants emergency days directly to any tenant without requiring a prior request.
     */
    @Transactional
    public void manualExtendSubscription(Long tenantId, Integer extendedDays, String reason, String superAdminEmail) {
        if (extendedDays == null || extendedDays <= 0) {
            throw new RuntimeException("Extension days must be greater than 0.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));
        User superAdmin = userRepository.findByEmail(superAdminEmail)
                .orElseThrow(() -> new RuntimeException("Super Admin not found: " + superAdminEmail));

        LocalDate previousExpiry = tenant.getSubscriptionExpiryDate() != null ? tenant.getSubscriptionExpiryDate() : LocalDate.now();
        LocalDate baseDate = LocalDate.now().isAfter(previousExpiry) ? LocalDate.now() : previousExpiry;
        LocalDate newExpiry = baseDate.plusDays(extendedDays);

        tenant.setSubscriptionExpiryDate(newExpiry);
        tenant.setSubscriptionStatus("ACTIVE");
        tenant.setStatus("ACTIVE");
        tenant.setEmergencyExtensionDays(tenant.getEmergencyExtensionDays() != null ? tenant.getEmergencyExtensionDays() + extendedDays : extendedDays);
        tenant.setLastSuspendedReason(null);
        tenant.setLastReminderDaysSent("");
        tenantRepository.save(tenant);

        // Audit in SubscriptionExtensionRequest table
        SubscriptionExtensionRequest auditReq = SubscriptionExtensionRequest.builder()
                .tenantId(tenant.getId())
                .organizationName(tenant.getOrganizationName())
                .requestedDays(extendedDays)
                .approvedDays(extendedDays)
                .reason(reason != null && !reason.isBlank() ? reason.trim() : "Super Admin manual extension grant")
                .requestedByEmail(superAdmin.getEmail())
                .requestedByName(superAdmin.getFullName() + " (Super Admin Direct)")
                .status("APPROVED")
                .previousExpiryDate(previousExpiry)
                .newExpiryDate(newExpiry)
                .processedByEmail(superAdmin.getEmail())
                .processedByName(superAdmin.getFullName())
                .processedAt(LocalDateTime.now())
                .adminNotes("Direct manual extension applied by Super Admin")
                .build();
        subscriptionExtensionRequestRepository.save(auditReq);

        try {
            auditLogService.logAction(superAdmin, tenant, "DIRECT_EXTEND_SUBSCRIPTION_" + tenant.getOrganizationName().toUpperCase() + "_" + extendedDays + "_DAYS", "127.0.0.1");
        } catch (Exception ignored) {}
    }

    /**
     * SUPER ADMIN MANUAL SUSPEND:
     * - Immediately halts workspace access and triggers Renewal Gate.
     */
    @Transactional
    public void manualSuspendSubscription(Long tenantId, String reason, String superAdminEmail) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));
        User superAdmin = userRepository.findByEmail(superAdminEmail)
                .orElseGet(() -> userRepository.findByRole("super_admin").stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Super Admin not found: " + superAdminEmail)));

        String finalReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Administrative policy suspension";
        tenant.setSubscriptionStatus("SUSPENDED");
        tenant.setStatus("SUSPENDED");
        tenant.setLastSuspendedReason(finalReason);
        tenantRepository.save(tenant);

        try {
            auditLogService.logAction(superAdmin, tenant, "MANUAL_SUSPEND_TENANT_SUBSCRIPTION_" + tenant.getOrganizationName().toUpperCase(), "127.0.0.1");
        } catch (Exception ignored) {}
    }

    /**
     * SUPER ADMIN MANUAL REACTIVATE:
     * - Restores active workspace access.
     */
    @Transactional
    public void manualReactivateSubscription(Long tenantId, String superAdminEmail) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));
        User superAdmin = userRepository.findByEmail(superAdminEmail)
                .orElseGet(() -> userRepository.findByRole("super_admin").stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Super Admin not found: " + superAdminEmail)));

        LocalDate expiry = tenant.getSubscriptionExpiryDate();
        if (expiry == null || LocalDate.now().isAfter(expiry)) {
            // Give 7 days grace to allow active use
            tenant.setSubscriptionExpiryDate(LocalDate.now().plusDays(7));
        }

        tenant.setSubscriptionStatus("ACTIVE");
        tenant.setStatus("ACTIVE");
        tenant.setLastSuspendedReason(null);
        tenantRepository.save(tenant);

        try {
            auditLogService.logAction(superAdmin, tenant, "MANUAL_REACTIVATE_TENANT_SUBSCRIPTION_" + tenant.getOrganizationName().toUpperCase(), "127.0.0.1");
        } catch (Exception ignored) {}
    }

    /**
     * ADMIN SUBSCRIPTION OVERVIEW:
     * - Returns comprehensive subscription state, remaining days, invoices, and limits.
     */
    public AdminSubscriptionOverviewDTO getAdminSubscriptionOverview(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));
        Tenant tenant = admin.getTenant();
        if (tenant == null) {
            throw new RuntimeException("Admin is not associated with any organization.");
        }

        LocalDate today = LocalDate.now();
        LocalDate expiry = tenant.getSubscriptionExpiryDate();
        if (expiry == null) {
            expiry = tenant.getCreatedAt() != null ? tenant.getCreatedAt().toLocalDate().plusMonths(1) : today.plusMonths(1);
            tenant.setSubscriptionExpiryDate(expiry);
            tenant.setSubscriptionStartDate(tenant.getCreatedAt() != null ? tenant.getCreatedAt().toLocalDate() : today);
            tenantRepository.save(tenant);
        }

        LocalDate start = tenant.getSubscriptionStartDate() != null ? tenant.getSubscriptionStartDate() : today.minusMonths(1);
        long daysRemaining = ChronoUnit.DAYS.between(today, expiry);
        boolean isExpired = today.isAfter(expiry) || "EXPIRED".equalsIgnoreCase(tenant.getSubscriptionStatus());
        boolean isSuspended = "SUSPENDED".equalsIgnoreCase(tenant.getSubscriptionStatus()) || "SUSPENDED".equalsIgnoreCase(tenant.getStatus());
        boolean isExpiringSoon = daysRemaining <= 7 && !isExpired && !isSuspended;

        String effectiveStatus = isSuspended ? "SUSPENDED" : (isExpired ? "EXPIRED" : (isExpiringSoon ? "EXPIRING_SOON" : "ACTIVE"));

        SubscriptionPlan plan = subscriptionPlanService.getPlanEntityByName(tenant.getSubscriptionTier());
        List<String> features = plan != null && plan.getFeatures() != null 
                ? Arrays.stream(plan.getFeatures().split("\\r?\\n")).filter(s -> !s.isBlank()).collect(Collectors.toList())
                : Collections.emptyList();

        SubscriptionOrder latestOrder = revenueAnalyticsService.getLatestValidSubscriptionOrder(tenant);
        Double lastPaid = latestOrder != null ? latestOrder.getAmount() : (plan != null ? plan.getMonthlyPrice() : 2000.0);

        SubscriptionExtensionRequest pendingExt = subscriptionExtensionRequestRepository
                .findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .findFirst()
                .orElse(null);

        // Fetch tenant invoices
        List<PlatformInvoiceDTO> invoices = platformInvoiceRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> (i.getTenantId() != null && i.getTenantId().equals(tenant.getId()))
                        || (tenant.getRegistrationNumber() != null && tenant.getRegistrationNumber().equalsIgnoreCase(i.getRegistrationNumber()))
                        || (tenant.getOrganizationName() != null && tenant.getOrganizationName().equalsIgnoreCase(i.getOrganizationName())))
                .map(this::mapInvoiceToDTO)
                .collect(Collectors.toList());

        String activePlanName = tenant.getSubscriptionTier() != null ? tenant.getSubscriptionTier() : "Starter";
        String displayPlanName = plan != null ? plan.getDisplayName() : activePlanName;

        // Live capacity utilization
        long currentStaff = userRepository.findByTenantIdAndRole(tenant.getId(), "service_provider").size()
                + invitationRepository.countByTenantAndRoleAndUsedFalse(tenant, "service_provider");

        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        long currentMonthAppts = appointmentRepository.findByTenantId(tenant.getId()).stream()
                .filter(a -> {
                    LocalDate d = a.getAppointmentDate();
                    return d != null && !d.isBefore(startOfMonth) && !d.isAfter(endOfMonth)
                            && !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus());
                })
                .count();

        return AdminSubscriptionOverviewDTO.builder()
                .tenantId(tenant.getId())
                .organizationName(tenant.getOrganizationName())
                .organizationType(tenant.getOrganizationType())
                .registrationNumber(tenant.getRegistrationNumber())
                .planTier(activePlanName)
                .currentPlan(activePlanName)
                .plan(activePlanName)
                .planDisplayName(displayPlanName)
                .displayName(displayPlanName)
                .billingCycle(tenant.getBillingCycle() != null ? tenant.getBillingCycle() : "Monthly")
                .lastPaidAmount(lastPaid)
                .amountPaid(lastPaid)
                .monthlyPrice(plan != null ? plan.getMonthlyPrice() : 2000.0)
                .annualPrice(plan != null ? plan.getAnnualPrice() : 20400.0)
                .currency("NPR")
                .startDate(start)
                .expiryDate(expiry)
                .subscriptionStartDate(start)
                .subscriptionExpiryDate(expiry)
                .daysRemaining(daysRemaining)
                .remainingDays(daysRemaining)
                .status(effectiveStatus)
                .subscriptionStatus(effectiveStatus)
                .isExpired(isExpired)
                .isSuspended(isSuspended)
                .isExpiringSoon(isExpiringSoon)
                .isGated("EXPIRED".equalsIgnoreCase(effectiveStatus) || "SUSPENDED".equalsIgnoreCase(effectiveStatus))
                .suspendedReason(tenant.getLastSuspendedReason())
                .lastSuspendedReason(tenant.getLastSuspendedReason())
                .emergencyExtensionDays(tenant.getEmergencyExtensionDays() != null ? tenant.getEmergencyExtensionDays() : 0)
                .hasPendingExtensionRequest(pendingExt != null)
                .hasPendingExtension(pendingExt != null)
                .pendingExtensionRequest(pendingExt != null ? mapExtensionDTO(pendingExt) : null)
                .planFeatures(features)
                .features(features)
                .userLimit(plan != null ? plan.getUserLimit() : -1)
                .currentUsers(currentStaff)
                .appointmentLimit(plan != null ? plan.getAppointmentLimit() : -1)
                .currentAppointmentsThisMonth(currentMonthAppts)
                .invoices(invoices)
                .build();
    }

    /**
     * SUPER ADMIN TENANT SUBSCRIPTION DIRECTORY:
     * - Returns all organizations with live subscription status and days remaining.
     */
    public List<SuperadminSubscriptionTenantDTO> getSuperadminTenantSubscriptions() {
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDate today = LocalDate.now();

        return tenants.stream().map(t -> {
            LocalDate expiry = t.getSubscriptionExpiryDate();
            if (expiry == null) {
                expiry = t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate().plusMonths(1) : today.plusMonths(1);
            }
            LocalDate start = t.getSubscriptionStartDate() != null ? t.getSubscriptionStartDate() : (t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : today);
            long daysLeft = ChronoUnit.DAYS.between(today, expiry);

            boolean isExpired = today.isAfter(expiry) || "EXPIRED".equalsIgnoreCase(t.getSubscriptionStatus());
            boolean isSuspended = "SUSPENDED".equalsIgnoreCase(t.getSubscriptionStatus()) || "SUSPENDED".equalsIgnoreCase(t.getStatus());
            boolean isExpiringSoon = daysLeft <= 7 && !isExpired && !isSuspended;
            String effStatus = isSuspended ? "SUSPENDED" : (isExpired ? "EXPIRED" : (isExpiringSoon ? "EXPIRING_SOON" : "ACTIVE"));

            SubscriptionOrder order = revenueAnalyticsService.getLatestValidSubscriptionOrder(t);
            double mrr = revenueAnalyticsService.calculateTenantMRR(t);

            List<User> admins = userRepository.findByTenantIdAndRole(t.getId(), "admin");
            String adminEmail = !admins.isEmpty() ? admins.get(0).getEmail() : "N/A";
            String adminName = !admins.isEmpty() ? admins.get(0).getFullName() : "N/A";

            boolean hasPendingExt = subscriptionExtensionRequestRepository.existsByTenantIdAndStatus(t.getId(), "PENDING");

            return SuperadminSubscriptionTenantDTO.builder()
                    .tenantId(t.getId())
                    .organizationName(t.getOrganizationName())
                    .organizationType(t.getOrganizationType())
                    .registrationNumber(t.getRegistrationNumber())
                    .adminEmail(adminEmail)
                    .adminName(adminName)
                    .planTier(t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter")
                    .billingCycle(t.getBillingCycle() != null ? t.getBillingCycle() : "Monthly")
                    .lastPaidAmount(order != null ? order.getAmount() : mrr)
                    .currentMrr(mrr)
                    .startDate(start)
                    .expiryDate(expiry)
                    .daysRemaining(daysLeft)
                    .subscriptionStatus(effStatus)
                    .tenantStatus(t.getStatus())
                    .emergencyExtensionDays(t.getEmergencyExtensionDays() != null ? t.getEmergencyExtensionDays() : 0)
                    .lastSuspendedReason(t.getLastSuspendedReason())
                    .hasPendingExtension(hasPendingExt)
                    .latestOrderId(order != null ? order.getId() : null)
                    .latestOrderNumber(order != null ? order.getOrderNumber() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<SubscriptionExtensionRequestDTO> getExtensionRequestsForTenant(Long tenantId) {
        return subscriptionExtensionRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::mapExtensionDTO)
                .collect(Collectors.toList());
    }

    public List<SubscriptionExtensionRequestDTO> getAllExtensionRequests() {
        return subscriptionExtensionRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapExtensionDTO)
                .collect(Collectors.toList());
    }

    private SubscriptionExtensionRequestDTO mapExtensionDTO(SubscriptionExtensionRequest req) {
        Tenant tenant = req.getTenantId() != null ? tenantRepository.findById(req.getTenantId()).orElse(null) : null;
        String orgName = req.getOrganizationName();
        if ((orgName == null || orgName.trim().isEmpty()) && tenant != null) {
            orgName = tenant.getOrganizationName();
        }
        String orgType = tenant != null ? tenant.getOrganizationType() : null;
        String regNum = tenant != null ? tenant.getRegistrationNumber() : null;
        String planTier = tenant != null ? (tenant.getSubscriptionTier() != null ? tenant.getSubscriptionTier() : "Starter") : "Starter";
        String phone = tenant != null ? tenant.getPhoneContact() : null;
        LocalDate prevExpiry = req.getPreviousExpiryDate() != null ? req.getPreviousExpiryDate() : (tenant != null ? tenant.getSubscriptionExpiryDate() : null);

        return SubscriptionExtensionRequestDTO.builder()
                .id(req.getId())
                .tenantId(req.getTenantId())
                .organizationName(orgName != null ? orgName : "Organization #" + req.getTenantId())
                .organizationType(orgType)
                .registrationNumber(regNum)
                .currentPlanTier(planTier)
                .phone(phone)
                .requestedDays(req.getRequestedDays())
                .reason(req.getReason())
                .requestedByEmail(req.getRequestedByEmail())
                .requestedByName(req.getRequestedByName())
                .status(req.getStatus())
                .previousExpiryDate(prevExpiry)
                .newExpiryDate(req.getNewExpiryDate())
                .approvedDays(req.getApprovedDays())
                .processedByEmail(req.getProcessedByEmail())
                .processedByName(req.getProcessedByName())
                .processedAt(req.getProcessedAt())
                .adminNotes(req.getAdminNotes())
                .createdAt(req.getCreatedAt())
                .build();
    }

    private PlatformInvoiceDTO mapInvoiceToDTO(PlatformInvoice inv) {
        return PlatformInvoiceDTO.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .orderNumber(inv.getOrderNumber())
                .invoiceDate(inv.getInvoiceDate())
                .amount(inv.getAmount())
                .currency(inv.getCurrency())
                .status(inv.getStatus())
                .planName(inv.getPlanName())
                .billingPeriod(inv.getBillingPeriod())
                .billingCycle(inv.getBillingCycle())
                .organizationName(inv.getOrganizationName())
                .organizationType(inv.getOrganizationType())
                .registrationNumber(inv.getRegistrationNumber())
                .address(inv.getAddress())
                .adminFullName(inv.getAdminFullName())
                .adminEmail(inv.getAdminEmail())
                .adminPhone(inv.getAdminPhone())
                .paymentMethod(inv.getPaymentMethod())
                .verificationStatus(inv.getVerificationStatus())
                .transactionId(inv.getTransactionId())
                .refundId(inv.getRefundId())
                .refundReason(inv.getRefundReason())
                .refundedAt(inv.getRefundedAt() != null ? inv.getRefundedAt().toString() : null)
                .build();
    }

    public List<SubscriptionOrder> getAllOrders() {
        return subscriptionOrderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Map<String, Object> sendVerificationUpdateEmail(Long invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + invoiceId));

        if (invoice.getAdminEmail() == null || invoice.getAdminEmail().isBlank()) {
            throw new RuntimeException("Invoice record does not contain an administrator email address.");
        }

        SubscriptionOrder order = null;
        if (invoice.getOrderNumber() != null) {
            order = subscriptionOrderRepository.findByOrderNumber(invoice.getOrderNumber()).orElse(null);
        }

        String planTier = order != null ? order.getPlanTier() : (invoice.getPlanName() != null ? invoice.getPlanName() : "Enterprise");
        String billingCycle = order != null ? order.getBillingCycle() : (invoice.getBillingCycle() != null ? invoice.getBillingCycle() : "Monthly");

        emailService.sendSubscriptionVerificationUpdateEmail(
                invoice.getAdminEmail(),
                invoice.getAdminFullName(),
                invoice.getOrganizationName(),
                invoice.getOrganizationType(),
                planTier,
                billingCycle,
                invoice.getOrderNumber(),
                invoice.getInvoiceNumber(),
                invoice.getPaymentMethod()
        );

        if (order != null) {
            order.setVerificationStatus("VERIFICATION_IN_PROGRESS");
            subscriptionOrderRepository.save(order);
        }
        invoice.setVerificationStatus("VERIFICATION_IN_PROGRESS");
        platformInvoiceRepository.save(invoice);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Verification update email dispatched successfully to " + invoice.getAdminEmail());
        response.put("invoiceId", invoice.getId());
        response.put("adminEmail", invoice.getAdminEmail());
        response.put("verificationStatus", "VERIFICATION_IN_PROGRESS");
        return response;
    }

    @Transactional
    public Map<String, Object> rejectAndRefundSubscription(Long invoiceId, String reason, String adminUsername) {
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Platform invoice not found with ID: " + invoiceId));

        if ("Refunded".equalsIgnoreCase(invoice.getStatus())) {
            throw new RuntimeException("This invoice has already been refunded.");
        }

        SubscriptionOrder order = null;
        if (invoice.getOrderNumber() != null) {
            order = subscriptionOrderRepository.findByOrderNumber(invoice.getOrderNumber()).orElse(null);
        }

        String paymentMethod = invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : (order != null ? order.getPaymentMethod() : "eSewa");
        String transactionId = invoice.getTransactionId() != null ? invoice.getTransactionId() : (order != null ? order.getTransactionId() : invoice.getOrderNumber());
        String refundId = null;
        LocalDateTime now = LocalDateTime.now();

        // 1. Gateway refund processing
        if ("Stripe".equalsIgnoreCase(paymentMethod) || "Card".equalsIgnoreCase(paymentMethod)) {
            try {
                Stripe.apiKey = stripeApiKey;
                String targetPaymentIntent = transactionId;
                if (targetPaymentIntent != null && targetPaymentIntent.startsWith("cs_")) {
                    Session session = Session.retrieve(targetPaymentIntent);
                    if (session.getPaymentIntent() != null) {
                        targetPaymentIntent = session.getPaymentIntent();
                    }
                }

                if (targetPaymentIntent != null && targetPaymentIntent.startsWith("pi_")) {
                    com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(targetPaymentIntent)
                            .setReason(com.stripe.param.RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                            .build();
                    com.stripe.model.Refund stripeRefund = com.stripe.model.Refund.create(params);
                    refundId = stripeRefund.getId();
                } else {
                    refundId = "REF-STRIPE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
            } catch (Exception e) {
                log.error("Stripe refund exception: ", e);
                throw new RuntimeException("Failed to process payment gateway refund: " + e.getMessage(), e);
            }
        } else if ("eSewa".equalsIgnoreCase(paymentMethod)) {
            refundId = "REF-ESEWA-" + (transactionId != null ? transactionId.replace("ORD-", "") : "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } else {
            refundId = "REF-BANK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Subscription request rejected after administrative verification review";

        String inviteEmail = null;
        if (invoice.getAdminEmail() != null && !invoice.getAdminEmail().isBlank()) {
            inviteEmail = invoice.getAdminEmail().trim();
        } else if (order != null && order.getAdminEmail() != null && !order.getAdminEmail().isBlank()) {
            inviteEmail = order.getAdminEmail().trim();
            invoice.setAdminEmail(inviteEmail);
        }

        // 2. Update PlatformInvoice (preserve history)
        invoice.setStatus("Refunded");
        invoice.setVerificationStatus("REJECTED");
        invoice.setRefundId(refundId);
        invoice.setRefundReason(finalReason);
        invoice.setRefundedAt(now);
        platformInvoiceRepository.save(invoice);

        // 3. Update SubscriptionOrder (preserve history)
        if (order != null) {
            order.setPaymentStatus("REFUNDED");
            order.setVerificationStatus("REJECTED");
            order.setRefundId(refundId);
            order.setRefundReason(finalReason);
            order.setRefundedAt(now);
            subscriptionOrderRepository.save(order);
        }

        // 4. Send Email Notification
        if (inviteEmail != null && !inviteEmail.isBlank()) {
            try {
                String planTier = order != null ? order.getPlanTier() : (invoice.getPlanName() != null ? invoice.getPlanName() : "Standard");
                String adminName = invoice.getAdminFullName() != null ? invoice.getAdminFullName() : (order != null ? order.getAdminFullName() : "Administrator");

                emailService.sendSubscriptionRejectionRefundEmail(
                        inviteEmail,
                        adminName,
                        invoice.getOrganizationName(),
                        planTier,
                        invoice.getAmount(),
                        invoice.getCurrency() != null ? invoice.getCurrency() : "NPR",
                        paymentMethod,
                        invoice.getOrderNumber(),
                        invoice.getInvoiceNumber(),
                        transactionId,
                        refundId,
                        finalReason,
                        now
                );
            } catch (Exception e) {
                log.error("Failed to send rejection refund email: ", e);
            }
        }

        // 5. Send Super Admin notification
        try {
            notificationService.notifySuperAdmin(
                    "Subscription Rejected & Refunded",
                    "Subscription request for " + invoice.getOrganizationName() + " (Order #" + invoice.getOrderNumber() + ") was rejected. Refund reference: " + refundId,
                    "SUBSCRIPTION_REFUNDED",
                    "/superadmin/settings"
            );
        } catch (Exception ignored) {}

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Subscription request rejected and full refund processed successfully.");
        response.put("invoiceId", invoice.getId());
        response.put("orderNumber", invoice.getOrderNumber());
        response.put("refundId", refundId);
        response.put("refundReason", finalReason);
        response.put("status", "Refunded");
        response.put("verificationStatus", "REJECTED");
        return response;
    }

    // Convenience overload for Admin renew endpoint
    @Transactional
    public Map<String, Object> renewSubscription(String adminEmail, String billingCycle, String paymentMethod) {
        return renewSubscription(adminEmail, null, billingCycle, paymentMethod);
    }

    @Transactional
    public Map<String, Object> renewSubscription(String adminEmail, String planName, String billingCycle, String paymentMethod) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));
        Tenant tenant = admin.getTenant();
        if (tenant == null) {
            throw new RuntimeException("Admin is not associated with any organization.");
        }
        String targetTier = (planName != null && !planName.isBlank()) ? planName : tenant.getSubscriptionTier();
        return renewSubscription(tenant.getId(), targetTier, billingCycle, paymentMethod, adminEmail);
    }

    // Convenience overload for Admin extension request submission
    @Transactional
    public SubscriptionExtensionRequestDTO submitExtensionRequest(String adminEmail, SubscriptionExtensionRequestDTO dto) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));
        Tenant tenant = admin.getTenant();
        if (tenant == null) {
            throw new RuntimeException("Admin is not associated with any organization.");
        }
        return submitExtensionRequest(tenant.getId(), dto.getRequestedDays(), dto.getReason(), adminEmail);
    }

    // Convenience overload for Admin extension request listing
    public List<SubscriptionExtensionRequestDTO> getTenantExtensionRequests(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminEmail));
        Tenant tenant = admin.getTenant();
        if (tenant == null) {
            return Collections.emptyList();
        }
        return getExtensionRequestsForTenant(tenant.getId());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionOrderLookupDTO> lookupOrdersForProvisioning(String query) {
        List<SubscriptionOrder> orders;
        if (query == null || query.trim().isEmpty()) {
            orders = subscriptionOrderRepository.findTop20ByOrderByCreatedAtDesc();
        } else {
            orders = subscriptionOrderRepository.searchOrders(query.trim());
        }

        return orders.stream().map(o -> SubscriptionOrderLookupDTO.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .invoiceNumber(o.getInvoiceNumber())
                .organizationName(o.getOrganizationName())
                .organizationType(o.getOrganizationType())
                .registrationNumber(o.getRegistrationNumber())
                .address(o.getAddress())
                .adminFullName(o.getAdminFullName())
                .adminEmail(o.getAdminEmail())
                .adminPhone(o.getAdminPhone())
                .planTier(o.getPlanTier())
                .billingCycle(o.getBillingCycle())
                .amount(o.getAmount())
                .currency(o.getCurrency())
                .paymentStatus(o.getPaymentStatus())
                .verificationStatus(o.getVerificationStatus())
                .tenantId(o.getTenantId())
                .createdAt(o.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }
}

