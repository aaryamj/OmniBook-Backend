package com.backend.service;

import com.backend.model.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
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

    public void sendAdminInvite(String to, String token, String organizationName, String organizationType) {
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
                "            <p class=\"message\">Welcome to OmniBook. You have been invited to set up the root administrator account for your <strong>" + organizationType + "</strong>, <strong>" + organizationName + "</strong>.</p>" +
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
    public void sendProviderInvite(String to, String token, String organizationName, String organizationType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Activate Your Provider Account: " + organizationName);
            
            String inviteLink = "http://localhost:5173/accept-invite?token=" + token;

            String orgTypeNormalized = (organizationType != null) ? organizationType.trim().toLowerCase() : "clinic";
            String teamTitle;
            String targetAudience;
            String iconEmoji;
            String roleTitle;

            if (orgTypeNormalized.contains("college") || orgTypeNormalized.contains("school") || orgTypeNormalized.contains("university") || orgTypeNormalized.contains("education")) {
                teamTitle = "Join Your Academic Team";
                targetAudience = "students";
                iconEmoji = "&#127891;"; // Graduation Cap
                roleTitle = "Instructor / Faculty Member";
            } else if (orgTypeNormalized.contains("saloon") || orgTypeNormalized.contains("salon") || orgTypeNormalized.contains("spa") || orgTypeNormalized.contains("beauty")) {
                teamTitle = "Join Your Styling Team";
                targetAudience = "clients";
                iconEmoji = "&#9986;&#65039;"; // Scissors
                roleTitle = "Stylist / Specialist";
            } else if (orgTypeNormalized.contains("clinic") || orgTypeNormalized.contains("hospital") || orgTypeNormalized.contains("health") || orgTypeNormalized.contains("medical")) {
                teamTitle = "Join Your Medical Team";
                targetAudience = "patients";
                iconEmoji = "&#128104;&#8205;&#9877;&#65039;"; // Doctor
                roleTitle = "Healthcare Provider";
            } else {
                teamTitle = "Join Your Professional Team";
                targetAudience = "clients";
                iconEmoji = "&#128188;"; // Briefcase
                roleTitle = "Service Provider";
            }
            
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
                "                <h4>" + (organizationName != null ? organizationName : "Provider") + " Network Invitation</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon\">" + iconEmoji + "</div>" +
                "            <h3>" + teamTitle + "</h3>" +
                "            <p class=\"message\">Welcome to OmniBook. You have been invited to join your <strong>" + organizationType + "</strong>, <strong>" + organizationName + "</strong> as a " + roleTitle + ". Please activate your account to view your schedule and " + targetAudience + ".</p>" +
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
    public void sendTenantApprovalEmail(String to, String organizationName, String organizationType) {
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
                "            <p class=\"message\">Your KYC and financial details for your <strong>" + organizationType + "</strong>, <strong>" + organizationName + "</strong> have been successfully verified and approved. You can now access all platform features by logging in.</p>" +
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
    public void sendProviderApprovalEmail(String to, String organizationName, String organizationType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Your Provider Account is Approved: " + organizationName);
            
            String loginLink = "http://localhost:5173/login";

            String orgTypeNormalized = (organizationType != null) ? organizationType.trim().toLowerCase() : "clinic";
            String profileDescription;
            if (orgTypeNormalized.contains("college") || orgTypeNormalized.contains("school") || orgTypeNormalized.contains("university") || orgTypeNormalized.contains("education")) {
                profileDescription = "instructor profile and courses";
            } else if (orgTypeNormalized.contains("saloon") || orgTypeNormalized.contains("salon") || orgTypeNormalized.contains("spa") || orgTypeNormalized.contains("beauty")) {
                profileDescription = "stylist profile and services";
            } else if (orgTypeNormalized.contains("clinic") || orgTypeNormalized.contains("hospital") || orgTypeNormalized.contains("health") || orgTypeNormalized.contains("medical")) {
                profileDescription = "clinical profile and services";
            } else {
                profileDescription = "professional profile and services";
            }
            
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
                "            <p class=\"message\">Your " + profileDescription + " for your <strong>" + organizationType + "</strong>, <strong>" + organizationName + "</strong> have been successfully verified and approved by the administrator. You can now access all platform features by logging in.</p>" +
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

    public void sendAppointmentCancelledEmail(Appointment appointment) {
        if (appointment.getPatientEmail() == null || appointment.getPatientEmail().isEmpty()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(appointment.getPatientEmail());
            helper.setSubject("Appointment Cancelled: " + appointment.getServiceName());

            String dateStr = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")) : "N/A";
            String timeStr = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "N/A";
            String refundInfo = "";
            if (appointment.getRefundEligibilityPercentage() != null && appointment.getRefundEligibilityPercentage() > 0) {
                refundInfo = "<p><strong>Refund Eligibility:</strong> " + appointment.getRefundEligibilityPercentage() + "% (" + appointment.getRefundCurrency() + " " + appointment.getRefundAmount() + ")</p>" +
                             "<p><strong>Refund Status:</strong> " + appointment.getRefundStatus() + "</p>";
            } else {
                refundInfo = "<p><strong>Refund Status:</strong> Not eligible for refund per cancellation policy.</p>";
            }

            String htmlContent = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                ".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                ".header { background: #fee2e2; padding: 20px; border-bottom: 1px solid #fecaca; text-align: center; }" +
                ".header h3 { margin: 0; color: #991b1b; font-size: 20px; }" +
                ".content { padding: 32px 24px; }" +
                ".details { background: #f8fafc; padding: 20px; border-radius: 8px; margin-bottom: 24px; color: #334155; }" +
                ".details p { margin: 8px 0; font-size: 14px; }" +
                "</style></head><body><div class=\"container\">" +
                "<div class=\"header\"><h3>Appointment Cancelled</h3></div>" +
                "<div class=\"content\"><div class=\"details\">" +
                "<p><strong>Service:</strong> " + appointment.getServiceName() + "</p>" +
                "<p><strong>Date & Time:</strong> " + dateStr + " at " + timeStr + "</p>" +
                "<p><strong>Cancellation Reason:</strong> " + (appointment.getCancellationReason() != null ? appointment.getCancellationReason() : "User Requested") + "</p>" +
                refundInfo +
                "</div><p style=\"color:#64748b;font-size:14px;\">If you have any questions, please contact our support team.</p></div></div></body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Cancellation email sent to {}", appointment.getPatientEmail());
        } catch (Exception e) {
            log.error("Failed to send cancellation email to {}", appointment.getPatientEmail(), e);
        }
    }

    public void sendAppointmentRejectedRefundEmail(Appointment appointment, String rejectionReason, double refundAmount, String refundCurrency) {
        if (appointment.getPatientEmail() == null || appointment.getPatientEmail().isEmpty()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(appointment.getPatientEmail());
            helper.setSubject("Booking Request Declined - 100% Refund Processed: " + appointment.getServiceName());

            String dateStr = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")) : "N/A";
            String timeStr = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "N/A";
            String providerName = appointment.getDoctorName() != null ? appointment.getDoctorName() : "Service Provider";
            String curr = (refundCurrency != null && !refundCurrency.isBlank()) ? refundCurrency : "NPR";

            String refundSection;
            if (refundAmount > 0) {
                refundSection = "<div style=\"background: #ecfdf5; border: 1px solid #a7f3d0; padding: 14px; border-radius: 8px; margin-top: 14px;\">" +
                        "<p style=\"margin: 0; color: #065f46; font-weight: bold;\">✓ 100% Full Refund Issued</p>" +
                        "<p style=\"margin: 4px 0 0 0; color: #047857; font-size: 13px;\">Amount: <strong>" + curr + " " + String.format("%.2f", refundAmount) + "</strong></p>" +
                        "<p style=\"margin: 4px 0 0 0; color: #047857; font-size: 13px;\">Payment Method: " + (appointment.getPaymentMethod() != null ? appointment.getPaymentMethod() : "Online Gateway") + "</p>" +
                        (appointment.getRefundTransactionId() != null ? ("<p style=\"margin: 4px 0 0 0; color: #047857; font-size: 12px;\">Refund Ref: " + appointment.getRefundTransactionId() + "</p>") : "") +
                        "</div>";
            } else {
                refundSection = "<p style=\"color: #64748b; font-size: 13px; margin-top: 12px;\">Payment Status: No charge was captured for this booking.</p>";
            }

            String htmlContent = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                ".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                ".header { background: #fee2e2; padding: 20px; border-bottom: 1px solid #fecaca; text-align: center; }" +
                ".header h3 { margin: 0; color: #991b1b; font-size: 20px; }" +
                ".content { padding: 32px 24px; }" +
                ".details { background: #f8fafc; padding: 20px; border-radius: 8px; margin-bottom: 24px; color: #334155; }" +
                ".details p { margin: 8px 0; font-size: 14px; }" +
                "</style></head><body><div class=\"container\">" +
                "<div class=\"header\"><h3>Booking Request Declined</h3></div>" +
                "<div class=\"content\"><div class=\"details\">" +
                "<p><strong>Service:</strong> " + appointment.getServiceName() + "</p>" +
                "<p><strong>Provider:</strong> " + providerName + "</p>" +
                "<p><strong>Requested Schedule:</strong> " + dateStr + " at " + timeStr + "</p>" +
                "<p><strong>Reason:</strong> " + ((rejectionReason != null && !rejectionReason.isBlank()) ? rejectionReason : "Provider unavailable during this slot") + "</p>" +
                refundSection +
                "</div><p style=\"color:#64748b;font-size:14px;\">We apologize for the inconvenience. You may book another slot or choose another provider anytime on OmniBook.</p></div></div></body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Booking request rejection refund email sent to {}", appointment.getPatientEmail());
        } catch (Exception e) {
            log.error("Failed to send rejection refund email to {}", appointment.getPatientEmail(), e);
        }
    }

    public void sendAppointmentRescheduledEmail(Appointment appointment, LocalDate oldDate, LocalTime oldTime) {
        if (appointment.getPatientEmail() == null || appointment.getPatientEmail().isEmpty()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(appointment.getPatientEmail());
            helper.setSubject("Appointment Rescheduled: " + appointment.getServiceName());

            String oldDateStr = oldDate != null ? oldDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")) : "N/A";
            String oldTimeStr = oldTime != null ? oldTime.format(DateTimeFormatter.ofPattern("hh:mm a")) : "N/A";
            String newDateStr = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")) : "N/A";
            String newTimeStr = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "N/A";

            String htmlContent = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 20px; margin: 0; }" +
                ".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; }" +
                ".header { background: #e0f2fe; padding: 20px; border-bottom: 1px solid #bae6fd; text-align: center; }" +
                ".header h3 { margin: 0; color: #0369a1; font-size: 20px; }" +
                ".content { padding: 32px 24px; }" +
                ".details { background: #f8fafc; padding: 20px; border-radius: 8px; margin-bottom: 24px; color: #334155; }" +
                ".details p { margin: 8px 0; font-size: 14px; }" +
                "</style></head><body><div class=\"container\">" +
                "<div class=\"header\"><h3>Appointment Rescheduled</h3></div>" +
                "<div class=\"content\"><div class=\"details\">" +
                "<p><strong>Service:</strong> " + appointment.getServiceName() + "</p>" +
                "<p><strong>Previous Slot:</strong> <span style=\"text-decoration: line-through; color: #94a3b8;\">" + oldDateStr + " at " + oldTimeStr + "</span></p>" +
                "<p><strong>New Slot:</strong> <strong style=\"color: #0284c7;\">" + newDateStr + " at " + newTimeStr + "</strong></p>" +
                "<p><strong>Reschedule Count:</strong> " + appointment.getRescheduleCount() + "</p>" +
                "</div><p style=\"color:#64748b;font-size:14px;\">Your appointment has been successfully updated. We look forward to seeing you!</p></div></div></body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Rescheduled email sent to {}", appointment.getPatientEmail());
        } catch (Exception e) {
            log.error("Failed to send rescheduled email to {}", appointment.getPatientEmail(), e);
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

    public void sendPasswordResetEmail(String to, String token, String organizationName, String organizationType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            String subjectOrg = (organizationName != null && !organizationName.isBlank()) ? organizationName : "OmniBook";
            helper.setSubject("Reset Your Password - " + subjectOrg);
            
            String resetLink = "http://localhost:5173/reset-password?token=" + token;
            String orgDisplay = (organizationName != null && !organizationName.isBlank()) ? organizationName : "OmniBook Central Platform";
            String typeDisplay = (organizationType != null && !organizationType.isBlank()) ? organizationType : "Account";
            
            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; padding: 24px; margin: 0; }" +
                "        .container { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }" +
                "        .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 24px; text-align: center; color: #ffffff; }" +
                "        .badge { display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.15); border-radius: 20px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; font-weight: 600; margin-bottom: 8px; }" +
                "        .header h2 { margin: 0; font-size: 20px; font-weight: 700; color: #ffffff; }" +
                "        .content { padding: 32px 28px; text-align: center; }" +
                "        .icon-circle { width: 56px; height: 56px; background: #fee2e2; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px; color: #dc2626; font-size: 24px; line-height: 56px; }" +
                "        h3 { color: #0f172a; font-size: 18px; margin: 0 0 12px; font-weight: 600; }" +
                "        p.desc { color: #475569; font-size: 14px; line-height: 1.6; margin: 0 auto 24px; max-width: 440px; }" +
                "        .button { display: inline-block; background-color: #2563eb; color: #ffffff !important; padding: 13px 28px; border-radius: 10px; text-decoration: none; font-weight: 600; font-size: 14px; box-shadow: 0 4px 6px -1px rgba(37, 99, 235, 0.2); }" +
                "        .notice { font-size: 12px; color: #64748b; background: #f8fafc; border-radius: 8px; padding: 12px; margin: 20px 0; text-align: left; border-left: 3px solid #3b82f6; }" +
                "        .footer { border-top: 1px solid #e2e8f0; padding: 16px 24px; font-size: 11px; color: #94a3b8; text-align: center; background: #f8fafc; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div class=\"badge\">" + typeDisplay + " Security</div>" +
                "            <h2>" + orgDisplay + "</h2>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"icon-circle\">&#128274;</div>" +
                "            <h3>Password Reset Request</h3>" +
                "            <p class=\"desc\">We received a request to reset your password for your account associated with <strong>" + to + "</strong>. Click the button below to choose a new password.</p>" +
                "            <div style=\"margin: 28px 0;\">" +
                "                <a href=\"" + resetLink + "\" class=\"button\">Reset Password</a>" +
                "            </div>" +
                "            <div class=\"notice\">" +
                "                <strong>Security Note:</strong> This password reset link is valid for <strong>2 hours</strong>. If you did not request a password reset, please ignore this email or contact your administrator immediately." +
                "            </div>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            &copy; " + java.time.Year.now().getValue() + " OmniBook Platform. All rights reserved." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
                
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", to, e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }

    public void sendSystemAnnouncementEmail(String to, String title, String messageBody, String priority) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("[OmniBook Alert] " + title);

            String priorityBadge = "NORMAL";
            String badgeBg = "#3b82f6";
            if ("Urgent".equalsIgnoreCase(priority) || "Critical".equalsIgnoreCase(priority)) {
                priorityBadge = "CRITICAL PRIORITY";
                badgeBg = "#ef4444";
            } else if ("Low".equalsIgnoreCase(priority)) {
                priorityBadge = "INFO NOTICE";
                badgeBg = "#64748b";
            }

            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; padding: 24px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }" +
                "        .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 28px 24px; text-align: center; color: #ffffff; }" +
                "        .badge { display: inline-block; padding: 4px 12px; background: " + badgeBg + "; color: #ffffff; border-radius: 20px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; margin-bottom: 10px; }" +
                "        .header h1 { margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.02em; color: #ffffff; }" +
                "        .content { padding: 36px 32px; }" +
                "        .announcement-card { background: #f8fafc; border: 1px solid #e2e8f0; border-left: 4px solid " + badgeBg + "; border-radius: 12px; padding: 20px; margin-bottom: 24px; }" +
                "        .announcement-title { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0; }" +
                "        .announcement-body { color: #334155; font-size: 14px; line-height: 1.7; white-space: pre-wrap; margin: 0; }" +
                "        .footer { border-top: 1px solid #e2e8f0; padding: 20px 24px; font-size: 11px; color: #94a3b8; text-align: center; background: #f8fafc; line-height: 1.5; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div class=\"badge\">" + priorityBadge + "</div>" +
                "            <h1>OmniBook Enterprise Broadcast</h1>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"announcement-card\">" +
                "                <div class=\"announcement-title\">" + title + "</div>" +
                "                <div class=\"announcement-body\">" + messageBody + "</div>" +
                "            </div>" +
                "            <p style=\"font-size: 12px; color: #64748b; line-height: 1.5; margin: 0;\">" +
                "                This message is an official system announcement dispatched to all registered tenants, administrators, service providers, and subscribers across the OmniBook platform." +
                "            </p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            &copy; " + java.time.Year.now().getValue() + " OmniBook Enterprise Platform. All rights reserved.<br/>" +
                "            You received this notification because your account or subscription is registered on the OmniBook platform." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("System announcement email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send system announcement email to {}: {}", to, e.getMessage());
        }
    }

    public void sendTicketStatusUpdateEmail(String to, String ticketNumber, String organizationName, 
                                           String newStatus, String issueType, String subject, String originalMessage) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("[Support Update] " + ticketNumber + " - Status: " + newStatus.toUpperCase());

            String statusColor = "#2563eb";
            String statusBg = "#eff6ff";
            String statusMessage = "Your support escalation status has been updated to <strong>" + newStatus + "</strong>.";

            if ("Resolved".equalsIgnoreCase(newStatus)) {
                statusColor = "#059669";
                statusBg = "#ecfdf5";
                statusMessage = "Great news! Your support ticket has been reviewed and marked as <strong>Resolved</strong> by our enterprise engineering team. Please review the service to confirm all systems are operating as expected.";
            } else if ("In Progress".equalsIgnoreCase(newStatus)) {
                statusColor = "#2563eb";
                statusBg = "#eff6ff";
                statusMessage = "Our tier-3 technical support engineers have picked up your escalation and are <strong>actively investigating and resolving</strong> this issue for your workspace.";
            } else if ("Urgent".equalsIgnoreCase(newStatus) || "Critical".equalsIgnoreCase(newStatus)) {
                statusColor = "#dc2626";
                statusBg = "#fef2f2";
                statusMessage = "Your ticket has been prioritized to <strong>Critical / Urgent</strong>. On-call engineering teams and senior diagnostic specialists have been alerted.";
            } else if ("Closed".equalsIgnoreCase(newStatus)) {
                statusColor = "#475569";
                statusBg = "#f1f5f9";
                statusMessage = "This support escalation has been marked as <strong>Closed</strong>. If you require further assistance or if the issue reoccurs, please submit a new ticket or contact your account lead.";
            }

            String orgNameSafe = (organizationName != null && !organizationName.isBlank()) ? organizationName : "OmniBook Workspace";
            String issueTypeSafe = (issueType != null && !issueType.isBlank()) ? issueType : "Technical Escalation";
            String subjectSafe = (subject != null && !subject.isBlank()) ? subject : "Support Inquiry";
            String messageSafe = (originalMessage != null && !originalMessage.isBlank()) ? originalMessage : "No description provided.";

            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; padding: 24px; margin: 0; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }" +
                "        .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 26px 24px; color: #ffffff; display: flex; align-items: center; justify-content: space-between; }" +
                "        .ticket-id { font-family: monospace; font-size: 14px; font-weight: 700; color: #93c5fd; background: rgba(255,255,255,0.1); padding: 4px 10px; border-radius: 6px; display: inline-block; }" +
                "        .org-name { font-size: 18px; font-weight: 800; color: #ffffff; margin-top: 6px; }" +
                "        .content { padding: 32px 28px; }" +
                "        .status-banner { background: " + statusBg + "; border: 1px solid " + statusColor + "40; border-left: 5px solid " + statusColor + "; border-radius: 12px; padding: 18px 20px; margin-bottom: 24px; }" +
                "        .status-badge { display: inline-block; padding: 3px 10px; background: " + statusColor + "; color: #ffffff; border-radius: 20px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; font-weight: 700; margin-bottom: 8px; }" +
                "        .status-desc { color: #1e293b; font-size: 14px; line-height: 1.6; margin: 0; }" +
                "        .details-table { width: 100%; border-collapse: collapse; margin-bottom: 24px; font-size: 13px; }" +
                "        .details-table td { padding: 10px 12px; border-bottom: 1px solid #f1f5f9; vertical-align: top; }" +
                "        .details-table td.label { color: #64748b; font-weight: 600; width: 35%; }" +
                "        .details-table td.value { color: #0f172a; font-weight: 500; }" +
                "        .message-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 16px; font-size: 13px; color: #334155; line-height: 1.6; margin-bottom: 24px; }" +
                "        .footer { border-top: 1px solid #e2e8f0; padding: 18px 24px; font-size: 11px; color: #94a3b8; text-align: center; background: #f8fafc; line-height: 1.5; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div>" +
                "                <span class=\"ticket-id\">" + ticketNumber + "</span>" +
                "                <div class=\"org-name\">" + orgNameSafe + "</div>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"status-banner\">" +
                "                <div class=\"status-badge\">STATUS: " + newStatus.toUpperCase() + "</div>" +
                "                <p class=\"status-desc\">" + statusMessage + "</p>" +
                "            </div>" +
                "            <table class=\"details-table\">" +
                "                <tr><td class=\"label\">Subject</td><td class=\"value\">" + subjectSafe + "</td></tr>" +
                "                <tr><td class=\"label\">Issue Type</td><td class=\"value\">" + issueTypeSafe + "</td></tr>" +
                "                <tr><td class=\"label\">Account Email</td><td class=\"value\">" + to + "</td></tr>" +
                "            </table>" +
                "            <div style=\"font-size: 12px; font-weight: 700; color: #475569; text-transform: uppercase; margin-bottom: 6px;\">Original Inquiry Excerpt</div>" +
                "            <div class=\"message-box\">" + messageSafe + "</div>" +
                "            <p style=\"font-size: 12px; color: #64748b; line-height: 1.5; margin: 0;\">" +
                "                Need further details? Simply reply directly to this email with your inquiry or contact the OmniBook tier-3 support hotline." +
                "            </p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            &copy; " + java.time.Year.now().getValue() + " OmniBook Enterprise Support System. All rights reserved.<br/>" +
                "            This is an automated notification concerning your enterprise support ticket." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Ticket status update email sent to {} for ticket {}", to, ticketNumber);
        } catch (Exception e) {
            log.error("Failed to send ticket status update email to {}: {}", to, e.getMessage());
        }
    }

    public void sendSubscriptionVerificationUpdateEmail(
            String to,
            String adminFullName,
            String organizationName,
            String organizationType,
            String planTier,
            String billingCycle,
            String orderNumber,
            String invoiceNumber,
            String paymentMethod) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("OmniBook Update: Payment Received & Verification in Progress (" + organizationName + ")");

            String adminNameSafe = (adminFullName != null && !adminFullName.isBlank()) ? adminFullName : "Administrator";
            String orgNameSafe = (organizationName != null && !organizationName.isBlank()) ? organizationName : "Your Organization";
            String orgTypeSafe = (organizationType != null && !organizationType.isBlank()) ? organizationType : "Organization";
            String planTierSafe = (planTier != null && !planTier.isBlank()) ? planTier : "Enterprise";
            String billingCycleSafe = (billingCycle != null && !billingCycle.isBlank()) ? billingCycle : "Monthly";
            String orderNumberSafe = (orderNumber != null && !orderNumber.isBlank()) ? orderNumber : "ORD-2026-N/A";
            String invoiceNumberSafe = (invoiceNumber != null && !invoiceNumber.isBlank()) ? invoiceNumber : "INV-2026-N/A";
            String paymentMethodSafe = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "Online Gateway";

            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; padding: 24px; margin: 0; color: #1e293b; }" +
                "        .wrapper { max-width: 620px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05); }" +
                "        .header { background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 60%, #1d4ed8 100%); padding: 32px 28px; text-align: left; color: #ffffff; }" +
                "        .brand-badge { display: inline-block; background: rgba(255, 255, 255, 0.15); backdrop-filter: blur(8px); padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 12px; border: 1px solid rgba(255,255,255,0.2); }" +
                "        .header h1 { margin: 0; font-size: 22px; font-weight: 800; line-height: 1.3; }" +
                "        .header p { margin: 6px 0 0; font-size: 13px; color: #cbd5e1; }" +
                "        .body { padding: 32px 28px; }" +
                "        .greeting { font-size: 16px; font-weight: 700; color: #0f172a; margin-bottom: 16px; }" +
                "        .lead-text { font-size: 14px; line-height: 1.6; color: #475569; margin-bottom: 24px; }" +
                "        .status-box { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 12px; padding: 20px; margin-bottom: 24px; }" +
                "        .pill-paid { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; font-size: 11px; font-weight: 800; text-transform: uppercase; padding: 5px 12px; border-radius: 20px; display: inline-block; }" +
                "        .timeline { margin: 20px 0; }" +
                "        .step { display: flex; align-items: flex-start; margin-bottom: 14px; }" +
                "        .step-circle { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; margin-right: 12px; flex-shrink: 0; }" +
                "        .circle-done { background: #10b981; color: white; }" +
                "        .circle-active { background: #3b82f6; color: white; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25); }" +
                "        .circle-upcoming { background: #e2e8f0; color: #64748b; }" +
                "        .step-info h4 { margin: 0; font-size: 13px; font-weight: 700; color: #1e293b; }" +
                "        .step-info p { margin: 2px 0 0; font-size: 12px; color: #64748b; line-height: 1.4; }" +
                "        .details-grid { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 13px; }" +
                "        .details-grid td { padding: 8px 10px; border-bottom: 1px solid #f1f5f9; }" +
                "        .details-grid td.label { color: #64748b; font-weight: 600; width: 40%; }" +
                "        .details-grid td.value { color: #0f172a; font-weight: 700; width: 60%; text-align: right; }" +
                "        .highlight-notice { background: #eff6ff; border-left: 4px solid #3b82f6; padding: 16px 18px; border-radius: 0 10px 10px 0; margin: 24px 0; }" +
                "        .highlight-notice h4 { margin: 0 0 4px; font-size: 13px; color: #1e3a8a; font-weight: 700; }" +
                "        .highlight-notice p { margin: 0; font-size: 12px; color: #1e40af; line-height: 1.5; }" +
                "        .footer { background: #f8fafc; padding: 20px 28px; border-top: 1px solid #e2e8f0; text-align: center; font-size: 11px; color: #94a3b8; line-height: 1.6; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"wrapper\">" +
                "        <div class=\"header\">" +
                "            <div class=\"brand-badge\">OmniBook Enterprise Suite</div>" +
                "            <h1>Registration &amp; Verification on Process</h1>" +
                "            <p>Order Reference: " + orderNumberSafe + " &bull; " + orgNameSafe + "</p>" +
                "        </div>" +
                "        <div class=\"body\">" +
                "            <div class=\"greeting\">Dear " + adminNameSafe + ",</div>" +
                "            <p class=\"lead-text\">" +
                "                Thank you for choosing <strong>OmniBook</strong>! We are delighted to confirm that your subscription payment for the <strong>" + planTierSafe + " Plan</strong> has been received and verified." +
                "            </p>" +
                "            <div class=\"status-box\">" +
                "                <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;\">" +
                "                    <span style=\"font-size: 12px; font-weight: 700; color: #475569; text-transform: uppercase;\">Subscription Details</span>" +
                "                    <span class=\"pill-paid\">Payment Confirmed</span>" +
                "                </div>" +
                "                <table class=\"details-grid\">" +
                "                    <tr><td class=\"label\">Organization Name</td><td class=\"value\">" + orgNameSafe + " (" + orgTypeSafe + ")</td></tr>" +
                "                    <tr><td class=\"label\">Plan Subscribed</td><td class=\"value\">" + planTierSafe + " (" + billingCycleSafe + ")</td></tr>" +
                "                    <tr><td class=\"label\">Order Number</td><td class=\"value\" style=\"font-family: monospace;\">" + orderNumberSafe + "</td></tr>" +
                "                    <tr><td class=\"label\">Tax Invoice #</td><td class=\"value\" style=\"font-family: monospace;\">" + invoiceNumberSafe + "</td></tr>" +
                "                    <tr><td class=\"label\">Payment Rail</td><td class=\"value\">" + paymentMethodSafe + "</td></tr>" +
                "                </table>" +
                "            </div>" +
                "            <div style=\"margin: 20px 0 10px; font-size: 13px; font-weight: 700; color: #0f172a;\">Onboarding Progress Tracker</div>" +
                "            <div class=\"timeline\">" +
                "                <div class=\"step\">" +
                "                    <div class=\"step-circle circle-done\">&#10003;</div>" +
                "                    <div class=\"step-info\">" +
                "                        <h4>Step 1: Order &amp; Payment Verification</h4>" +
                "                        <p>Your transaction has settled successfully with our financial gateway.</p>" +
                "                    </div>" +
                "                </div>" +
                "                <div class=\"step\">" +
                "                    <div class=\"step-circle circle-active\">2</div>" +
                "                    <div class=\"step-info\">" +
                "                        <h4 style=\"color: #1d4ed8;\">Step 2: Organization Audit &amp; System Provisioning (IN PROCESS)</h4>" +
                "                        <p>Our platform Super Admin team is verifying your registration/PAN number and configuring your dedicated cloud partition.</p>" +
                "                    </div>" +
                "                </div>" +
                "                <div class=\"step\">" +
                "                    <div class=\"step-circle circle-upcoming\">3</div>" +
                "                    <div class=\"step-info\">" +
                "                        <h4>Step 3: Administrator Invitation &amp; Credential Setup</h4>" +
                "                        <p>You will receive an official secure invitation link to initialize your administrator password and configure your portal.</p>" +
                "                    </div>" +
                "                </div>" +
                "            </div>" +
                "            <div class=\"highlight-notice\">" +
                "                <h4>Stay Updated!</h4>" +
                "                <p>" +
                "                    Verification is currently on process. No action is required from you at this time. As soon as our Super Admin audit is complete, we will deliver an official invitation link to set up your administrator account and access your organization portal." +
                "                </p>" +
                "            </div>" +
                "            <p style=\"font-size: 12px; color: #64748b; line-height: 1.5; margin: 0;\">" +
                "                If you have any questions or require expedited onboarding, feel free to reply directly to this email or contact support at <a href=\"mailto:support@omnibook.com\" style=\"color: #2563eb; text-decoration: none;\">support@omnibook.com</a>." +
                "            </p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            &copy; " + java.time.Year.now().getValue() + " OmniBook Multi-Tenant Platform &bull; All rights reserved.<br/>" +
                "            Automated notification sent to " + to + " for " + orgNameSafe + "." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Subscription verification update email successfully sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send subscription verification update email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    public void sendSubscriptionRejectionRefundEmail(
            String to,
            String adminFullName,
            String organizationName,
            String planTier,
            Double amount,
            String currency,
            String paymentMethod,
            String orderNumber,
            String invoiceNumber,
            String transactionId,
            String refundId,
            String rejectionReason,
            java.time.LocalDateTime refundedAt
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("OmniBook Subscription Update: Request Rejected & Refund Processed (#" + orderNumber + ")");

            String safeAdmin = (adminFullName != null && !adminFullName.isBlank()) ? adminFullName : "Organization Administrator";
            String safeOrg = (organizationName != null && !organizationName.isBlank()) ? organizationName : "Your Organization";
            String safeReason = (rejectionReason != null && !rejectionReason.isBlank()) ? rejectionReason : "Application did not satisfy organizational verification criteria.";
            String safeTxId = (transactionId != null && !transactionId.isBlank()) ? transactionId : "TXN-" + orderNumber;
            String safeRefundId = (refundId != null && !refundId.isBlank()) ? refundId : "REF-" + System.currentTimeMillis();
            String formattedAmount = (currency != null && currency.equalsIgnoreCase("NPR")) ? ("Rs. " + String.format("%,.2f", amount)) : ("$" + String.format("%,.2f", amount));
            String refundDateStr = refundedAt != null ? refundedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")) : java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a"));

            String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <style>" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; padding: 24px 12px; margin: 0; }" +
                "        .container { max-width: 620px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }" +
                "        .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 28px 32px; text-align: left; }" +
                "        .brand { font-size: 20px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px; display: inline-flex; align-items: center; }" +
                "        .brand span { color: #38bdf8; margin-left: 4px; }" +
                "        .badge { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; font-size: 11px; font-weight: 800; text-transform: uppercase; padding: 4px 10px; border-radius: 9999px; display: inline-block; margin-top: 12px; letter-spacing: 0.5px; }" +
                "        .content { padding: 32px; color: #1e293b; font-size: 14px; line-height: 1.6; }" +
                "        .greeting { font-size: 18px; font-weight: 700; color: #0f172a; margin-bottom: 8px; }" +
                "        .summary-text { color: #475569; margin-bottom: 24px; font-size: 14px; }" +
                "        .reason-card { background: #fff1f2; border: 1px solid #fecdd3; border-left: 4px solid #e11d48; padding: 18px 20px; border-radius: 10px; margin-bottom: 26px; }" +
                "        .reason-card h4 { margin: 0 0 6px; color: #9f1239; font-size: 13px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }" +
                "        .reason-card p { margin: 0; color: #881337; font-size: 14px; line-height: 1.5; font-weight: 500; }" +
                "        .table-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin-bottom: 26px; }" +
                "        .table-card h4 { margin: 0 0 14px; color: #0f172a; font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }" +
                "        .meta-row { display: flex; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed #e2e8f0; font-size: 13px; }" +
                "        .meta-row:last-child { border-bottom: none; }" +
                "        .meta-label { color: #64748b; font-weight: 500; }" +
                "        .meta-value { color: #0f172a; font-weight: 700; text-align: right; }" +
                "        .refund-highlight { color: #059669; font-weight: 800; font-size: 15px; }" +
                "        .info-callout { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 10px; padding: 16px 18px; margin-bottom: 26px; color: #166534; font-size: 13px; line-height: 1.5; }" +
                "        .info-callout strong { display: block; margin-bottom: 4px; font-size: 13px; }" +
                "        .footer { background: #f1f5f9; padding: 20px 32px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <div class=\"brand\">OMNI<span>BOOK</span> &bull; Platform Intelligence</div><br/>" +
                "            <div class=\"badge\">Subscription Application Rejected &amp; Refunded</div>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <div class=\"greeting\">Dear " + safeAdmin + ",</div>" +
                "            <div class=\"summary-text\">" +
                "                Thank you for your interest in OmniBook. Our Super Admin compliance team has reviewed the organization registration and subscription details submitted for <strong>" + safeOrg + "</strong>." +
                "                Regrettably, we are unable to approve this subscription application at this time." +
                "            </div>" +
                "            <div class=\"reason-card\">" +
                "                <h4>Rejection Rationale &amp; Audit Finding</h4>" +
                "                <p>" + safeReason + "</p>" +
                "            </div>" +
                "            <div class=\"table-card\">" +
                "                <h4>Confirmed Refund &amp; Transaction Details</h4>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Organization Name:</span><span class=\"meta-value\">" + safeOrg + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Plan Tier:</span><span class=\"meta-value\">" + planTier + " Plan</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Order Number:</span><span class=\"meta-value\">" + orderNumber + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Invoice Number:</span><span class=\"meta-value\">" + invoiceNumber + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Original Transaction ID:</span><span class=\"meta-value\" style=\"font-family: monospace; font-size: 12px;\">" + safeTxId + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Gateway Refund Reference ID:</span><span class=\"meta-value\" style=\"font-family: monospace; font-size: 12px; color: #2563eb;\">" + safeRefundId + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Payment Method:</span><span class=\"meta-value\">" + paymentMethod + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Amount Refunded:</span><span class=\"meta-value refund-highlight\">" + formattedAmount + "</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Refund Status:</span><span class=\"meta-value\" style=\"color: #059669;\">Settled / Completed ✓</span></div>" +
                "                <div class=\"meta-row\"><span class=\"meta-label\">Processed On:</span><span class=\"meta-value\">" + refundDateStr + "</span></div>" +
                "            </div>" +
                "            <div class=\"info-callout\">" +
                "                <strong>Financial Gateway Return Information</strong>" +
                "                The full amount has been released through <strong>" + paymentMethod + "</strong> to your original payment source. Depending on your financial institution or card issuer, the returned funds will reflect in your account within <strong>3 to 5 business days</strong> (instant for eSewa digital wallet)." +
                "            </div>" +
                "            <p style=\"font-size: 13px; color: #64748b; line-height: 1.5; margin: 0;\">" +
                "                If you have corrected organizational credentials or wish to appeal this decision, you may submit a new application through our pricing portal or contact our compliance desk directly at <a href=\"mailto:support@omnibook.com\" style=\"color: #2563eb; text-decoration: none; font-weight: bold;\">support@omnibook.com</a>." +
                "            </p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            &copy; " + java.time.Year.now().getValue() + " OmniBook Multi-Tenant Platform &bull; Security &amp; Compliance Division.<br/>" +
                "            This automated financial receipt was delivered to " + to + "." +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Subscription rejection & refund email successfully dispatched to {}", to);
        } catch (Exception e) {
            log.error("Failed to send subscription rejection & refund email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to dispatch refund email: " + e.getMessage(), e);
        }
    }
}


