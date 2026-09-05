const fs = require('fs');
const path = 'd:/Final Year Project/fyp-Backend/src/main/java/com/backend/service/EmailService.java';
let content = fs.readFileSync(path, 'utf8');

if (!content.includes('import jakarta.mail.internet.MimeMessage;')) {
    content = content.replace(
        'import org.springframework.mail.SimpleMailMessage;',
        'import jakarta.mail.internet.MimeMessage;\nimport org.springframework.mail.javamail.MimeMessageHelper;\nimport org.springframework.mail.SimpleMailMessage;'
    );
}

const htmlEmailLogic = `
    public void sendAdminInvite(String to, String token, String clinicName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject("Activate Your OmniBook Workspace: " + clinicName);
            
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
                "    <div class=\\"container\\">" +
                "        <div class=\\"header\\">" +
                "            <div>" +
                "                <h4>Secure Magic Link Invitation</h4>" +
                "                <p>To: " + to + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\\"content\\">" +
                "            <div class=\\"icon\\">&#128273;</div>" +
                "            <h3>Activate Your OmniBook Enterprise Workspace</h3>" +
                "            <p class=\\"message\\">Welcome to OmniBook. You have been invited to set up the root administrator account for <strong>" + clinicName + "</strong>.</p>" +
                "            <a href=\\"" + inviteLink + "\\" class=\\"button\\">Activate My Workspace</a>" +
                "            <p class=\\"expiry\\">This secure link expires in exactly 48 hours.</p>" +
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
`;

content = content.replace(
    /public void sendAdminInvite\([\s\S]*?\}[\s\S]*?\}/,
    htmlEmailLogic.trim()
);

fs.writeFileSync(path, content);
console.log('EmailService HTML email logic successfully added!');
