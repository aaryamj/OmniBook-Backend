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

    @Autowired
    private com.backend.repository.AppointmentRepository appointmentRepository;

    @Autowired
    private com.backend.repository.TenantRepository tenantRepository;

    @Autowired
    private com.backend.repository.UserRepository userRepository;

    @Autowired
    private com.backend.service.CurrencyExchangeService currencyExchangeService;

    @GetMapping("/exchange-rate")
    public ResponseEntity<?> getExchangeRate() {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", currencyExchangeService.getRateDetails()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

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
        } catch (Throwable e) {
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
        } catch (Throwable e) {
            e.printStackTrace();
            return ResponseEntity.status(302).location(URI.create("http://localhost:5173/payment-failed?error=exception")).build();
        }
    }

    @GetMapping("/confirmation/{transactionId}")
    public ResponseEntity<?> getBookingConfirmation(@PathVariable String transactionId) {
        try {
            java.util.List<com.backend.model.Appointment> appointments = appointmentRepository.findByTransactionId(transactionId);
            if (appointments == null || appointments.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "No booking found for transaction ID " + transactionId));
            }

            com.backend.model.Appointment first = appointments.get(0);
            String orgName = "Organization";
            String orgType = "Clinic";
            String orgAddress = "";
            String orgLogo = "";

            if (first.getTenantId() != null) {
                java.util.Optional<com.backend.model.Tenant> tenantOpt = tenantRepository.findById(first.getTenantId());
                if (tenantOpt.isPresent()) {
                    com.backend.model.Tenant tenant = tenantOpt.get();
                    if (tenant.getOrganizationName() != null && !tenant.getOrganizationName().isBlank()) {
                        orgName = tenant.getOrganizationName();
                    }
                    if (tenant.getOrganizationType() != null && !tenant.getOrganizationType().isBlank()) {
                        orgType = tenant.getOrganizationType();
                    }
                    orgAddress = tenant.getAddress() != null ? tenant.getAddress() : "";
                    orgLogo = tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "";
                }
            }

            String providerName = "";
            if (first.getProviderId() != null) {
                java.util.Optional<com.backend.model.User> providerOpt = userRepository.findById(first.getProviderId());
                if (providerOpt.isPresent()) {
                    providerName = providerOpt.get().getFullName();
                }
            }

            java.util.List<Map<String, Object>> slotList = appointments.stream().map(a -> {
                Map<String, Object> sm = new java.util.HashMap<>();
                sm.put("id", a.getId());
                sm.put("date", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "");
                sm.put("time", a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "");
                sm.put("serviceName", a.getServiceName());
                sm.put("price", a.getPrice());
                return sm;
            }).collect(java.util.stream.Collectors.toList());

            double totalAmount = appointments.stream().mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0).sum();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("transactionId", transactionId);
            result.put("organizationName", orgName);
            result.put("organizationType", orgType);
            result.put("address", orgAddress);
            result.put("logoUrl", orgLogo);
            result.put("serviceName", first.getServiceName());
            result.put("providerName", providerName);
            result.put("patientName", first.getPatientName());
            result.put("patientEmail", first.getPatientEmail());
            result.put("appointmentDate", first.getAppointmentDate() != null ? first.getAppointmentDate().toString() : "");
            result.put("appointmentTime", first.getAppointmentTime() != null ? first.getAppointmentTime().toString() : "");
            result.put("appointmentType", first.getAppointmentType());
            result.put("meetingLink", first.getMeetingLink());
            result.put("paymentStatus", first.getPaymentStatus());
            result.put("paymentMethod", first.getPaymentMethod());
            result.put("totalAmount", totalAmount > 0 ? totalAmount : (first.getPrice() != null ? first.getPrice() : 0.0));
            result.put("baseCurrency", first.getBaseCurrency() != null ? first.getBaseCurrency() : "NPR");
            result.put("basePriceNpr", first.getBasePriceNpr() != null ? first.getBasePriceNpr() : first.getPrice());
            result.put("chargedCurrency", first.getChargedCurrency() != null ? first.getChargedCurrency() : ("STRIPE".equalsIgnoreCase(first.getPaymentMethod()) ? "USD" : "NPR"));
            result.put("chargedAmount", first.getChargedAmount() != null ? first.getChargedAmount() : totalAmount);
            result.put("exchangeRate", first.getExchangeRate() != null ? first.getExchangeRate() : ("STRIPE".equalsIgnoreCase(first.getPaymentMethod()) ? 135.0 : 1.0));
            result.put("conversionTimestamp", first.getConversionTimestamp() != null ? first.getConversionTimestamp().toString() : null);
            result.put("slots", slotList);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error fetching confirmation: " + e.getMessage()));
        }
    }
}
