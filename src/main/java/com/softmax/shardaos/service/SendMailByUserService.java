package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.NewUserMailRequest;
import com.softmax.shardaos.models.User;
import com.softmax.shardaos.models.UserType;
import com.softmax.shardaos.repository.UserRepository;
import com.softmax.shardaos.repository.UserTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SendMailByUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.company.name:Softmax Technologies and IT Services}")
    private String companyName;

    @Value("${app.company.logo:https://example.com/logo.png}")
    private String companyLogo;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private List<String> getHeadDirectorEmails() {
        List<UserType> directorTypes = userTypeRepository.findAll().stream()
                .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
                .collect(Collectors.toList());

        return directorTypes.stream()
                .map(ut -> ut.getUser().getEmail())
                .filter(email -> email != null && !email.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private String getUserRole(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        if (userTypes.isEmpty()) return "USER";

        String ptype = userTypes.get(0).getPtype();
        String stype = userTypes.get(0).getStype();

        if ("EMP".equals(ptype)) {
            if ("DIRECTOR".equals(stype)) return "HEAD DIRECTOR";
            if ("MANAGER".equals(stype)) return "MANAGER";
            return "EMPLOYEE";
        } else if ("CONSULTANT".equals(ptype)) {
            return "CONSULTANT";
        } else if ("CLIENT".equals(ptype)) {
            return "CLIENT";
        }
        return "USER";
    }

    private String generateReferenceId(Long userId) {
        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" + userId;
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            if (to == null || to.trim().isEmpty()) {
                System.err.println("Email address is empty, skipping");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            System.out.println("✅ Email sent to: " + to);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email to " + to + ": " + e.getMessage());
        }
    }

    public void sendUserInquiryEmail(Long userId, NewUserMailRequest request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String userEmail = user.getEmail();

            if (userEmail == null || userEmail.trim().isEmpty()) {
                throw new RuntimeException("User email is missing. Please update your profile.");
            }

            String userInterest = request.getIntrest() != null ? request.getIntrest() : "General Inquiry";
            String userMessage = request.getMsg() != null ? request.getMsg() : "No message provided";
            String userRole = getUserRole(userId);
            String referenceId = generateReferenceId(userId);
            List<String> headDirectorEmails = getHeadDirectorEmails();

            System.out.println("📧 Sending inquiry from: " + userEmail);
            System.out.println("📝 Interest: " + userInterest);
            System.out.println("💬 Message: " + userMessage);
            System.out.println("🆔 Reference ID: " + referenceId);

            // Send to Head Directors
            if (!headDirectorEmails.isEmpty()) {
                String directorSubject = "🔔 " + generateSubject(userInterest, user.getName()) + " [Ref: " + referenceId + "] - Softmax Technologies and IT Services";
                String directorHtml = generateDirectorEmailHtml(user, userEmail, userInterest, userMessage, userRole, referenceId);
                for (String directorEmail : headDirectorEmails) {
                    sendEmail(directorEmail, directorSubject, directorHtml);
                }
            }

            // Send confirmation to user
            String userSubject = "✅ We've received your inquiry - " + companyName;
            String userHtml = generateUserConfirmationHtml(user, userInterest, userMessage, referenceId);
            sendEmail(userEmail, userSubject, userHtml);

            System.out.println("✅ Inquiry processed for user: " + user.getName());

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private String generateSubject(String interest, String userName) {
        String lowerInterest = interest.toLowerCase();
        String prefix;

        if (lowerInterest.contains("project")) prefix = "📁 Project Inquiry";
        else if (lowerInterest.contains("payment")) prefix = "💰 Payment Issue";
        else if (lowerInterest.contains("support")) prefix = "🛠️ Technical Support";
        else if (lowerInterest.contains("feedback")) prefix = "💬 User Feedback";
        else if (lowerInterest.contains("complaint")) prefix = "⚠️ User Complaint";
        else if (lowerInterest.contains("consultant")) prefix = "👨‍💻 Consultant Application";
        else if (lowerInterest.contains("client")) prefix = "🏢 Client Registration";
        else prefix = "📧 New User Inquiry";

        return prefix + " from " + userName;
    }

    private String generateDirectorEmailHtml(User user, String userEmail, String interest, String message, String userRole, String referenceId) {
        String currentTime = LocalDateTime.now().format(formatter);
        String gradientStart = "#667eea";
        String gradientEnd = "#764ba2";
        String accentColor = "#5b67e8";

        // ✅ User message ke links clickable banao
        String safeMessage = EmailService.makeLinksClickable(message);

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>New User Inquiry - " + referenceId + "</title>\n" +
                "    <style>\n" +
                "        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');\n" +
                "        body {\n" +
                "            font-family: 'Inter', Arial, sans-serif;\n" +
                "            line-height: 1.6;\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            background: linear-gradient(135deg, #f5f7fa 0%, #eef2f7 100%);\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 650px;\n" +
                "            margin: 40px auto;\n" +
                "            background: #ffffff;\n" +
                "            border-radius: 24px;\n" +
                "            box-shadow: 0 20px 60px rgba(0,0,0,0.15);\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: linear-gradient(135deg, " + gradientStart + " 0%, " + gradientEnd + " 100%);\n" +
                "            padding: 35px 30px;\n" +
                "            text-align: center;\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .header h1 {\n" +
                "            margin: 0;\n" +
                "            font-size: 28px;\n" +
                "            font-weight: 700;\n" +
                "            letter-spacing: -0.5px;\n" +
                "        }\n" +
                "        .header p {\n" +
                "            margin: 10px 0 0;\n" +
                "            opacity: 0.9;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .content {\n" +
                "            padding: 30px;\n" +
                "        }\n" +
                "        .ref-badge {\n" +
                "            background: linear-gradient(135deg, " + gradientStart + "15, " + gradientEnd + "15);\n" +
                "            border-left: 4px solid " + accentColor + ";\n" +
                "            padding: 12px 20px;\n" +
                "            border-radius: 12px;\n" +
                "            margin-bottom: 25px;\n" +
                "            font-family: monospace;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .ref-badge strong {\n" +
                "            color: " + accentColor + ";\n" +
                "        }\n" +
                "        .info-card {\n" +
                "            background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 20px;\n" +
                "            border: 1px solid #e8ecf4;\n" +
                "        }\n" +
                "        .info-row {\n" +
                "            display: flex;\n" +
                "            margin-bottom: 12px;\n" +
                "            padding-bottom: 12px;\n" +
                "            border-bottom: 1px solid #e0e6f0;\n" +
                "        }\n" +
                "        .info-row:last-child {\n" +
                "            border-bottom: none;\n" +
                "            margin-bottom: 0;\n" +
                "            padding-bottom: 0;\n" +
                "        }\n" +
                "        .info-label {\n" +
                "            width: 120px;\n" +
                "            font-weight: 600;\n" +
                "            color: #4a5568;\n" +
                "            font-size: 13px;\n" +
                "        }\n" +
                "        .info-value {\n" +
                "            flex: 1;\n" +
                "            color: #1a202c;\n" +
                "            font-weight: 500;\n" +
                "        }\n" +
                "        .message-box {\n" +
                "            background: #fff;\n" +
                "            border-radius: 16px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 20px;\n" +
                "            border: 1px solid #e2e8f0;\n" +
                "            box-shadow: 0 2px 8px rgba(0,0,0,0.04);\n" +
                "        }\n" +
                "        .message-box h3 {\n" +
                "            margin: 0 0 12px 0;\n" +
                "            color: " + accentColor + ";\n" +
                "            font-size: 16px;\n" +
                "        }\n" +
                "        .message-text {\n" +
                "            background: #f7fafc;\n" +
                "            padding: 16px;\n" +
                "            border-radius: 12px;\n" +
                "            color: #2d3748;\n" +
                "            line-height: 1.7;\n" +
                "            font-style: italic;\n" +
                "        }\n" +
                "        .message-text a {\n" +
                "            color: #2196F3;\n" +
                "            text-decoration: underline;\n" +
                "            word-break: break-all;\n" +
                "            font-style: normal;\n" +
                "        }\n" +
                "        .action-box {\n" +
                "            background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 20px;\n" +
                "            margin-top: 20px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .action-btn {\n" +
                "            display: inline-block;\n" +
                "            background: linear-gradient(135deg, " + gradientStart + " 0%, " + gradientEnd + " 100%);\n" +
                "            color: white;\n" +
                "            padding: 12px 28px;\n" +
                "            border-radius: 40px;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 600;\n" +
                "            margin-top: 10px;\n" +
                "            transition: transform 0.2s;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            background: #f7fafc;\n" +
                "            padding: 20px 30px;\n" +
                "            text-align: center;\n" +
                "            font-size: 12px;\n" +
                "            color: #718096;\n" +
                "            border-top: 1px solid #e2e8f0;\n" +
                "        }\n" +
                "        .footer a {\n" +
                "            color: #667eea;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .priority-badge {\n" +
                "            display: inline-block;\n" +
                "            background: #fef3c7;\n" +
                "            color: #d97706;\n" +
                "            padding: 4px 12px;\n" +
                "            border-radius: 20px;\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            margin-left: 10px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class='container'>\n" +
                "        <div class='header'>\n" +
                "            <h1>📧 New User Inquiry</h1>\n" +
                "            <p>" + companyName + " - Priority Support</p>\n" +
                "        </div>\n" +
                "        <div class='content'>\n" +
                "            <div class='ref-badge'>\n" +
                "                <strong>🔖 Reference ID:</strong> " + referenceId + "\n" +
                "            </div>\n" +
                "            <div class='info-card'>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>👤 User Name:</div>\n" +
                "                    <div class='info-value'><strong>" + escapeHtml(user.getName()) + "</strong></div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>📧 Email:</div>\n" +
                "                    <div class='info-value'><a href='mailto:" + escapeHtml(userEmail) + "' style='color: " + accentColor + ";'>" + escapeHtml(userEmail) + "</a></div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>🆔 User ID:</div>\n" +
                "                    <div class='info-value'>" + user.getUserId() + "</div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>👔 Role:</div>\n" +
                "                    <div class='info-value'>" + escapeHtml(userRole) + "</div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>📱 Phone:</div>\n" +
                "                    <div class='info-value'>" + (user.getPhoneNumber() != null ? escapeHtml(user.getPhoneNumber()) : "Not provided") + "</div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>🎯 Interest:</div>\n" +
                "                    <div class='info-value'><strong>" + escapeHtml(interest) + "</strong><span class='priority-badge'>⚠️ Review Required</span></div>\n" +
                "                </div>\n" +
                "                <div class='info-row'>\n" +
                "                    <div class='info-label'>📅 Submitted:</div>\n" +
                "                    <div class='info-value'>" + currentTime + "</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class='message-box'>\n" +
                "                <h3>💬 User Message</h3>\n" +
                "                <div class='message-text'>\"" + safeMessage + "\"</div>\n" +
                "            </div>\n" +
                "            <div class='action-box'>\n" +
                "                <strong>📌 Quick Action Required</strong><br/>\n" +
                "                <a href='mailto:" + escapeHtml(userEmail) + "' class='action-btn'>✉️ Reply to User</a>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class='footer'>\n" +
                "            <p><strong>" + companyName + "</strong></p>\n" +
                "            <p>🌐 <a href='https://softmax.in'>softmax.in</a> | 💼 <a href='https://nexus.softmax.in'>nexus.softmax.in</a></p>\n" +
                "            <p>📧 <a href='mailto:softmaxdigitals@gmail.com'>softmaxdigitals@gmail.com</a></p>\n" +
                "            <p>This is an automated notification from " + companyName + ".<br/>\n" +
                "            Please review this inquiry at the earliest convenience.<br/>\n" +
                "            Reference ID: <strong>" + referenceId + "</strong></p>\n" +
                "            <p>© 2026 " + companyName + " | All Rights Reserved</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String generateUserConfirmationHtml(User user, String interest, String message, String referenceId) {
        String gradientStart = "#667eea";
        String gradientEnd = "#764ba2";

        // ✅ User message ke links clickable banao
        String safeMessage = EmailService.makeLinksClickable(message);

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>Inquiry Received - " + companyName + "</title>\n" +
                "    <style>\n" +
                "        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');\n" +
                "        body {\n" +
                "            font-family: 'Inter', Arial, sans-serif;\n" +
                "            line-height: 1.6;\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            background: linear-gradient(135deg, #f0f4f8 0%, #e2e8f0 100%);\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 550px;\n" +
                "            margin: 40px auto;\n" +
                "            background: #ffffff;\n" +
                "            border-radius: 24px;\n" +
                "            box-shadow: 0 20px 60px rgba(0,0,0,0.1);\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);\n" +
                "            padding: 35px 30px;\n" +
                "            text-align: center;\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .header h1 {\n" +
                "            margin: 0;\n" +
                "            font-size: 32px;\n" +
                "        }\n" +
                "        .content {\n" +
                "            padding: 30px;\n" +
                "        }\n" +
                "        .success-icon {\n" +
                "            text-align: center;\n" +
                "            font-size: 64px;\n" +
                "            margin: 20px 0;\n" +
                "        }\n" +
                "        .greeting {\n" +
                "            font-size: 18px;\n" +
                "            color: #1a202c;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .ref-card {\n" +
                "            background: linear-gradient(135deg, " + gradientStart + "10, " + gradientEnd + "10);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 20px;\n" +
                "            margin: 20px 0;\n" +
                "            border: 1px solid #e2e8f0;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .ref-code {\n" +
                "            font-family: monospace;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: bold;\n" +
                "            color: " + gradientStart + ";\n" +
                "            background: white;\n" +
                "            padding: 8px 16px;\n" +
                "            border-radius: 8px;\n" +
                "            display: inline-block;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .message-review {\n" +
                "            background: #f7fafc;\n" +
                "            border-radius: 12px;\n" +
                "            padding: 16px;\n" +
                "            margin: 20px 0;\n" +
                "            border-left: 4px solid #22c55e;\n" +
                "        }\n" +
                "        .message-review a {\n" +
                "            color: #2196F3;\n" +
                "            text-decoration: underline;\n" +
                "            word-break: break-all;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            background: #f7fafc;\n" +
                "            padding: 20px 30px;\n" +
                "            text-align: center;\n" +
                "            font-size: 12px;\n" +
                "            color: #718096;\n" +
                "            border-top: 1px solid #e2e8f0;\n" +
                "        }\n" +
                "        .footer a {\n" +
                "            color: #667eea;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);\n" +
                "            color: white;\n" +
                "            padding: 12px 28px;\n" +
                "            border-radius: 40px;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 600;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class='container'>\n" +
                "        <div class='header'>\n" +
                "            <h1>✅ Inquiry Received!</h1>\n" +
                "            <p>We're here to help you</p>\n" +
                "        </div>\n" +
                "        <div class='content'>\n" +
                "            <div class='success-icon'>🎉</div>\n" +
                "            <div class='greeting'>\n" +
                "                Dear <strong>" + escapeHtml(user.getName()) + "</strong>,\n" +
                "            </div>\n" +
                "            <p>Thank you for reaching out to <strong>" + companyName + "</strong>. We have received your inquiry regarding:</p>\n" +
                "            <div class='message-review'>\n" +
                "                <strong>📝 Interest:</strong> " + escapeHtml(interest) + "<br/><br/>\n" +
                "                <strong>💬 Your Message:</strong><br/>\n" +
                "                \"" + safeMessage + "\"\n" +
                "            </div>\n" +
                "            <div class='ref-card'>\n" +
                "                <strong>🔖 Your Reference ID</strong><br/>\n" +
                "                <div class='ref-code'>" + referenceId + "</div>\n" +
                "                <p style='font-size: 12px; margin-top: 10px;'>Please keep this reference ID for future communication.</p>\n" +
                "            </div>\n" +
                "            <p>Our team will review your request and get back to you within <strong>24-48 business hours</strong>.</p>\n" +
                "            <div style='text-align: center;'>\n" +
                "                <a href='mailto:softmaxdigitals@gmail.com' class='btn'>📧 Contact Support</a>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class='footer'>\n" +
                "            <p><strong>" + companyName + "</strong></p>\n" +
                "            <p>🌐 <a href='https://softmax.in'>softmax.in</a> | 💼 <a href='https://nexus.softmax.in'>nexus.softmax.in</a></p>\n" +
                "            <p>📧 <a href='mailto:softmaxdigitals@gmail.com'>softmaxdigitals@gmail.com</a></p>\n" +
                "            <p>© 2026 " + companyName + " | All Rights Reserved<br/>\n" +
                "            This is an automated confirmation, please do not reply to this email.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}









//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.NewUserMailRequest;
//import com.softmax.shardaos.models.User;
//import com.softmax.shardaos.models.UserType;
//import com.softmax.shardaos.repository.UserRepository;
//import com.softmax.shardaos.repository.UserTypeRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.stereotype.Service;
//
//import jakarta.mail.internet.MimeMessage;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@Service
//public class SendMailByUserService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private JavaMailSender mailSender;
//
//    @Value("${spring.mail.username}")
//    private String fromEmail;
//
//    @Value("${app.company.name:ShardaOS}")
//    private String companyName;
//
//    @Value("${app.company.logo:https://example.com/logo.png}")
//    private String companyLogo;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//
//    private List<String> getHeadDirectorEmails() {
//        List<UserType> directorTypes = userTypeRepository.findAll().stream()
//                .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
//                .collect(Collectors.toList());
//
//        return directorTypes.stream()
//                .map(ut -> ut.getUser().getEmail())
//                .filter(email -> email != null && !email.trim().isEmpty())
//                .collect(Collectors.toList());
//    }
//
//    private String getUserRole(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        if (userTypes.isEmpty()) return "USER";
//
//        String ptype = userTypes.get(0).getPtype();
//        String stype = userTypes.get(0).getStype();
//
//        if ("EMP".equals(ptype)) {
//            if ("DIRECTOR".equals(stype)) return "HEAD DIRECTOR";
//            if ("MANAGER".equals(stype)) return "MANAGER";
//            return "EMPLOYEE";
//        } else if ("CONSULTANT".equals(ptype)) {
//            return "CONSULTANT";
//        } else if ("CLIENT".equals(ptype)) {
//            return "CLIENT";
//        }
//        return "USER";
//    }
//
//    private String generateReferenceId(Long userId) {
//        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" + userId;
//    }
//
//    private void sendEmail(String to, String subject, String htmlContent) {
//        try {
//            if (to == null || to.trim().isEmpty()) {
//                System.err.println("Email address is empty, skipping");
//                return;
//            }
//
//            MimeMessage message = mailSender.createMimeMessage();
//            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
//            helper.setFrom(fromEmail);
//            helper.setTo(to.trim());
//            helper.setSubject(subject);
//            helper.setText(htmlContent, true);
//
//            mailSender.send(message);
//            System.out.println("✅ Email sent to: " + to);
//        } catch (Exception e) {
//            System.err.println("❌ Failed to send email to " + to + ": " + e.getMessage());
//        }
//    }
//
//    public void sendUserInquiryEmail(Long userId, NewUserMailRequest request) {
//        try {
//            User user = userRepository.findById(userId)
//                    .orElseThrow(() -> new RuntimeException("User not found"));
//
//            String userEmail = user.getEmail();
//
//            if (userEmail == null || userEmail.trim().isEmpty()) {
//                throw new RuntimeException("User email is missing. Please update your profile.");
//            }
//
//            String userInterest = request.getIntrest() != null ? request.getIntrest() : "General Inquiry";
//            String userMessage = request.getMsg() != null ? request.getMsg() : "No message provided";
//            String userRole = getUserRole(userId);
//            String referenceId = generateReferenceId(userId);
//            List<String> headDirectorEmails = getHeadDirectorEmails();
//
//            System.out.println("📧 Sending inquiry from: " + userEmail);
//            System.out.println("📝 Interest: " + userInterest);
//            System.out.println("💬 Message: " + userMessage);
//            System.out.println("🆔 Reference ID: " + referenceId);
//
//            // Send to Head Directors
//            if (!headDirectorEmails.isEmpty()) {
//                String directorSubject = "🔔 " + generateSubject(userInterest, user.getName()) + " [Ref: " + referenceId + "]";
//                String directorHtml = generateDirectorEmailHtml(user, userEmail, userInterest, userMessage, userRole, referenceId);
//                for (String directorEmail : headDirectorEmails) {
//                    sendEmail(directorEmail, directorSubject, directorHtml);
//                }
//            }
//
//            // Send confirmation to user
//            String userSubject = "✅ We've received your inquiry - " + companyName;
//            String userHtml = generateUserConfirmationHtml(user, userInterest, userMessage, referenceId);
//            sendEmail(userEmail, userSubject, userHtml);
//
//            System.out.println("✅ Inquiry processed for user: " + user.getName());
//
//        } catch (Exception e) {
//            System.err.println("❌ Error: " + e.getMessage());
//            throw new RuntimeException(e.getMessage());
//        }
//    }
//
//    private String generateSubject(String interest, String userName) {
//        String lowerInterest = interest.toLowerCase();
//        String prefix;
//
//        if (lowerInterest.contains("project")) prefix = "📁 Project Inquiry";
//        else if (lowerInterest.contains("payment")) prefix = "💰 Payment Issue";
//        else if (lowerInterest.contains("support")) prefix = "🛠️ Technical Support";
//        else if (lowerInterest.contains("feedback")) prefix = "💬 User Feedback";
//        else if (lowerInterest.contains("complaint")) prefix = "⚠️ User Complaint";
//        else if (lowerInterest.contains("consultant")) prefix = "👨‍💻 Consultant Application";
//        else if (lowerInterest.contains("client")) prefix = "🏢 Client Registration";
//        else prefix = "📧 New User Inquiry";
//
//        return prefix + " from " + userName;
//    }
//
//    private String generateDirectorEmailHtml(User user, String userEmail, String interest, String message, String userRole, String referenceId) {
//        String currentTime = LocalDateTime.now().format(formatter);
//        String gradientStart = "#667eea";
//        String gradientEnd = "#764ba2";
//        String accentColor = "#5b67e8";
//
//        return "<!DOCTYPE html>\n" +
//                "<html>\n" +
//                "<head>\n" +
//                "    <meta charset='UTF-8'>\n" +
//                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
//                "    <title>New User Inquiry - " + referenceId + "</title>\n" +
//                "    <style>\n" +
//                "        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');\n" +
//                "        body {\n" +
//                "            font-family: 'Inter', Arial, sans-serif;\n" +
//                "            line-height: 1.6;\n" +
//                "            margin: 0;\n" +
//                "            padding: 0;\n" +
//                "            background: linear-gradient(135deg, #f5f7fa 0%, #eef2f7 100%);\n" +
//                "        }\n" +
//                "        .container {\n" +
//                "            max-width: 650px;\n" +
//                "            margin: 40px auto;\n" +
//                "            background: #ffffff;\n" +
//                "            border-radius: 24px;\n" +
//                "            box-shadow: 0 20px 60px rgba(0,0,0,0.15);\n" +
//                "            overflow: hidden;\n" +
//                "        }\n" +
//                "        .header {\n" +
//                "            background: linear-gradient(135deg, " + gradientStart + " 0%, " + gradientEnd + " 100%);\n" +
//                "            padding: 35px 30px;\n" +
//                "            text-align: center;\n" +
//                "            color: white;\n" +
//                "        }\n" +
//                "        .header h1 {\n" +
//                "            margin: 0;\n" +
//                "            font-size: 28px;\n" +
//                "            font-weight: 700;\n" +
//                "            letter-spacing: -0.5px;\n" +
//                "        }\n" +
//                "        .header p {\n" +
//                "            margin: 10px 0 0;\n" +
//                "            opacity: 0.9;\n" +
//                "            font-size: 14px;\n" +
//                "        }\n" +
//                "        .content {\n" +
//                "            padding: 30px;\n" +
//                "        }\n" +
//                "        .ref-badge {\n" +
//                "            background: linear-gradient(135deg, " + gradientStart + "15, " + gradientEnd + "15);\n" +
//                "            border-left: 4px solid " + accentColor + ";\n" +
//                "            padding: 12px 20px;\n" +
//                "            border-radius: 12px;\n" +
//                "            margin-bottom: 25px;\n" +
//                "            font-family: monospace;\n" +
//                "            font-size: 14px;\n" +
//                "        }\n" +
//                "        .ref-badge strong {\n" +
//                "            color: " + accentColor + ";\n" +
//                "        }\n" +
//                "        .info-card {\n" +
//                "            background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);\n" +
//                "            border-radius: 16px;\n" +
//                "            padding: 20px;\n" +
//                "            margin-bottom: 20px;\n" +
//                "            border: 1px solid #e8ecf4;\n" +
//                "        }\n" +
//                "        .info-row {\n" +
//                "            display: flex;\n" +
//                "            margin-bottom: 12px;\n" +
//                "            padding-bottom: 12px;\n" +
//                "            border-bottom: 1px solid #e0e6f0;\n" +
//                "        }\n" +
//                "        .info-row:last-child {\n" +
//                "            border-bottom: none;\n" +
//                "            margin-bottom: 0;\n" +
//                "            padding-bottom: 0;\n" +
//                "        }\n" +
//                "        .info-label {\n" +
//                "            width: 120px;\n" +
//                "            font-weight: 600;\n" +
//                "            color: #4a5568;\n" +
//                "            font-size: 13px;\n" +
//                "        }\n" +
//                "        .info-value {\n" +
//                "            flex: 1;\n" +
//                "            color: #1a202c;\n" +
//                "            font-weight: 500;\n" +
//                "        }\n" +
//                "        .message-box {\n" +
//                "            background: #fff;\n" +
//                "            border-radius: 16px;\n" +
//                "            padding: 20px;\n" +
//                "            margin-bottom: 20px;\n" +
//                "            border: 1px solid #e2e8f0;\n" +
//                "            box-shadow: 0 2px 8px rgba(0,0,0,0.04);\n" +
//                "        }\n" +
//                "        .message-box h3 {\n" +
//                "            margin: 0 0 12px 0;\n" +
//                "            color: " + accentColor + ";\n" +
//                "            font-size: 16px;\n" +
//                "        }\n" +
//                "        .message-text {\n" +
//                "            background: #f7fafc;\n" +
//                "            padding: 16px;\n" +
//                "            border-radius: 12px;\n" +
//                "            color: #2d3748;\n" +
//                "            line-height: 1.7;\n" +
//                "            font-style: italic;\n" +
//                "        }\n" +
//                "        .action-box {\n" +
//                "            background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);\n" +
//                "            border-radius: 16px;\n" +
//                "            padding: 20px;\n" +
//                "            margin-top: 20px;\n" +
//                "            text-align: center;\n" +
//                "        }\n" +
//                "        .action-btn {\n" +
//                "            display: inline-block;\n" +
//                "            background: linear-gradient(135deg, " + gradientStart + " 0%, " + gradientEnd + " 100%);\n" +
//                "            color: white;\n" +
//                "            padding: 12px 28px;\n" +
//                "            border-radius: 40px;\n" +
//                "            text-decoration: none;\n" +
//                "            font-weight: 600;\n" +
//                "            margin-top: 10px;\n" +
//                "            transition: transform 0.2s;\n" +
//                "        }\n" +
//                "        .footer {\n" +
//                "            background: #f7fafc;\n" +
//                "            padding: 20px 30px;\n" +
//                "            text-align: center;\n" +
//                "            font-size: 12px;\n" +
//                "            color: #718096;\n" +
//                "            border-top: 1px solid #e2e8f0;\n" +
//                "        }\n" +
//                "        .priority-badge {\n" +
//                "            display: inline-block;\n" +
//                "            background: #fef3c7;\n" +
//                "            color: #d97706;\n" +
//                "            padding: 4px 12px;\n" +
//                "            border-radius: 20px;\n" +
//                "            font-size: 12px;\n" +
//                "            font-weight: 600;\n" +
//                "            margin-left: 10px;\n" +
//                "        }\n" +
//                "    </style>\n" +
//                "</head>\n" +
//                "<body>\n" +
//                "    <div class='container'>\n" +
//                "        <div class='header'>\n" +
//                "            <h1>📧 New User Inquiry</h1>\n" +
//                "            <p>" + companyName + " - Priority Support</p>\n" +
//                "        </div>\n" +
//                "        <div class='content'>\n" +
//                "            <div class='ref-badge'>\n" +
//                "                <strong>🔖 Reference ID:</strong> " + referenceId + "\n" +
//                "            </div>\n" +
//                "            <div class='info-card'>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>👤 User Name:</div>\n" +
//                "                    <div class='info-value'><strong>" + escapeHtml(user.getName()) + "</strong></div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>📧 Email:</div>\n" +
//                "                    <div class='info-value'><a href='mailto:" + escapeHtml(userEmail) + "' style='color: " + accentColor + ";'>" + escapeHtml(userEmail) + "</a></div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>🆔 User ID:</div>\n" +
//                "                    <div class='info-value'>" + user.getUserId() + "</div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>👔 Role:</div>\n" +
//                "                    <div class='info-value'>" + escapeHtml(userRole) + "</div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>📱 Phone:</div>\n" +
//                "                    <div class='info-value'>" + (user.getPhoneNumber() != null ? escapeHtml(user.getPhoneNumber()) : "Not provided") + "</div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>🎯 Interest:</div>\n" +
//                "                    <div class='info-value'><strong>" + escapeHtml(interest) + "</strong><span class='priority-badge'>⚠️ Review Required</span></div>\n" +
//                "                </div>\n" +
//                "                <div class='info-row'>\n" +
//                "                    <div class='info-label'>📅 Submitted:</div>\n" +
//                "                    <div class='info-value'>" + currentTime + "</div>\n" +
//                "                </div>\n" +
//                "            </div>\n" +
//                "            <div class='message-box'>\n" +
//                "                <h3>💬 User Message</h3>\n" +
//                "                <div class='message-text'>\"" + escapeHtml(message) + "\"</div>\n" +
//                "            </div>\n" +
//                "            <div class='action-box'>\n" +
//                "                <strong>📌 Quick Action Required</strong><br/>\n" +
//                "                <a href='mailto:" + escapeHtml(userEmail) + "' class='action-btn'>✉️ Reply to User</a>\n" +
//                "            </div>\n" +
//                "        </div>\n" +
//                "        <div class='footer'>\n" +
//                "            <p>This is an automated notification from " + companyName + ".<br/>\n" +
//                "            Please review this inquiry at the earliest convenience.<br/>\n" +
//                "            Reference ID: <strong>" + referenceId + "</strong></p>\n" +
//                "            <p>© " + LocalDateTime.now().getYear() + " " + companyName + " | All Rights Reserved</p>\n" +
//                "        </div>\n" +
//                "    </div>\n" +
//                "</body>\n" +
//                "</html>";
//    }
//
//    private String generateUserConfirmationHtml(User user, String interest, String message, String referenceId) {
//        String gradientStart = "#667eea";
//        String gradientEnd = "#764ba2";
//
//        return "<!DOCTYPE html>\n" +
//                "<html>\n" +
//                "<head>\n" +
//                "    <meta charset='UTF-8'>\n" +
//                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
//                "    <title>Inquiry Received - " + companyName + "</title>\n" +
//                "    <style>\n" +
//                "        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');\n" +
//                "        body {\n" +
//                "            font-family: 'Inter', Arial, sans-serif;\n" +
//                "            line-height: 1.6;\n" +
//                "            margin: 0;\n" +
//                "            padding: 0;\n" +
//                "            background: linear-gradient(135deg, #f0f4f8 0%, #e2e8f0 100%);\n" +
//                "        }\n" +
//                "        .container {\n" +
//                "            max-width: 550px;\n" +
//                "            margin: 40px auto;\n" +
//                "            background: #ffffff;\n" +
//                "            border-radius: 24px;\n" +
//                "            box-shadow: 0 20px 60px rgba(0,0,0,0.1);\n" +
//                "            overflow: hidden;\n" +
//                "        }\n" +
//                "        .header {\n" +
//                "            background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);\n" +
//                "            padding: 35px 30px;\n" +
//                "            text-align: center;\n" +
//                "            color: white;\n" +
//                "        }\n" +
//                "        .header h1 {\n" +
//                "            margin: 0;\n" +
//                "            font-size: 32px;\n" +
//                "        }\n" +
//                "        .content {\n" +
//                "            padding: 30px;\n" +
//                "        }\n" +
//                "        .success-icon {\n" +
//                "            text-align: center;\n" +
//                "            font-size: 64px;\n" +
//                "            margin: 20px 0;\n" +
//                "        }\n" +
//                "        .greeting {\n" +
//                "            font-size: 18px;\n" +
//                "            color: #1a202c;\n" +
//                "            margin-bottom: 20px;\n" +
//                "        }\n" +
//                "        .ref-card {\n" +
//                "            background: linear-gradient(135deg, " + gradientStart + "10, " + gradientEnd + "10);\n" +
//                "            border-radius: 16px;\n" +
//                "            padding: 20px;\n" +
//                "            margin: 20px 0;\n" +
//                "            border: 1px solid #e2e8f0;\n" +
//                "            text-align: center;\n" +
//                "        }\n" +
//                "        .ref-code {\n" +
//                "            font-family: monospace;\n" +
//                "            font-size: 18px;\n" +
//                "            font-weight: bold;\n" +
//                "            color: " + gradientStart + ";\n" +
//                "            background: white;\n" +
//                "            padding: 8px 16px;\n" +
//                "            border-radius: 8px;\n" +
//                "            display: inline-block;\n" +
//                "            margin-top: 10px;\n" +
//                "        }\n" +
//                "        .message-review {\n" +
//                "            background: #f7fafc;\n" +
//                "            border-radius: 12px;\n" +
//                "            padding: 16px;\n" +
//                "            margin: 20px 0;\n" +
//                "            border-left: 4px solid #22c55e;\n" +
//                "        }\n" +
//                "        .footer {\n" +
//                "            background: #f7fafc;\n" +
//                "            padding: 20px 30px;\n" +
//                "            text-align: center;\n" +
//                "            font-size: 12px;\n" +
//                "            color: #718096;\n" +
//                "            border-top: 1px solid #e2e8f0;\n" +
//                "        }\n" +
//                "        .btn {\n" +
//                "            display: inline-block;\n" +
//                "            background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);\n" +
//                "            color: white;\n" +
//                "            padding: 12px 28px;\n" +
//                "            border-radius: 40px;\n" +
//                "            text-decoration: none;\n" +
//                "            font-weight: 600;\n" +
//                "            margin-top: 10px;\n" +
//                "        }\n" +
//                "    </style>\n" +
//                "</head>\n" +
//                "<body>\n" +
//                "    <div class='container'>\n" +
//                "        <div class='header'>\n" +
//                "            <h1>✅ Inquiry Received!</h1>\n" +
//                "            <p>We're here to help you</p>\n" +
//                "        </div>\n" +
//                "        <div class='content'>\n" +
//                "            <div class='success-icon'>🎉</div>\n" +
//                "            <div class='greeting'>\n" +
//                "                Dear <strong>" + escapeHtml(user.getName()) + "</strong>,\n" +
//                "            </div>\n" +
//                "            <p>Thank you for reaching out to <strong>" + companyName + "</strong>. We have received your inquiry regarding:</p>\n" +
//                "            <div class='message-review'>\n" +
//                "                <strong>📝 Interest:</strong> " + escapeHtml(interest) + "<br/><br/>\n" +
//                "                <strong>💬 Your Message:</strong><br/>\n" +
//                "                \"" + escapeHtml(message) + "\"\n" +
//                "            </div>\n" +
//                "            <div class='ref-card'>\n" +
//                "                <strong>🔖 Your Reference ID</strong><br/>\n" +
//                "                <div class='ref-code'>" + referenceId + "</div>\n" +
//                "                <p style='font-size: 12px; margin-top: 10px;'>Please keep this reference ID for future communication.</p>\n" +
//                "            </div>\n" +
//                "            <p>Our team will review your request and get back to you within <strong>24-48 business hours</strong>.</p>\n" +
//                "            <div style='text-align: center;'>\n" +
//                "                <a href='mailto:support@shardaos.com' class='btn'>📧 Contact Support</a>\n" +
//                "            </div>\n" +
//                "        </div>\n" +
//                "        <div class='footer'>\n" +
//                "            <p>© " + LocalDateTime.now().getYear() + " " + companyName + " | All Rights Reserved<br/>\n" +
//                "            This is an automated confirmation, please do not reply to this email.</p>\n" +
//                "        </div>\n" +
//                "    </div>\n" +
//                "</body>\n" +
//                "</html>";
//    }
//
//    private String escapeHtml(String input) {
//        if (input == null) return "";
//        return input.replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;")
//                .replace("\"", "&quot;")
//                .replace("'", "&#39;");
//    }
//}