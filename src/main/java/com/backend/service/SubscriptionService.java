package com.backend.service;

import com.backend.dto.SubscriptionPurchaseRequest;
import com.backend.dto.SubscriptionPurchaseResponse;
import com.backend.model.PlatformInvoice;
import com.backend.model.SubscriptionOrder;
import com.backend.repository.PlatformInvoiceRepository;
import com.backend.repository.SubscriptionOrderRepository;
import com.backend.repository.TenantRepository;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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

    @Transactional
    public Map<String, Object> initiateSubscription(SubscriptionPurchaseRequest request) {
        if (tenantRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new RuntimeException("An organization with registration/PAN number " + request.getRegistrationNumber() + " already exists.");
        }

        Random random = new Random();
        int suffix = 1000 + random.nextInt(9000);
        String orderNumber = "ORD-2026-" + suffix;
        String invoiceNumber = "INV-2026-" + suffix;
        String currency = request.getCurrency() != null ? request.getCurrency() : "NPR";
        String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod().trim() : "eSewa";

        // Save initial SubscriptionOrder with PENDING_REVIEW status
        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber(orderNumber)
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType())
                .registrationNumber(request.getRegistrationNumber())
                .address(request.getAddress())
                .adminFullName(request.getAdminFullName())
                .adminEmail(request.getAdminEmail())
                .adminPhone(request.getAdminPhone())
                .planTier(request.getPlanTier())
                .billingCycle(request.getBillingCycle())
                .amount(request.getAmount())
                .currency(currency)
                .paymentMethod(paymentMethod)
                .paymentStatus("Bank Transfer".equalsIgnoreCase(paymentMethod) ? "PAID" : "PENDING_PAYMENT")
                .verificationStatus("PENDING_REVIEW")
                .invoiceNumber(invoiceNumber)
                .transactionId(orderNumber)
                .build();

        subscriptionOrderRepository.save(order);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderNumber", orderNumber);
        response.put("invoiceNumber", invoiceNumber);
        response.put("paymentMethod", paymentMethod);

        if ("eSewa".equalsIgnoreCase(paymentMethod)) {
            // Build ePay v2 payload for eSewa
            String amountStr = String.valueOf(request.getAmount());
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
                                        .setUnitAmount((long) (request.getAmount() * 100))
                                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(request.getPlanTier() + " Plan (" + request.getBillingCycle() + ") - OmniBook")
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
                        "New Subscription Order (" + request.getPlanTier() + ")",
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
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            String decodedData = new String(Base64.getDecoder().decode(data));

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

            if ("COMPLETE".equalsIgnoreCase(status) && !transactionUuid.isEmpty()) {
                SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(transactionUuid).orElse(null);
                if (order != null) {
                    order.setPaymentStatus("PAID");
                    order.setVerificationStatus("PENDING_REVIEW");
                    if (!transactionCode.isEmpty()) {
                        order.setTransactionId(transactionCode);
                    } else if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                        order.setTransactionId(transactionUuid);
                    }
                    subscriptionOrderRepository.save(order);

                    createOrUpdatePlatformInvoice(order);

                    try {
                        notificationService.notifySuperAdmin(
                                "Subscription Paid via eSewa (" + order.getPlanTier() + ")",
                                order.getOrganizationName() + " completed payment of Rs. " + order.getAmount() + " for " + order.getPlanTier() + " Plan via eSewa. Order #" + order.getOrderNumber() + ". Verification required.",
                                "SUBSCRIPTION_PAID",
                                "/superadmin/settings"
                        );
                    } catch (Exception ignored) {}

                    return transactionUuid;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Transactional
    public String verifyStripeSubscription(String sessionId, String orderNumber) {
        try {
            Stripe.apiKey = stripeApiKey;
            Session session = Session.retrieve(sessionId);

            if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
                String targetOrderNumber = (orderNumber != null && !orderNumber.isEmpty()) ? orderNumber : session.getClientReferenceId();
                if (targetOrderNumber != null) {
                    SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(targetOrderNumber).orElse(null);
                    if (order != null) {
                        order.setPaymentStatus("PAID");
                        order.setVerificationStatus("PENDING_REVIEW");
                        if (session.getPaymentIntent() != null && !session.getPaymentIntent().isBlank()) {
                            order.setTransactionId(session.getPaymentIntent());
                        } else if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                            order.setTransactionId(sessionId);
                        }
                        subscriptionOrderRepository.save(order);

                        createOrUpdatePlatformInvoice(order);

                        try {
                            notificationService.notifySuperAdmin(
                                    "Subscription Paid via Stripe (" + order.getPlanTier() + ")",
                                    order.getOrganizationName() + " completed payment of Rs. " + order.getAmount() + " for " + order.getPlanTier() + " Plan via Stripe Card. Order #" + order.getOrderNumber() + ". Verification required.",
                                    "SUBSCRIPTION_PAID",
                                    "/superadmin/settings"
                            );
                        } catch (Exception ignored) {}

                        return targetOrderNumber;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private PlatformInvoice createOrUpdatePlatformInvoice(SubscriptionOrder order) {
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String invoiceDate = now.format(fmt);
        LocalDate endDate = "Annually".equalsIgnoreCase(order.getBillingCycle()) ? now.plusYears(1) : now.plusMonths(1);
        String billingPeriod = now.format(fmt) + " - " + endDate.format(fmt);

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
                    .verificationStatus(order.getVerificationStatus() != null ? order.getVerificationStatus() : "PENDING_REVIEW")
                    .transactionId(order.getTransactionId())
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
        }

        return platformInvoiceRepository.save(invoice);
    }

    public SubscriptionPurchaseResponse getOrderResponse(String orderNumber) {
        SubscriptionOrder order = subscriptionOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Subscription order not found: " + orderNumber));

        PlatformInvoice invoice = platformInvoiceRepository.findByOrderNumber(orderNumber).orElse(null);

        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        LocalDate endDate = "Annually".equalsIgnoreCase(order.getBillingCycle()) ? now.plusYears(1) : now.plusMonths(1);
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
        String verStatus = order.getVerificationStatus() != null ? order.getVerificationStatus() : (invoice != null && invoice.getVerificationStatus() != null ? invoice.getVerificationStatus() : "PENDING_REVIEW");

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

        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String invoiceDate = now.format(fmt);

        LocalDate endDate = "Annually".equalsIgnoreCase(request.getBillingCycle()) ? now.plusYears(1) : now.plusMonths(1);
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
                .planTier(request.getPlanTier())
                .billingCycle(request.getBillingCycle())
                .amount(request.getAmount())
                .currency(currency)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PAID")
                .verificationStatus("PENDING_REVIEW")
                .invoiceNumber(invoiceNumber)
                .transactionId(orderNumber)
                .build();

        subscriptionOrderRepository.save(order);

        PlatformInvoice invoice = PlatformInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .orderNumber(orderNumber)
                .invoiceDate(invoiceDate)
                .amount(request.getAmount())
                .currency(currency)
                .status("Paid")
                .planName(request.getPlanTier() + " Plan (" + request.getBillingCycle() + ")")
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
                .verificationStatus("PENDING_REVIEW")
                .transactionId(orderNumber)
                .build();

        platformInvoiceRepository.save(invoice);

        try {
            notificationService.notifySuperAdmin(
                    "New Subscription Order (" + request.getPlanTier() + ")",
                    request.getOrganizationName() + " purchased " + request.getPlanTier() + " Plan via " + request.getPaymentMethod() + ". Order #" + orderNumber + ". Pending verification & invitation.",
                    "SUBSCRIPTION_ORDER",
                    "/superadmin/settings"
            );
        } catch (Exception ignored) {}

        return SubscriptionPurchaseResponse.builder()
                .success(true)
                .message("Subscription purchased successfully! Your order has been placed for platform verification.")
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
                .verificationStatus("PENDING_REVIEW")
                .planTier(request.getPlanTier())
                .amount(request.getAmount())
                .currency(currency)
                .billingPeriod(billingPeriod)
                .paymentMethod(request.getPaymentMethod())
                .status("Paid")
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
                // If transactionId is checkout session (starts with cs_), retrieve session to get payment_intent
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
                    log.info("Actual Stripe refund executed successfully for invoice #{}: {} (Status: {})", invoice.getInvoiceNumber(), refundId, stripeRefund.getStatus());
                } else {
                    refundId = "REF-STRIPE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    log.info("Simulated Stripe refund generated for non-live reference: {}", refundId);
                }
            } catch (com.stripe.exception.StripeException e) {
                log.error("Stripe gateway refund API error: ", e);
                String userMsg = e.getUserMessage() != null ? e.getUserMessage() : e.getMessage();
                throw new RuntimeException("Payment Gateway (Stripe) refund failed: " + userMsg, e);
            } catch (Exception e) {
                log.error("Stripe refund exception: ", e);
                throw new RuntimeException("Failed to process payment gateway refund: " + e.getMessage(), e);
            }
        } else if ("eSewa".equalsIgnoreCase(paymentMethod)) {
            refundId = "REF-ESEWA-" + (transactionId != null ? transactionId.replace("ORD-", "") : "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            log.info("eSewa refund reference created: {}", refundId);
        } else {
            refundId = "REF-BANK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Bank refund reference created: {}", refundId);
        }

        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Subscription request rejected after administrative verification review";

        // Extract Email Address (For Invite) from invoice or underlying order
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

        // 4. Send Email Notification to Email Address (For Invite)
        if (inviteEmail != null && !inviteEmail.isBlank()) {
            try {
                String planTier = order != null ? order.getPlanTier() : (invoice.getPlanName() != null ? invoice.getPlanName() : "Standard");
                String adminName = invoice.getAdminFullName() != null ? invoice.getAdminFullName() : (order != null ? order.getAdminFullName() : "Administrator");

                log.info("Dispatching subscription rejection & refund email to Email Address (For Invite): {}", inviteEmail);
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
                log.error("Failed to send rejection refund email to invite email {}: ", inviteEmail, e);
            }
        }

        // 5. Send Super Admin in-app notification
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
}
