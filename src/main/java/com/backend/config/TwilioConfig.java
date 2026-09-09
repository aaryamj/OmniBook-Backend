package com.backend.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@Getter
public class TwilioConfig {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.phone-number:}")
    private String fromPhoneNumber;

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void initTwilio() {
        if (enabled && accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
            try {
                Twilio.init(accountSid.trim(), authToken.trim());
                String maskedSid = accountSid.length() > 8 
                        ? accountSid.substring(0, 4) + "..." + accountSid.substring(accountSid.length() - 4) 
                        : accountSid;
                log.info("Twilio SMS Client successfully initialized with Account SID: {} and Sender Number: {}", maskedSid, fromPhoneNumber);
            } catch (Exception e) {
                log.error("Failed to initialize Twilio client: {}", e.getMessage());
            }
        } else {
            log.info("Twilio SMS is disabled (twilio.enabled={}) or credentials missing. SMS will operate in simulation mode.", enabled);
        }
    }
}
