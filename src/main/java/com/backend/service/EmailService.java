package com.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Verify your account");
            message.setText("Your verification code is: " + code + "\n\nPlease use this code to verify your account.");
            
            mailSender.send(message);
            log.info("Verification email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", to, e);
            throw new RuntimeException("Failed to send email");
        }
    }
}
