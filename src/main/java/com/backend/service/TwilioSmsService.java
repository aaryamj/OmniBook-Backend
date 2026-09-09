package com.backend.service;

import com.backend.config.TwilioConfig;
import com.backend.model.Appointment;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsService {

    private final TwilioConfig twilioConfig;

    /**
     * Normalizes a phone number into international E.164 format.
     * Automatically handles 10-digit Nepali numbers (+977) and international prefixes.
     */
    public String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            return null;
        }

        // Strip spaces, dashes, dots, parentheses
        String cleaned = rawPhone.replaceAll("[\\s\\-\\(\\)\\.]", "").trim();

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }

        // 13-digit Nepal format starting with 977 (e.g. 97798XXXXXXXX)
        if (cleaned.startsWith("977") && cleaned.length() == 13) {
            return "+" + cleaned;
        }

        // Standard 10-digit Nepali mobile starting with 98 or 97 or 96 (e.g. 9812345678)
        if (cleaned.length() == 10 && (cleaned.startsWith("98") || cleaned.startsWith("97") || cleaned.startsWith("96"))) {
            return "+977" + cleaned;
        }

        // Standard 10-digit US/Canada format (e.g. 7372508034)
        if (cleaned.length() == 10) {
            return "+1" + cleaned;
        }

        // Default prefix with '+' if only digits
        return "+" + cleaned;
    }

    /**
     * Dispatches an SMS via Twilio.
     * Falls back cleanly to simulation mode if disabled or if credentials are dummy.
     */
    public boolean sendSms(String toPhone, String messageBody) {
        String normalizedTo = normalizePhoneNumber(toPhone);
        if (normalizedTo == null) {
            log.warn("Cannot send SMS: recipient phone number is missing or invalid.");
            return false;
        }

        if (!twilioConfig.isEnabled() 
                || twilioConfig.getAccountSid() == null 
                || twilioConfig.getAccountSid().isBlank()
                || twilioConfig.getFromPhoneNumber() == null
                || twilioConfig.getFromPhoneNumber().isBlank()) {
            log.info("[SIMULATED SMS] To: {} | Sender: {} | Body: {}", 
                    normalizedTo, twilioConfig.getFromPhoneNumber(), messageBody);
            return true;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(normalizedTo),
                    new PhoneNumber(twilioConfig.getFromPhoneNumber().trim()),
                    messageBody
            ).create();

            log.info("Twilio SMS sent successfully! SID: {}, Status: {}, To: {}", 
                    message.getSid(), message.getStatus(), normalizedTo);
            return true;
        } catch (Exception e) {
            log.error("Failed to send Twilio SMS to {}: {}", normalizedTo, e.getMessage());
            return false;
        }
    }

    /**
     * Sends an appointment booking confirmation SMS to the patient.
     */
    public void sendBookingConfirmationSms(Appointment appointment) {
        if (appointment == null) return;
        String phone = appointment.getPatientPhone();
        if (phone == null || phone.isBlank()) {
            log.debug("No phone number found on appointment #{} for SMS notification.", appointment.getId());
            return;
        }

        try {
            String dateStr = appointment.getAppointmentDate() != null 
                    ? appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    : "Scheduled Date";
            String timeStr = appointment.getAppointmentTime() != null 
                    ? appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a"))
                    : "Scheduled Time";

            String ref = appointment.getTransactionId() != null 
                    ? appointment.getTransactionId() 
                    : String.valueOf(appointment.getId());

            String body = String.format(
                    "OmniBook: Hi %s, your appointment for %s is confirmed on %s at %s. Booking Ref: %s. Thank you!",
                    appointment.getPatientName() != null ? appointment.getPatientName() : "Valued Customer",
                    appointment.getServiceName() != null ? appointment.getServiceName() : "Service",
                    dateStr,
                    timeStr,
                    ref
            );

            sendSms(phone, body);
        } catch (Exception e) {
            log.error("Error creating booking confirmation SMS for appointment #{}: {}", appointment.getId(), e.getMessage());
        }
    }

    /**
     * Sends an SMS when an appointment status is updated (e.g. APPROVED, CANCELLED, COMPLETED).
     */
    public void sendAppointmentStatusSms(Appointment appointment, String status) {
        if (appointment == null) return;
        String phone = appointment.getPatientPhone();
        if (phone == null || phone.isBlank()) return;

        try {
            String dateStr = appointment.getAppointmentDate() != null 
                    ? appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    : "Scheduled Date";

            String body = String.format(
                    "OmniBook: Your appointment for %s on %s has been %s.",
                    appointment.getServiceName() != null ? appointment.getServiceName() : "Service",
                    dateStr,
                    status.toLowerCase()
            );

            sendSms(phone, body);
        } catch (Exception e) {
            log.error("Error creating appointment status SMS for appointment #{}: {}", appointment.getId(), e.getMessage());
        }
    }

    /**
     * Sends 2FA OTP verification code via SMS.
     */
    public void sendTwoFactorOtpSms(String phone, String otpCode) {
        if (phone == null || phone.isBlank() || otpCode == null || otpCode.isBlank()) return;

        String body = String.format(
                "OmniBook Security: Your login verification code is: %s. Valid for 10 minutes. Do not share this code.",
                otpCode
        );

        sendSms(phone, body);
    }
}
