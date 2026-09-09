package com.backend.controller;

import com.backend.dto.SubscriptionPurchaseRequest;
import com.backend.dto.SubscriptionPurchaseResponse;
import com.backend.model.SubscriptionOrder;
import com.backend.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateSubscription(@Valid @RequestBody SubscriptionPurchaseRequest request) {
        try {
            Map<String, Object> result = subscriptionService.initiateSubscription(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/verify-esewa")
    public ResponseEntity<Void> verifyEsewa(@RequestParam(value = "data", required = false) String data) {
        if (data == null || data.isEmpty()) {
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=no_data")).build();
        }

        try {
            String orderNumber = subscriptionService.verifyEsewaSubscription(data);
            if (orderNumber != null) {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?subscription_success=true&order_number=" + orderNumber)).build();
            } else {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=verification_failed")).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=exception")).build();
        }
    }

    @GetMapping("/verify-stripe")
    public ResponseEntity<Void> verifyStripe(
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "order_number", required = false) String orderNumber) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=no_session")).build();
        }

        try {
            String verifiedOrderNumber = subscriptionService.verifyStripeSubscription(sessionId, orderNumber);
            if (verifiedOrderNumber != null) {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?subscription_success=true&order_number=" + verifiedOrderNumber)).build();
            } else {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=verification_failed")).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/pricing?payment=failed&error=exception")).build();
        }
    }

    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<?> getSubscriptionOrder(@PathVariable String orderNumber) {
        try {
            SubscriptionPurchaseResponse response = subscriptionService.getOrderResponse(orderNumber);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/purchase")
    public ResponseEntity<SubscriptionPurchaseResponse> purchaseSubscription(
            @Valid @RequestBody SubscriptionPurchaseRequest request) {
        return ResponseEntity.ok(subscriptionService.purchaseSubscription(request));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SubscriptionOrder>> getAllOrders() {
        return ResponseEntity.ok(subscriptionService.getAllOrders());
    }

    @PostMapping("/invoices/{invoiceId}/send-verification-update")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> sendVerificationUpdateEmail(@PathVariable Long invoiceId) {
        try {
            Map<String, Object> result = subscriptionService.sendVerificationUpdateEmail(invoiceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/invoices/{invoiceId}/reject-and-refund")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> rejectAndRefundSubscription(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) Map<String, String> payload,
            java.security.Principal principal) {
        try {
            String reason = payload != null ? payload.get("reason") : null;
            String adminUsername = principal != null ? principal.getName() : "Super Admin";
            Map<String, Object> result = subscriptionService.rejectAndRefundSubscription(invoiceId, reason, adminUsername);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
