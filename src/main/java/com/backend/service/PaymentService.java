package com.backend.service;

import com.backend.dto.PaymentRequestDTO;
import com.backend.model.Appointment;
import com.backend.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

@Service
public class PaymentService {

    @org.springframework.beans.factory.annotation.Value("${stripe.api.key}")
    private String stripeApiKey;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private PublicBookingService publicBookingService;

    @Transactional
    public Map<String, Object> initiatePayment(PaymentRequestDTO request) {
        String transactionId = UUID.randomUUID().toString().replace("-", "");
        
        // For each selected slot, create an appointment record
        for (String slotId : request.getSelectedSlots()) {
            String dateStr = LocalDate.now().toString(); // Default to today
            String[] parts = slotId.split("-");
            if (parts.length >= 4) {
                dateStr = parts[1] + "-" + parts[2] + "-" + parts[3]; // YYYY-MM-DD
            }
            
            // Re-fetch slots to find the correct time for this slot ID
            Map<String, Object> slotsResult = publicBookingService.getProviderSlots(request.getProviderId(), dateStr, request.getServiceName());
            java.util.List<com.backend.dto.TimeSlotDTO> dynamicSlots = (java.util.List<com.backend.dto.TimeSlotDTO>) slotsResult.get("slots");
            
            LocalTime appointmentTime = LocalTime.NOON; // fallback
            String priceStr = "1500";
            if (dynamicSlots != null) {
                for (com.backend.dto.TimeSlotDTO s : dynamicSlots) {
                    if (s.getId().equals(slotId)) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH);
                        try {
                            appointmentTime = LocalTime.parse(s.getTime(), formatter);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        priceStr = s.getPrice().replaceAll("[^\\d]", "");
                        break;
                    }
                }
            }
            
            // If fallback is still NOON, attempt to extract time from the slot ID (e.g. if coming from AI slots)
            if (appointmentTime.equals(LocalTime.NOON) && parts.length >= 5) {
                try {
                    appointmentTime = LocalTime.parse(parts[4]); // Expects "HH:mm"
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            
            Double price = 1500.0;
            try {
                price = Double.parseDouble(priceStr);
            } catch (Exception e) {
                e.printStackTrace();
            }

            String aptType = request.getAppointmentType() != null ? request.getAppointmentType() : "IN_PERSON";
            String meetingLink = null;
            if ("VIRTUAL".equalsIgnoreCase(aptType) || (request.getServiceName() != null && request.getServiceName().toLowerCase().contains("telemedicine"))) {
                aptType = "VIRTUAL";
                meetingLink = "https://meet.jit.si/OmniBook-" + UUID.randomUUID().toString();
            }

            Appointment appointment = Appointment.builder()
                    .tenantId(request.getTenantId())
                    .providerId(request.getProviderId())
                    .patientName(request.getPatientName())
                    .patientPhone(request.getPatientPhone())
                    .patientEmail(request.getPatientEmail())
                    .reasonForVisit(request.getReasonForVisit())
                    .serviceName(request.getServiceName())
                    .appointmentDate(LocalDate.parse(dateStr))
                    .appointmentTime(appointmentTime)
                    .price(price)
                    .appointmentType(aptType)
                    .meetingLink(meetingLink)
                    .paymentStatus("PENDING")
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "ESEWA")
                    .transactionId(transactionId)
                    .build();
                    
            appointmentRepository.save(appointment);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        if ("STRIPE".equalsIgnoreCase(request.getPaymentMethod())) {
            try {
                Stripe.apiKey = stripeApiKey;

                String successUrl = "http://localhost:8080/api/v1/public/booking/verify-stripe?session_id={CHECKOUT_SESSION_ID}";
                String cancelUrl = "http://localhost:5173/payment-failed?error=cancelled";

                SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setClientReferenceId(transactionId)
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd") // Convert NPR to USD if needed, assuming USD for stripe test
                                        .setUnitAmount((long) (request.getTotalAmount() * 100))
                                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(request.getServiceName() != null ? request.getServiceName() : "Appointment Booking")
                                                .build())
                                        .build())
                                .build())
                        .build();

                Session session = Session.create(params);

                response.put("gatewayUrl", session.getUrl());
            } catch (Exception e) {
                throw new RuntimeException("Failed to initiate Stripe payment", e);
            }
        } else {
            Map<String, Object> esewaParams = new HashMap<>();
            String amount = String.valueOf(request.getTotalAmount());
            if (amount.endsWith(".0")) {
                amount = amount.substring(0, amount.length() - 2);
            }
            String productCode = "EPAYTEST";
            
            esewaParams.put("amount", amount);
            esewaParams.put("tax_amount", "0");
            esewaParams.put("total_amount", amount);
            esewaParams.put("transaction_uuid", transactionId);
            esewaParams.put("product_code", productCode);
            esewaParams.put("product_service_charge", "0");
            esewaParams.put("product_delivery_charge", "0");
            esewaParams.put("success_url", "http://localhost:8080/api/v1/public/booking/verify-esewa");
            esewaParams.put("failure_url", "http://localhost:5173/payment-failed");
            esewaParams.put("signed_field_names", "total_amount,transaction_uuid,product_code");
            
            // Generate HMAC SHA256 Signature
            try {
                String secretKey = "8gBm/:&EnhH.1/q";
                String message = "total_amount=" + amount + ",transaction_uuid=" + transactionId + ",product_code=" + productCode;
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hash = mac.doFinal(message.getBytes("UTF-8"));
                String signature = Base64.getEncoder().encodeToString(hash);
                esewaParams.put("signature", signature);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate payment signature", e);
            }

            response.put("gatewayUrl", "https://rc-epay.esewa.com.np/api/epay/main/v2/form");
            response.put("formData", esewaParams);
        }

        return response;
    }

    @Transactional
    public boolean verifyEsewaPayment(String oid, String amt, String refId) {
        // In a real scenario, we would make a server-to-server call to eSewa to verify the refId.
        // For this test environment, we just mark it as SUCCESS.
        // Verify payment and update all appointments with this transaction ID
        java.util.List<Appointment> appointments = appointmentRepository.findAll().stream()
                .filter(a -> oid.equals(a.getTransactionId()))
                .collect(java.util.stream.Collectors.toList());
                
        if (!appointments.isEmpty()) {
            for (Appointment appointment : appointments) {
                appointment.setPaymentStatus("SUCCESS");
                appointmentRepository.save(appointment);
            }
            
            try {
                // Create a notification for the provider
                Appointment firstApp = appointments.get(0);
                com.backend.model.Notification notification = com.backend.model.Notification.builder()
                        .userId(firstApp.getProviderId())
                        .title("Booking Requested")
                        .message("New booking request from " + firstApp.getPatientName() + " for " + firstApp.getServiceName())
                        .type("BOOKING_REQUEST")
                        .isRead(false)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                notificationRepository.save(notification);
                
                emailService.sendAppointmentApprovedEmail(firstApp);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    @Transactional
    public String verifyStripePayment(String sessionId) {
        try {
            Stripe.apiKey = stripeApiKey;
            Session session = Session.retrieve(sessionId);

            if ("paid".equals(session.getPaymentStatus())) {
                String transactionId = session.getClientReferenceId();
                java.util.List<Appointment> appointments = appointmentRepository.findAll().stream()
                        .filter(a -> transactionId.equals(a.getTransactionId()))
                        .collect(java.util.stream.Collectors.toList());
                
                if (appointments != null && !appointments.isEmpty()) {
                    for (Appointment a : appointments) {
                        a.setPaymentStatus("SUCCESS");
                        appointmentRepository.save(a);
                    }
                    
                    try {
                        Appointment firstApp = appointments.get(0);
                        com.backend.model.Notification notification = com.backend.model.Notification.builder()
                                .userId(firstApp.getProviderId())
                                .title("Booking Requested (Stripe)")
                                .message("New booking request from " + firstApp.getPatientName() + " for " + firstApp.getServiceName())
                                .type("BOOKING_REQUEST")
                                .isRead(false)
                                .createdAt(java.time.LocalDateTime.now())
                                .build();
                        notificationRepository.save(notification);
                        
                        emailService.sendAppointmentApprovedEmail(firstApp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    return transactionId;
                } else {
                    System.out.println("No appointments found for transaction: " + transactionId);
                }
            } else {
                System.out.println("Stripe verification failed for session: " + sessionId + ", status: " + session.getPaymentStatus());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
