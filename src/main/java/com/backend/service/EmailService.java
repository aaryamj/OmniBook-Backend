package com.backend.service;

import com.backend.model.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

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
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendAdminInvite(String to, String token, String organizationName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Activate Your OmniBook Workspace: " + organizationName);
            
            String inviteLink = "http://localhost:5173/accept-invite?token=" + token;
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .header { background: #f8fafc; padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }" +
                "        .header h4 { margin: 0; color: #0f172a; font-size: 14px; }" +
                "        .header p { margin: 2px 0 0; color: #64748b; font-size: 12px; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        .icon { width: 64px; height: 64px; background: #eff6ff; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; color: #3b82f6; font-size: 32px; font-weight: bold; line-height: 64px; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p.message { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #0f172a; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; width: 250px; text-align: center; }" +
                "        .expiry { font-size: 10px; color: #64748b; margin-top: 12px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div>" +
                "                <h4>Secure Magic Link Invitation</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon\">&#128273;</div>" +
                "            <h3>Activate Your OmniBook Enterprise Workspace</h3>" +
                "            <p class=\"message\">Welcome to OmniBook. You have been invited to set up the root administrator account for <strong>" + organizationName + "</strong>.</p>" +
                "            <a href=\"" + inviteLink + "\" class=\"button\">Activate My Workspace</a>" +
                "            <p class=\"expiry\">This secure link expires in exactly 48 hours.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true); // true indicates html
            
            mailSender.send(message);
            log.info("Admin HTML invitation email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML invitation email to {}", to, e);
            throw new RuntimeException("Failed to send HTML invitation email", e);
        }
    }
public void sendProviderInvite(String to, String token, String organizationName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Activate Your Provider Account: " + organizationName);
            
            String inviteLink = "http://localhost:5173/accept-invite?token=" + token;
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .header { background: #f8fafc; padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }" +
                "        .header h4 { margin: 0; color: #0f172a; font-size: 14px; }" +
                "        .header p { margin: 2px 0 0; color: #64748b; font-size: 12px; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        .icon { width: 64px; height: 64px; background: #eff6ff; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; color: #3b82f6; font-size: 32px; font-weight: bold; line-height: 64px; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p.message { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #0f172a; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; width: 250px; text-align: center; }" +
                "        .expiry { font-size: 10px; color: #64748b; margin-top: 12px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div>" +
                "                <h4>Provider Network Invitation</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon\">&#128104;&#8205;&#9877;&#65039;</div>" +
                "            <h3>Join Your Medical Team</h3>" +
                "            <p class=\"message\">Welcome to OmniBook. You have been invited to join <strong>" + organizationName + "</strong> as a Service Provider. Please activate your account to view your schedule and patients.</p>" +
                "            <a href=\"" + inviteLink + "\" class=\"button\">Activate My Provider Account</a>" +
                "            <p class=\"expiry\">This secure link expires in exactly 48 hours.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true); // true indicates html
            
            mailSender.send(message);
            log.info("Provider HTML invitation email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML provider invitation email to {}", to, e);
            throw new RuntimeException("Failed to send HTML provider invitation email", e);
        }
    }
    public void sendTenantApprovalEmail(String to, String organizationName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Your OmniBook Workspace is Approved: " + organizationName);
            
            String loginLink = "http://localhost:5173/login";
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .header { background: #f8fafc; padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }" +
                "        .header h4 { margin: 0; color: #0f172a; font-size: 14px; }" +
                "        .header p { margin: 2px 0 0; color: #64748b; font-size: 12px; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        .icon { width: 64px; height: 64px; background: #f0fdf4; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; color: #22c55e; font-size: 32px; font-weight: bold; line-height: 64px; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p.message { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #0f172a; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; width: 250px; text-align: center; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div>" +
                "                <h4>Workspace Approved</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon\">&#10004;</div>" +
                "            <h3>Congratulations! Your Workspace is Active.</h3>" +
                "            <p class=\"message\">Your KYC and financial details for <strong>" + organizationName + "</strong> have been successfully verified and approved. You can now access all platform features by logging in.</p>" +
                "            <a href=\"" + loginLink + "\" class=\"button\">Go to Login</a>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Approval HTML email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML approval email to {}", to, e);
            throw new RuntimeException("Failed to send HTML approval email", e);
        }
    }
    public void sendProviderApprovalEmail(String to, String organizationName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Your Provider Account is Approved: " + organizationName);
            
            String loginLink = "http://localhost:5173/login";
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .header { background: #f8fafc; padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }" +
                "        .header h4 { margin: 0; color: #0f172a; font-size: 14px; }" +
                "        .header p { margin: 2px 0 0; color: #64748b; font-size: 12px; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        .icon { width: 64px; height: 64px; background: #f0fdf4; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; color: #22c55e; font-size: 32px; font-weight: bold; line-height: 64px; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p.message { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #0f172a; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; width: 250px; text-align: center; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div>" +
                "                <h4>Provider Account Approved</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon\">&#10004;</div>" +
                "            <h3>Congratulations! Your Account is Active.</h3>" +
                "            <p class=\"message\">Your clinical profile and services for <strong>" + organizationName + "</strong> have been successfully verified and approved by the administrator. You can now access all platform features by logging in.</p>" +
                "            <a href=\"" + loginLink + "\" class=\"button\">Go to Login</a>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Provider Approval HTML email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML provider approval email to {}", to, e);
            throw new RuntimeException("Failed to send HTML provider approval email", e);
        }
    }

    public void sendAppointmentApprovedEmail(Appointment appointment) {
        if (appointment.getPatientEmail() == null || appointment.getPatientEmail().isEmpty()) {
            log.warn("No email provided for appointment id {}", appointment.getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(appointment.getPatientEmail());
            helper.setSubject("Appointment Approved: " + appointment.getServiceName());
            
            // Generate QR code using quickchart.io (a free API for charts and QR codes)
            String qrUrl = "https://quickchart.io/qr?text=" + appointment.getTransactionId() + "&size=250&margin=2";
            
            String dateStr = appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"));
            String timeStr = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a"));

            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .header { background: #f8fafc; padding: 20px; border-bottom: 1px solid #e2e8f0; text-align: center; }" +
                "        .header h3 { margin: 0; color: #0f172a; font-size: 20px; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        .details { text-align: left; background: #f1f5f9; padding: 20px; border-radius: 8px; margin-bottom: 24px; color: #334155; }" +
                "        .details p { margin: 8px 0; font-size: 14px; }" +
                "        .qr-section { margin-top: 24px; padding-top: 24px; border-top: 1px dashed #cbd5e1; }" +
                "        .qr-code { width: 200px; height: 200px; border: 10px solid white; border-radius: 8px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); }" +
                "        .instruction { font-size: 14px; color: #64748b; margin-top: 16px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <h3>Appointment Approved</h3>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"details\">" +
                "                <p><strong>Patient:</strong> " + appointment.getPatientName() + "</p>" +
                "                <p><strong>Service:</strong> " + appointment.getServiceName() + "</p>" +
                "                <p><strong>Date:</strong> " + dateStr + "</p>" +
                "                <p><strong>Time:</strong> " + timeStr + "</p>" +
                "                <p><strong>Status:</strong> " + appointment.getAppointmentStatus() + "</p>" +
                "            </div>" +
                "            <div class=\"qr-section\">" +
                "                <p class=\"instruction\"><strong>Present this QR Code at the clinic for quick check-in.</strong></p>" +
                "                <img src=\"" + qrUrl + "\" alt=\"QR Code\" class=\"qr-code\" />" +
                "            </div>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Booking confirmation email sent to {}", appointment.getPatientEmail());
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email to {}", appointment.getPatientEmail(), e);
        }
    }

    public void sendFeedbackRequest(String to, String providerName, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("How was your visit with " + providerName + "?");
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #1a56db; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"content\">" +
                "            <h3>Thank you for your visit!</h3>" +
                "            <p>We hope you had a great experience with " + providerName + ". Please take a moment to leave a rating and review.</p>" +
                "            <a href=\"" + link + "\" class=\"button\">Leave a Review</a>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Feedback request email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send feedback request email to {}", to, e);
        }
    }

    public void sendRecallReminder(String to, String providerName, String customMessage) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Time to book your follow-up with " + providerName);
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                "        .content { padding: 32px 24px; text-align: center; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; }" +
                "        p { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 auto 24px; max-width: 400px; }" +
                "        .button { display: inline-block; background-color: #1a56db; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"content\">" +
                "            <h3>Hello! It's time for your check-up.</h3>" +
                "            <p>" + customMessage + "</p>" +
                "            <a href=\"http://localhost:5173/book\" class=\"button\">Book Appointment</a>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
            
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Recall reminder email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send recall reminder email to {}", to, e);
        }
    }
}
