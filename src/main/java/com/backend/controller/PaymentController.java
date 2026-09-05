package com.backend.controller;

import com.backend.dto.PaymentRequestDTO;
import com.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/booking")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiatePayment(@RequestBody PaymentRequestDTO request) {
        try {
            Map<String, Object> result = paymentService.initiatePayment(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/verify-esewa")
    public ResponseEntity<Void> verifyEsewa(@RequestParam(value = "data", required = false) String data) {
        if (data == null) {
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=no_data")).build();
        }
        
        try {
            String decodedData = new String(java.util.Base64.getDecoder().decode(data));
            
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
            
            if ("COMPLETE".equals(status)) {
                boolean isSuccess = paymentService.verifyEsewaPayment(transactionUuid, null, null);
                if (isSuccess) {
                    return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-success?oid=" + transactionUuid)).build();
                }
            }
            
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=verification_failed")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=parse_failed")).build();
        }
    }

    @GetMapping("/verify-stripe")
    public ResponseEntity<Void> verifyStripe(@RequestParam(value = "session_id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=no_session")).build();
        }

        try {
            String transactionId = paymentService.verifyStripePayment(sessionId);
            if (transactionId != null) {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-success?oid=" + transactionId)).build();
            } else {
                return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=verification_failed")).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=exception")).build();
        }
    }
}
