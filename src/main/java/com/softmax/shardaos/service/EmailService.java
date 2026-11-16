package com.softmax.shardaos.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private final String FROM_EMAIL = "noreply@softmax.in";
    private final String FROM_NAME = "Softmax Technologies and IT Services";
    private final String REPLY_TO = "noreply@softmax.in";

    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // ✅ From with display name (spam filter friendly)
            helper.setFrom(new InternetAddress(FROM_EMAIL, FROM_NAME));

            // ✅ Reply-To
            helper.setReplyTo(new InternetAddress(REPLY_TO));

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            // ✅ Anti-spam headers
            message.setHeader("X-Mailer", "Softmax Mailer");
            message.setHeader("Precedence", "bulk");
            message.setHeader("X-Priority", "3");
            message.setHeader("List-Unsubscribe", "<mailto:noreply@softmax.in?subject=unsubscribe>");

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Error while sending email: " + e.getMessage());
        }
    }

    public void sendTextEmail(String to, String subject, String body) {
        sendEmail(to, subject, body);
    }

    public static String makeLinksClickable(String text) {
        if (text == null || text.isEmpty()) return "";

        String safe = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        String urlRegex = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)";

        return safe.replaceAll(urlRegex,
                "<a href='$1' target='_blank' style='color:#1a73e8; text-decoration:underline;'>$1</a>");
    }
}




//package com.softmax.shardaos.service;
//
//import jakarta.mail.internet.MimeMessage;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.stereotype.Service;
//
//@Service
//public class EmailService {
//
//    @Autowired
//    private JavaMailSender mailSender;
//
//    private final String FROM_EMAIL = "softmaxdigitals@gmail.com";
//
//    // 🔥 HTML Email Function (MAIN)
//    public void sendEmail(String to, String subject, String htmlContent) {
//        try {
//            MimeMessage message = mailSender.createMimeMessage();
//
//            MimeMessageHelper helper = new MimeMessageHelper(message, true);
//
//            helper.setFrom(FROM_EMAIL);
//            helper.setTo(to);
//            helper.setSubject(subject);
//            helper.setText(htmlContent, true); // true = HTML
//
//            mailSender.send(message);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error while sending email: " + e.getMessage());
//        }
//    }
//
//    // 🔥 Plain Text Email (optional)
//    public void sendTextEmail(String to, String subject, String body) {
//        sendEmail(to, subject, body);
//    }
//
//    // ✅ Helper to make URLs clickable in Gmail
//    public static String makeLinksClickable(String text) {
//        if (text == null || text.isEmpty()) return "";
//
//        String safe = text.replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;");
//
//        String urlRegex = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)";
//
//        return safe.replaceAll(urlRegex,
//                "<a href='$1' target='_blank' style='color:#2196F3; text-decoration:underline; word-break:break-all;'>$1</a>");
//    }
//}
//








//package com.softmax.shardaos.service;
//
//import jakarta.mail.internet.MimeMessage;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.stereotype.Service;
//
//@Service
//public class EmailService {
//
//    @Autowired
//    private JavaMailSender mailSender;
//
//    private final String FROM_EMAIL = "shivamsharma220827@gmail.com";
//
//    // 🔥 HTML Email Function (MAIN)
//    public void sendEmail(String to, String subject, String htmlContent) {
//        try {
//            MimeMessage message = mailSender.createMimeMessage();
//
//            MimeMessageHelper helper = new MimeMessageHelper(message, true);
//
//            helper.setFrom(FROM_EMAIL);
//            helper.setTo(to);
//            helper.setSubject(subject);
//            helper.setText(htmlContent, true); // true = HTML
//
//            mailSender.send(message);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error while sending email: " + e.getMessage());
//        }
//    }
//
//    // 🔥 Plain Text Email (optional)
//    public void sendTextEmail(String to, String subject, String body) {
//        sendEmail(to, subject, body);
//    }
//}