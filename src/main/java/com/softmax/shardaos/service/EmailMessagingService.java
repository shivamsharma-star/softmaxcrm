package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.SendEmailRequest;
import com.softmax.shardaos.dto.output.EmailRecipientDTO;
import com.softmax.shardaos.dto.output.EmailResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmailMessagingService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // ==================== ROLE VALIDATION ====================

    public boolean isManager(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "MANAGER".equals(ut.getStype()))
                .isPresent();
    }

    public boolean isDirector(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "DIRECTOR".equals(ut.getStype()))
                .isPresent();
    }

    public boolean isHRDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 2L;
    }

    public boolean isHeadDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 1L;
    }

    // ==================== GET RECIPIENTS BY ROLE ====================

    // Get all consultants
    public List<EmailRecipientDTO> getAllConsultants() {
        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
        return consultants.stream().map(c -> EmailRecipientDTO.builder()
                .userId(c.getUser().getUserId())
                .name(c.getUser().getName())
                .email(c.getUser().getEmail())
                .role("CONSULTANT")
                .department(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
                .build()).collect(Collectors.toList());
    }

    // Get all employees
    public List<EmailRecipientDTO> getAllEmployees() {
        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
        return employees.stream().map(e -> EmailRecipientDTO.builder()
                .userId(e.getUser().getUserId())
                .name(e.getUser().getName())
                .email(e.getUser().getEmail())
                .role("EMPLOYEE")
                .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
                .build()).collect(Collectors.toList());
    }

    // Get all managers
    public List<EmailRecipientDTO> getAllManagers() {
        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
        return employees.stream()
                .filter(e -> isManager(e.getUser().getUserId()))
                .map(e -> EmailRecipientDTO.builder()
                        .userId(e.getUser().getUserId())
                        .name(e.getUser().getName())
                        .email(e.getUser().getEmail())
                        .role("MANAGER")
                        .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    // Get all directors
    public List<EmailRecipientDTO> getAllDirectors() {
        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
        return employees.stream()
                .filter(e -> isDirector(e.getUser().getUserId()))
                .map(e -> EmailRecipientDTO.builder()
                        .userId(e.getUser().getUserId())
                        .name(e.getUser().getName())
                        .email(e.getUser().getEmail())
                        .role("DIRECTOR")
                        .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    // Get all users (for Head Director)
    public List<EmailRecipientDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(u -> {
            List<UserType> types = userTypeRepository.findByUserUserId(u.getUserId());
            String role = types.isEmpty() ? "USER" : types.get(0).getPtype();
            return EmailRecipientDTO.builder()
                    .userId(u.getUserId())
                    .name(u.getName())
                    .email(u.getEmail())
                    .role(role)
                    .department(null)
                    .build();
        }).collect(Collectors.toList());
    }

    // Get consultants of manager's department
    public List<EmailRecipientDTO> getMyDepartmentConsultants(Long managerId) {
        EmployeeDetails manager = employeeDetailsRepository.findByUserUserId(managerId).orElse(null);
        if (manager == null || manager.getDepartmentRef() == null) return new ArrayList<>();

        Long deptId = manager.getDepartmentRef().getDepartmentId();
        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(deptId);

        return consultants.stream().map(c -> EmailRecipientDTO.builder()
                .userId(c.getUser().getUserId())
                .name(c.getUser().getName())
                .email(c.getUser().getEmail())
                .role("CONSULTANT")
                .department(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
                .build()).collect(Collectors.toList());
    }

    // ==================== SEND EMAIL ====================

    public EmailResponseDTO sendEmail(Long senderId, SendEmailRequest request) {
        // Validate sender permissions
        boolean isHead = isHeadDirector(senderId);
        boolean isHR = isHRDirector(senderId);
        boolean isDir = isDirector(senderId);
        boolean isMgr = isManager(senderId);

        if (!isHead && !isHR && !isDir && !isMgr) {
            throw new RuntimeException("Access denied: You don't have permission to send emails");
        }

        // Get recipients
        List<User> recipients = userRepository.findAllById(request.getUserIds());
        if (recipients.isEmpty()) {
            throw new RuntimeException("No valid recipients found");
        }

        // Validate recipient access based on sender role
        List<User> allowedRecipients = new ArrayList<>();
        for (User recipient : recipients) {
            if (canSendTo(senderId, recipient.getUserId(), isHead, isHR, isDir, isMgr)) {
                allowedRecipients.add(recipient);
            }
        }

        if (allowedRecipients.isEmpty()) {
            throw new RuntimeException("You don't have permission to send emails to selected recipients");
        }

        // Send emails
        int successCount = 0;
        int failedCount = 0;
        List<String> failedEmails = new ArrayList<>();

        User sender = userRepository.findById(senderId).get();
        String senderName = sender.getName();
        String senderRole = getRole(senderId);

        for (User recipient : allowedRecipients) {
            try {
                String htmlContent = buildEmailContent(senderName, senderRole, recipient.getName(), request);
                emailService.sendEmail(recipient.getEmail(), request.getSubject(), htmlContent);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                failedEmails.add(recipient.getEmail());
                System.out.println("Failed to send email to: " + recipient.getEmail() + " - " + e.getMessage());
            }
        }

        return EmailResponseDTO.builder()
                .totalRecipients(allowedRecipients.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .failedEmails(failedEmails)
                .message("Email sent to " + successCount + " recipients")
                .build();
    }

    private boolean canSendTo(Long senderId, Long recipientId, boolean isHead, boolean isHR, boolean isDir, boolean isMgr) {
        if (isHead) return true; // Head Director can send to ANYONE

        if (isHR) return true; // HR Director can send to ANYONE

        if (isDir) {
            // Director can send to employees of same department + consultants
            EmployeeDetails senderEmp = employeeDetailsRepository.findByUserUserId(senderId).orElse(null);
            if (senderEmp == null || senderEmp.getDepartmentRef() == null) return false;

            Long senderDeptId = senderEmp.getDepartmentRef().getDepartmentId();

            // Check if recipient is employee of same department
            EmployeeDetails recipientEmp = employeeDetailsRepository.findByUserUserId(recipientId).orElse(null);
            if (recipientEmp != null && recipientEmp.getDepartmentRef() != null) {
                return senderDeptId.equals(recipientEmp.getDepartmentRef().getDepartmentId());
            }

            // Check if recipient is consultant of same department
            List<ConsultantDetails> consultant = consultantDetailsRepository.findByUserUserId(recipientId);
            if (!consultant.isEmpty() && consultant.get(0).getDepartment() != null) {
                return senderDeptId.equals(consultant.get(0).getDepartment().getDepartmentId());
            }
            return false;
        }

        if (isMgr) {
            // Manager can send to consultants of same department
            EmployeeDetails senderEmp = employeeDetailsRepository.findByUserUserId(senderId).orElse(null);
            if (senderEmp == null || senderEmp.getDepartmentRef() == null) return false;

            Long senderDeptId = senderEmp.getDepartmentRef().getDepartmentId();
            List<ConsultantDetails> consultant = consultantDetailsRepository.findByUserUserId(recipientId);
            if (!consultant.isEmpty() && consultant.get(0).getDepartment() != null) {
                return senderDeptId.equals(consultant.get(0).getDepartment().getDepartmentId());
            }
            return false;
        }

        return false;
    }

    private String getRole(Long userId) {
        if (isHeadDirector(userId)) return "Head Director";
        if (isHRDirector(userId)) return "HR Director";
        if (isDirector(userId)) return "Director";
        if (isManager(userId)) return "Manager";
        return "User";
    }

    private String buildEmailContent(String senderName, String senderRole, String recipientName, SendEmailRequest request) {
        String messageTypeStyle = getMessageTypeStyle(request.getMessageType());
        String messageTypeIcon = getMessageTypeIcon(request.getMessageType());

        // ✅ Message body ke andar link clickable banao
        String safeMessage = EmailService.makeLinksClickable(request.getMessage() != null ? request.getMessage() : "");

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; background: #f0f2f5; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                    .header { background: %s; color: white; padding: 35px 30px; text-align: center; }
                    .header h2 { margin: 0; font-size: 24px; letter-spacing: 0.5px; }
                    .header p { margin: 8px 0 0; color: rgba(255,255,255,0.95); font-size: 13px; }
                    .content { padding: 35px 30px; background: white; }
                    .message-box { background: #f7fafc; padding: 22px; border-radius: 12px; margin: 20px 0; border-left: 5px solid %s; }
                    .message-box p { margin: 0; color: #2d3748; font-size: 15px; line-height: 1.7; }
                    .message-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                    .sender-info { background: #f0f4ff; padding: 16px 20px; border-radius: 10px; margin-top: 20px; }
                    .sender-info p { margin: 6px 0; color: #4a5568; font-size: 13px; }
                    .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                    .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header" style="background: %s;">
                        <h2>%s %s</h2>
                        <p>Softmax Technologies and IT Services</p>
                    </div>
                    <div class="content">
                        <p style="color:#2d3748;font-size:15px;">Dear <strong>%s</strong>,</p>
                        <div class="message-box" style="border-left-color: %s;">
                            <p>%s</p>
                        </div>
                        <div class="sender-info">
                            <p><strong>👤 Sent by:</strong> %s (%s)</p>
                            <p><strong>⏰ Sent at:</strong> %s</p>
                        </div>
                    </div>
                    <div class="footer">
                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                        <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                        <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Enterprise Communication System</p>
                        <p>This is an automated message, please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                getMessageTypeStyle(request.getMessageType()),
                getMessageTypeStyle(request.getMessageType()),
                getMessageTypeStyle(request.getMessageType()),
                messageTypeIcon,
                request.getMessageType() != null ? request.getMessageType() : "Message",
                recipientName,
                getMessageTypeStyle(request.getMessageType()),
                safeMessage,
                senderName,
                senderRole,
                LocalDateTime.now().format(formatter)
        );
    }

    private String getMessageTypeStyle(String type) {
        if (type == null) return "#4CAF50";
        switch (type) {
            case "ALERT": return "#f44336";
            case "TASK": return "#2196F3";
            case "MEETING": return "#FF9800";
            default: return "#4CAF50";
        }
    }

    private String getMessageTypeIcon(String type) {
        if (type == null) return "📧";
        switch (type) {
            case "ALERT": return "⚠️";
            case "TASK": return "📋";
            case "MEETING": return "📅";
            default: return "📧";
        }
    }
}










//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.SendEmailRequest;
//import com.softmax.shardaos.dto.output.EmailRecipientDTO;
//import com.softmax.shardaos.dto.output.EmailResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class EmailMessagingService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private ConsultantDetailsRepository consultantDetailsRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//
//    // ==================== ROLE VALIDATION ====================
//
//    public boolean isManager(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
//                .filter(ut -> "MANAGER".equals(ut.getStype()))
//                .isPresent();
//    }
//
//    public boolean isDirector(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
//                .filter(ut -> "DIRECTOR".equals(ut.getStype()))
//                .isPresent();
//    }
//
//    public boolean isHRDirector(Long userId) {
//        if (!isDirector(userId)) return false;
//        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 2L;
//    }
//
//    public boolean isHeadDirector(Long userId) {
//        if (!isDirector(userId)) return false;
//        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 1L;
//    }
//
//    // ==================== GET RECIPIENTS BY ROLE ====================
//
//    // Get all consultants
//    public List<EmailRecipientDTO> getAllConsultants() {
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
//        return consultants.stream().map(c -> EmailRecipientDTO.builder()
//                .userId(c.getUser().getUserId())
//                .name(c.getUser().getName())
//                .email(c.getUser().getEmail())
//                .role("CONSULTANT")
//                .department(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
//                .build()).collect(Collectors.toList());
//    }
//
//    // Get all employees
//    public List<EmailRecipientDTO> getAllEmployees() {
//        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//        return employees.stream().map(e -> EmailRecipientDTO.builder()
//                .userId(e.getUser().getUserId())
//                .name(e.getUser().getName())
//                .email(e.getUser().getEmail())
//                .role("EMPLOYEE")
//                .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                .build()).collect(Collectors.toList());
//    }
//
//    // Get all managers
//    public List<EmailRecipientDTO> getAllManagers() {
//        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//        return employees.stream()
//                .filter(e -> isManager(e.getUser().getUserId()))
//                .map(e -> EmailRecipientDTO.builder()
//                        .userId(e.getUser().getUserId())
//                        .name(e.getUser().getName())
//                        .email(e.getUser().getEmail())
//                        .role("MANAGER")
//                        .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                        .build())
//                .collect(Collectors.toList());
//    }
//
//    // Get all directors
//    public List<EmailRecipientDTO> getAllDirectors() {
//        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//        return employees.stream()
//                .filter(e -> isDirector(e.getUser().getUserId()))
//                .map(e -> EmailRecipientDTO.builder()
//                        .userId(e.getUser().getUserId())
//                        .name(e.getUser().getName())
//                        .email(e.getUser().getEmail())
//                        .role("DIRECTOR")
//                        .department(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                        .build())
//                .collect(Collectors.toList());
//    }
//
//    // Get all users (for Head Director)
//    public List<EmailRecipientDTO> getAllUsers() {
//        List<User> users = userRepository.findAll();
//        return users.stream().map(u -> {
//            List<UserType> types = userTypeRepository.findByUserUserId(u.getUserId());
//            String role = types.isEmpty() ? "USER" : types.get(0).getPtype();
//            return EmailRecipientDTO.builder()
//                    .userId(u.getUserId())
//                    .name(u.getName())
//                    .email(u.getEmail())
//                    .role(role)
//                    .department(null)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    // Get consultants of manager's department
//    public List<EmailRecipientDTO> getMyDepartmentConsultants(Long managerId) {
//        EmployeeDetails manager = employeeDetailsRepository.findByUserUserId(managerId).orElse(null);
//        if (manager == null || manager.getDepartmentRef() == null) return new ArrayList<>();
//
//        Long deptId = manager.getDepartmentRef().getDepartmentId();
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(deptId);
//
//        return consultants.stream().map(c -> EmailRecipientDTO.builder()
//                .userId(c.getUser().getUserId())
//                .name(c.getUser().getName())
//                .email(c.getUser().getEmail())
//                .role("CONSULTANT")
//                .department(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
//                .build()).collect(Collectors.toList());
//    }
//
//    // ==================== SEND EMAIL ====================
//
//    public EmailResponseDTO sendEmail(Long senderId, SendEmailRequest request) {
//        // Validate sender permissions
//        boolean isHead = isHeadDirector(senderId);
//        boolean isHR = isHRDirector(senderId);
//        boolean isDir = isDirector(senderId);
//        boolean isMgr = isManager(senderId);
//
//        if (!isHead && !isHR && !isDir && !isMgr) {
//            throw new RuntimeException("Access denied: You don't have permission to send emails");
//        }
//
//        // Get recipients
//        List<User> recipients = userRepository.findAllById(request.getUserIds());
//        if (recipients.isEmpty()) {
//            throw new RuntimeException("No valid recipients found");
//        }
//
//        // Validate recipient access based on sender role
//        List<User> allowedRecipients = new ArrayList<>();
//        for (User recipient : recipients) {
//            if (canSendTo(senderId, recipient.getUserId(), isHead, isHR, isDir, isMgr)) {
//                allowedRecipients.add(recipient);
//            }
//        }
//
//        if (allowedRecipients.isEmpty()) {
//            throw new RuntimeException("You don't have permission to send emails to selected recipients");
//        }
//
//        // Send emails
//        int successCount = 0;
//        int failedCount = 0;
//        List<String> failedEmails = new ArrayList<>();
//
//        User sender = userRepository.findById(senderId).get();
//        String senderName = sender.getName();
//        String senderRole = getRole(senderId);
//
//        for (User recipient : allowedRecipients) {
//            try {
//                String htmlContent = buildEmailContent(senderName, senderRole, recipient.getName(), request);
//                emailService.sendEmail(recipient.getEmail(), request.getSubject(), htmlContent);
//                successCount++;
//            } catch (Exception e) {
//                failedCount++;
//                failedEmails.add(recipient.getEmail());
//                System.out.println("Failed to send email to: " + recipient.getEmail() + " - " + e.getMessage());
//            }
//        }
//
//        return EmailResponseDTO.builder()
//                .totalRecipients(allowedRecipients.size())
//                .successCount(successCount)
//                .failedCount(failedCount)
//                .failedEmails(failedEmails)
//                .message("Email sent to " + successCount + " recipients")
//                .build();
//    }
//
//    private boolean canSendTo(Long senderId, Long recipientId, boolean isHead, boolean isHR, boolean isDir, boolean isMgr) {
//        if (isHead) return true; // Head Director can send to ANYONE
//
//        if (isHR) return true; // HR Director can send to ANYONE
//
//        if (isDir) {
//            // Director can send to employees of same department + consultants
//            EmployeeDetails senderEmp = employeeDetailsRepository.findByUserUserId(senderId).orElse(null);
//            if (senderEmp == null || senderEmp.getDepartmentRef() == null) return false;
//
//            Long senderDeptId = senderEmp.getDepartmentRef().getDepartmentId();
//
//            // Check if recipient is employee of same department
//            EmployeeDetails recipientEmp = employeeDetailsRepository.findByUserUserId(recipientId).orElse(null);
//            if (recipientEmp != null && recipientEmp.getDepartmentRef() != null) {
//                return senderDeptId.equals(recipientEmp.getDepartmentRef().getDepartmentId());
//            }
//
//            // Check if recipient is consultant of same department
//            List<ConsultantDetails> consultant = consultantDetailsRepository.findByUserUserId(recipientId);
//            if (!consultant.isEmpty() && consultant.get(0).getDepartment() != null) {
//                return senderDeptId.equals(consultant.get(0).getDepartment().getDepartmentId());
//            }
//            return false;
//        }
//
//        if (isMgr) {
//            // Manager can send to consultants of same department
//            EmployeeDetails senderEmp = employeeDetailsRepository.findByUserUserId(senderId).orElse(null);
//            if (senderEmp == null || senderEmp.getDepartmentRef() == null) return false;
//
//            Long senderDeptId = senderEmp.getDepartmentRef().getDepartmentId();
//            List<ConsultantDetails> consultant = consultantDetailsRepository.findByUserUserId(recipientId);
//            if (!consultant.isEmpty() && consultant.get(0).getDepartment() != null) {
//                return senderDeptId.equals(consultant.get(0).getDepartment().getDepartmentId());
//            }
//            return false;
//        }
//
//        return false;
//    }
//
//    private String getRole(Long userId) {
//        if (isHeadDirector(userId)) return "Head Director";
//        if (isHRDirector(userId)) return "HR Director";
//        if (isDirector(userId)) return "Director";
//        if (isManager(userId)) return "Manager";
//        return "User";
//    }
//
//    private String buildEmailContent(String senderName, String senderRole, String recipientName, SendEmailRequest request) {
//        String messageTypeStyle = getMessageTypeStyle(request.getMessageType());
//        String messageTypeIcon = getMessageTypeIcon(request.getMessageType());
//
//        return String.format("""
//            <!DOCTYPE html>
//            <html>
//            <head>
//                <style>
//                    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
//                    .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 12px; background: #f9f9f9; }
//                    .header { background: %s; color: white; padding: 15px; text-align: center; border-radius: 8px 8px 0 0; }
//                    .content { padding: 25px; background: white; border-radius: 8px; margin-top: 10px; }
//                    .message-box { background: #f0f0f0; padding: 15px; border-radius: 8px; margin: 15px 0; border-left: 4px solid %s; }
//                    .footer { font-size: 12px; color: #888; text-align: center; margin-top: 20px; padding-top: 15px; border-top: 1px solid #e0e0e0; }
//                    .badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; }
//                    .badge-info { background: #e3f2fd; color: #1976d2; }
//                    .badge-alert { background: #ffebee; color: #c62828; }
//                    .badge-task { background: #e8f5e9; color: #2e7d32; }
//                    .badge-meeting { background: #fff3e0; color: #ef6c00; }
//                </style>
//            </head>
//            <body>
//                <div class="container">
//                    <div class="header" style="background: %s;">
//                        <h2 style="margin: 0;">%s %s</h2>
//                    </div>
//                    <div class="content">
//                        <p>Dear <strong>%s</strong>,</p>
//                        <div class="message-box" style="border-left-color: %s;">
//                            <p style="margin: 0;">%s</p>
//                        </div>
//                        <p><strong>Sent by:</strong> %s (%s)</p>
//                        <p><strong>Sent at:</strong> %s</p>
//                    </div>
//                    <div class="footer">
//                        <p>This is an automated message from ShardaOS. Please do not reply to this email.</p>
//                        <p>&copy; 2024 ShardaOS - Enterprise Communication System</p>
//                    </div>
//                </div>
//            </body>
//            </html>
//            """,
//                getMessageTypeStyle(request.getMessageType()),
//                getMessageTypeStyle(request.getMessageType()),
//                getMessageTypeStyle(request.getMessageType()),
//                messageTypeIcon,
//                request.getMessageType() != null ? request.getMessageType() : "Message",
//                recipientName,
//                getMessageTypeStyle(request.getMessageType()),
//                request.getMessage(),
//                senderName,
//                senderRole,
//                LocalDateTime.now().format(formatter)
//        );
//    }
//
//    private String getMessageTypeStyle(String type) {
//        if (type == null) return "#4CAF50";
//        switch (type) {
//            case "ALERT": return "#f44336";
//            case "TASK": return "#2196F3";
//            case "MEETING": return "#FF9800";
//            default: return "#4CAF50";
//        }
//    }
//
//    private String getMessageTypeIcon(String type) {
//        if (type == null) return "📧";
//        switch (type) {
//            case "ALERT": return "⚠️";
//            case "TASK": return "📋";
//            case "MEETING": return "📅";
//            default: return "📧";
//        }
//    }
//}