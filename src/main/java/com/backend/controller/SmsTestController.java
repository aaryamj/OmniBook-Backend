package com.backend.controller;

import com.backend.config.TwilioConfig;
import com.backend.service.TwilioSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/sms", "/api/v1/sms", "/api/v1/public/sms"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SmsTestController {

    private final TwilioSmsService twilioSmsService;
    private final TwilioConfig twilioConfig;

    @GetMapping("/config-status")
    public ResponseEntity<?> getConfigStatus() {
        String sid = twilioConfig.getAccountSid();
        String maskedSid = (sid != null && sid.length() > 8)
                ? sid.substring(0, 4) + "..." + sid.substring(sid.length() - 4)
                : (sid != null ? "configured" : "none");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", twilioConfig.isEnabled(),
                "fromPhoneNumber", twilioConfig.getFromPhoneNumber() != null ? twilioConfig.getFromPhoneNumber() : "not_set",
                "accountSid", maskedSid
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestSms(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String message = request.get("message");

        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Phone number is required."
            ));
        }

        String messageBody = (message != null && !message.trim().isEmpty())
                ? message
                : "OmniBook Test SMS: Twilio integration is active and working successfully!";

        String normalized = twilioSmsService.normalizePhoneNumber(phone);
        boolean sent = twilioSmsService.sendSms(phone, messageBody);

        return ResponseEntity.ok(Map.of(
                "success", sent,
                "targetPhone", phone,
                "normalizedPhone", normalized != null ? normalized : "invalid",
                "message", sent ? "SMS dispatched successfully." : "Failed to dispatch SMS. Check server logs for details."
        ));
    }
}
