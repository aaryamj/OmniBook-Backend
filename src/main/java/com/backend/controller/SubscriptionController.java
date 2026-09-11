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
    private final com.backend.service.SubscriptionPlanService subscriptionPlanService;

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
    public ResponseEntity<Void> verifyEsewa(
            @RequestParam(value = "data", required = false) String data,
            @RequestParam(value = "origin", required = false) String origin) {
        String failureBase = "admin".equalsIgnoreCase(origin)
                ? "http://localhost:5173/admin/subscription?payment=failed"
                : "http://localhost:5173/pricing?payment=failed";

        if (data == null || data.isEmpty()) {
            return ResponseEntity.status(302).location(URI.create(failureBase + "&error=no_data")).build();
        }

        try {
            String orderNumber = subscriptionService.verifyEsewaSubscription(data);
            if (orderNumber != null) {
                String successUrl = "admin".equalsIgnoreCase(origin)
                        ? "http://localhost:5173/admin/subscription?payment_success=true&order_number=" + orderNumber
                        : "http://localhost:5173/pricing?subscription_success=true&order_number=" + orderNumber;
                return ResponseEntity.status(302).location(URI.create(successUrl)).build();
            } else {
                return ResponseEntity.status(302).location(URI.create(failureBase + "&error=verification_failed")).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create(failureBase + "&error=exception")).build();
        }
    }

    @GetMapping("/verify-stripe")
    public ResponseEntity<Void> verifyStripe(
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "order_number", required = false) String orderNumber,
            @RequestParam(value = "origin", required = false) String origin) {
        String failureBase = "admin".equalsIgnoreCase(origin)
                ? "http://localhost:5173/admin/subscription?payment=failed"
                : "http://localhost:5173/pricing?payment=failed";

        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(302).location(URI.create(failureBase + "&error=no_session")).build();
        }

        try {
            String verifiedOrderNumber = subscriptionService.verifyStripeSubscription(sessionId, orderNumber);
            if (verifiedOrderNumber != null) {
                String successUrl = "admin".equalsIgnoreCase(origin)
                        ? "http://localhost:5173/admin/subscription?payment_success=true&order_number=" + verifiedOrderNumber
                        : "http://localhost:5173/pricing?subscription_success=true&order_number=" + verifiedOrderNumber;
                return ResponseEntity.status(302).location(URI.create(successUrl)).build();
            } else {
                return ResponseEntity.status(302).location(URI.create(failureBase + "&error=verification_failed")).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create(failureBase + "&error=exception")).build();
        }
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancelSubscriptionPayment(
            @RequestParam(value = "order_number", required = false) String orderNumber,
            @RequestParam(value = "origin", required = false) String origin) {
        if (orderNumber != null && !orderNumber.isBlank()) {
            subscriptionService.markOrderCancelled(orderNumber);
        }
        String cancelUrl = "admin".equalsIgnoreCase(origin)
                ? "http://localhost:5173/admin/subscription?payment=cancelled"
                : "http://localhost:5173/pricing?payment=cancelled";
        return ResponseEntity.status(302).location(URI.create(cancelUrl)).build();
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

    @GetMapping("/plans")
    public ResponseEntity<List<com.backend.dto.SubscriptionPlanDTO>> getActivePlans() {
        return ResponseEntity.ok(subscriptionPlanService.getActivePlans());
    }

    @GetMapping("/my-overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getMySubscriptionOverview(java.security.Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }
            return ResponseEntity.ok(subscriptionService.getAdminSubscriptionOverview(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/renew")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> renewSubscription(
            @RequestBody Map<String, String> payload,
            java.security.Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }
            String planName = payload.get("planName");
            String billingCycle = payload.getOrDefault("billingCycle", "Monthly");
            String paymentMethod = payload.getOrDefault("paymentMethod", "ESEWA");
            return ResponseEntity.ok(subscriptionService.renewSubscription(principal.getName(), planName, billingCycle, paymentMethod));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/extension-request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> submitExtensionRequest(
            @Valid @RequestBody com.backend.dto.SubscriptionExtensionRequestDTO dto,
            java.security.Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }
            return ResponseEntity.ok(subscriptionService.submitExtensionRequest(principal.getName(), dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/extension-requests/my")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getMyExtensionRequests(java.security.Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }
            return ResponseEntity.ok(subscriptionService.getTenantExtensionRequests(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
