package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ApproveClientRequest;
import com.softmax.shardaos.dto.input.UpdateClientRequest;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BDEApprovalService {

    @Autowired private ClientDetailsRepository clientDetailsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTypeRepository userTypeRepository;
    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProjectDetailsRepository projectRepository;
    @Autowired private EmailService emailService;

    private static final Long BDE_DEPARTMENT_ID = 3L;
    private static final Long HEAD_DEPARTMENT_ID = 1L;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");
    private final DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    // ==================== VALIDATION ====================

    public boolean isBDE(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        if (!isEmp) return false;
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (employee == null) return false;
        return employee.getDepartmentRef() != null && BDE_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
    }

    public boolean isBDEDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (employee == null) return false;
        return employee.getDepartmentRef() != null && BDE_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
    }

    public boolean isHeadDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (employee == null) return false;
        return employee.getDepartmentRef() != null && HEAD_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
    }

    private void validateAccess(Long userId) {
        if (!isBDEDirector(userId) && !isHeadDirector(userId)) {
            throw new RuntimeException("Access denied: Only BDE Director or Head Director can access this resource");
        }
    }

    // ==================== CLIENT MANAGEMENT ====================

    public List<ClientDetails> getPendingClients(Long userId) {
        validateAccess(userId);
        return clientDetailsRepository.findAll().stream()
                .filter(c -> c.getIsApproved() == null || !c.getIsApproved())
                .collect(Collectors.toList());
    }

    public List<ClientDetails> getAllClients(Long userId) {
        validateAccess(userId);
        return clientDetailsRepository.findAll();
    }

    public ClientDetails getClientById(Long userId, Long clientId) {
        validateAccess(userId);
        return clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }

    @Transactional
    public ClientDetails approveClient(Long userId, Long clientId, ApproveClientRequest request) {
        validateAccess(userId);
        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        client.setIsApproved(request.getIsApproved());
        client.setClientStatus(request.getIsApproved() ? "ACTIVE" : "REJECTED");

        userTypeRepository.findByUserUserIdAndPtype(client.getUser().getUserId(), "CLIENT")
                .ifPresent(ut -> {
                    ut.setIsapproved(request.getIsApproved() ? "APPROVED" : "REJECTED");
                    userTypeRepository.save(ut);
                });

        ClientDetails updated = clientDetailsRepository.save(client);

        sendClientApprovalEmail(client, request.getIsApproved(), request.getRejectionReason());

        return updated;
    }

    @Transactional
    public ClientDetails updateClient(Long userId, Long clientId, UpdateClientRequest request) {
        validateAccess(userId);
        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        if (request.getCompanyName() != null) client.setCompanyName(request.getCompanyName());
        if (request.getCompanyMobileNumber() != null) client.setCompanyMobileNumber(request.getCompanyMobileNumber());
        if (request.getCompanyEmail() != null) client.setCompanyEmail(request.getCompanyEmail());
        if (request.getOrgType() != null) client.setOrgType(request.getOrgType());
        if (request.getClientType() != null) client.setClientType(request.getClientType());
        if (request.getClientWebsite() != null) client.setClientWebsite(request.getClientWebsite());

        ClientDetails updated = clientDetailsRepository.save(client);

        sendClientUpdateEmail(client);

        return updated;
    }

    public List<EmployeeDetails> getBDETeam(Long userId) {
        validateAccess(userId);
        return employeeDetailsRepository.findAll().stream()
                .filter(e -> e.getDepartmentRef() != null && BDE_DEPARTMENT_ID.equals(e.getDepartmentRef().getDepartmentId()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardKPIs(Long userId) {
        validateAccess(userId);
        List<ClientDetails> allClients = clientDetailsRepository.findAll();
        List<ClientDetails> pendingClients = allClients.stream().filter(c -> c.getIsApproved() == null || !c.getIsApproved()).collect(Collectors.toList());
        List<ClientDetails> approvedClients = allClients.stream().filter(c -> c.getIsApproved() != null && c.getIsApproved()).collect(Collectors.toList());
        List<ClientDetails> rejectedClients = allClients.stream().filter(c -> c.getIsApproved() != null && !c.getIsApproved()).collect(Collectors.toList());
        List<ProjectDetails> bdProjects = projectRepository.findByDepartmentDepartmentId(BDE_DEPARTMENT_ID);
        List<EmployeeDetails> bdeTeam = getBDETeam(userId);
        double approvalRate = allClients.isEmpty() ? 0 : (approvedClients.size() * 100.0) / allClients.size();
        Map<String, Long> clientTypeDistribution = allClients.stream().filter(c -> c.getClientType() != null).collect(Collectors.groupingBy(ClientDetails::getClientType, Collectors.counting()));
        Map<String, Long> orgTypeDistribution = allClients.stream().filter(c -> c.getOrgType() != null).collect(Collectors.groupingBy(ClientDetails::getOrgType, Collectors.counting()));
        return Map.of(
                "totalClients", allClients.size(), "pendingApprovals", pendingClients.size(),
                "approvedClients", approvedClients.size(), "rejectedClients", rejectedClients.size(),
                "approvalRate", approvalRate, "totalBDEProjects", bdProjects.size(),
                "totalBDETeam", bdeTeam.size(), "clientTypeDistribution", clientTypeDistribution,
                "orgTypeDistribution", orgTypeDistribution
        );
    }

    // ==================== REUSABLE EMAIL TEMPLATE ====================
    private String buildEmail(String title, String preheader, String bodyContent) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>" + title + "</title></head>" +
                "<body style='margin:0;padding:0;background-color:#ffffff;font-family:Arial,Helvetica,sans-serif;color:#222222;'>" +
                "<div style='display:none;max-height:0;overflow:hidden;'>" + preheader + "</div>" +
                "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color:#ffffff;'>" +
                "<tr><td align='left' style='padding:32px 40px;'>" +
                "<div style='font-size:14px;line-height:1.7;color:#222222;'>" + bodyContent + "</div>" +
                "<div style='margin-top:32px;padding-top:20px;border-top:1px solid #e0e0e0;font-size:13px;color:#555555;line-height:1.7;'>" +
                "<p style='margin:0 0 4px 0;'>Regards,</p>" +
                "<p style='margin:0 0 4px 0;'><strong>Business Development Team</strong></p>" +
                "<p style='margin:0 0 4px 0;'>Softmax Technologies and IT Services</p>" +
                "<p style='margin:0 0 4px 0;'><a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a></p>" +
                "<p style='margin:0;'><a href='https://softmax.in' style='color:#1a73e8;text-decoration:none;'>softmax.in</a> | <a href='https://clients.softmax.in' style='color:#1a73e8;text-decoration:none;'>clients.softmax.in</a></p>" +
                "</div>" +
                "<div style='margin-top:20px;font-size:11px;color:#999999;line-height:1.6;'>" +
                "<p style='margin:0 0 4px 0;'>This is an automated message. Please do not reply to this email.</p>" +
                "<p style='margin:0;'>&copy; 2026 Softmax Technologies and IT Services. All rights reserved.</p>" +
                "</div>" +
                "</td></tr></table></body></html>";
    }

    private void sendClientApprovalEmail(ClientDetails client, boolean isApproved, String rejectionReason) {
        try {
            if (isApproved) {
                String subject = "Application Approved - Client Registration";
                String body = "<p style='margin:0 0 16px 0;'>Dear " + client.getUser().getName() + ",</p>" +
                        "<p style='margin:0 0 20px 0;'>We are pleased to inform you that your client registration application has been <strong>APPROVED</strong>. You can now access the client portal and begin collaborating with us.</p>" +
                        "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:20px 0;border-collapse:collapse;'>" +
                        "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company:</strong></td>" +
                        "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyName() + "</td></tr>" +
                        "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Email:</strong></td>" +
                        "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyEmail() + "</td></tr>" +
                        "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Mobile:</strong></td>" +
                        "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyMobileNumber() + "</td></tr>" +
                        "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Approved On:</strong></td>" +
                        "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + LocalDateTime.now().format(dateOnly) + "</td></tr>" +
                        "</table>" +
                        "<p style='margin:0 0 8px 0;'>With your client account, you can now:</p>" +
                        "<ul style='margin:0 0 20px 20px;padding:0;'>" +
                        "<li style='margin:6px 0;'>Create and manage project requests</li>" +
                        "<li style='margin:6px 0;'>Track project progress in real-time</li>" +
                        "<li style='margin:6px 0;'>Collaborate with assigned teams</li>" +
                        "<li style='margin:6px 0;'>Manage payments and invoices</li>" +
                        "</ul>" +
                        "<p style='margin:20px 0 0 0;'>You can access the client portal using the link below:</p>" +
                        "<p style='margin:24px 0 0 0;'><a href='https://clients.softmax.in/dashboard' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Client Portal Login</a></p>" +
                        "<p style='margin:20px 0 0 0;'>For any assistance, contact us at <a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a>.</p>";

                String html = buildEmail(subject, "Your client registration application has been approved.", body);
                emailService.sendEmail(client.getUser().getEmail(), subject, html);
            } else {
                String subject = "Application Update - Client Registration";
                String safeReason = EmailService.makeLinksClickable(rejectionReason != null ? rejectionReason : "Not specified");

                String body = "<p style='margin:0 0 16px 0;'>Dear " + client.getUser().getName() + ",</p>" +
                        "<p style='margin:0 0 20px 0;'>Thank you for your interest in <strong>Softmax Technologies and IT Services</strong>. After careful review of your application for <strong>" + client.getCompanyName() + "</strong>, we regret to inform you that we are unable to proceed with your registration at this time.</p>" +
                        "<p style='margin:0 0 8px 0;'><strong>Reason:</strong></p>" +
                        "<p style='margin:0 0 20px 0;'>" + safeReason + "</p>" +
                        "<p style='margin:0 0 16px 0;'>If you believe this is an error or would like to reapply after addressing the concerns above, please contact our Business Development team at <a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a>.</p>" +
                        "<p style='margin:0;'>We appreciate your interest and wish you the best.</p>";

                String html = buildEmail(subject, "Update on your client registration application.", body);
                emailService.sendEmail(client.getUser().getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendClientUpdateEmail(ClientDetails client) {
        try {
            String subject = "Profile Update Confirmation";
            String body = "<p style='margin:0 0 16px 0;'>Dear " + client.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>Your client profile has been updated by our Business Development team. Please review the updated information below:</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:20px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Email:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyEmail() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Mobile:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + client.getCompanyMobileNumber() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Updated On:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + LocalDateTime.now().format(formatter) + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:0 0 16px 0;'>If you did not request these changes or notice any discrepancy, please contact us immediately at <a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a>.</p>" +
                    "<p style='margin:20px 0 0 0;'>You can access the client portal using the link below:</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://clients.softmax.in/dashboard' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Client Portal Login</a></p>";

            String html = buildEmail(subject, "Your client profile has been updated.", body);
            emailService.sendEmail(client.getUser().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }
}












//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ApproveClientRequest;
//import com.softmax.shardaos.dto.input.UpdateClientRequest;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class BDEApprovalService {
//
//    @Autowired
//    private ClientDetailsRepository clientDetailsRepository;
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
//    private DepartmentRepository departmentRepository;
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    // Business Development Department ID = 3
//    private static final Long BDE_DEPARTMENT_ID = 3L;
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    // ==================== VALIDATION ====================
//
//    public boolean isBDE(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        if (!isEmp) return false;
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        if (employee == null) return false;
//
//        return employee.getDepartmentRef() != null &&
//                BDE_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
//    }
//
//    public boolean isBDEDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//
//        if (!isEmp || !isDirector) return false;
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        if (employee == null) return false;
//
//        return employee.getDepartmentRef() != null &&
//                BDE_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
//    }
//
//    public boolean isHeadDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//
//        if (!isEmp || !isDirector) return false;
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        if (employee == null) return false;
//
//        return employee.getDepartmentRef() != null &&
//                HEAD_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
//    }
//
//    private void validateAccess(Long userId) {
//        if (!isBDEDirector(userId) && !isHeadDirector(userId)) {
//            throw new RuntimeException("Access denied: Only BDE Director or Head Director can access this resource");
//        }
//    }
//
//    // ==================== CLIENT MANAGEMENT ====================
//
//    public List<ClientDetails> getPendingClients(Long userId) {
//        validateAccess(userId);
//        return clientDetailsRepository.findAll().stream()
//                .filter(c -> c.getIsApproved() == null || !c.getIsApproved())
//                .collect(Collectors.toList());
//    }
//
//    public List<ClientDetails> getAllClients(Long userId) {
//        validateAccess(userId);
//        return clientDetailsRepository.findAll();
//    }
//
//    public ClientDetails getClientById(Long userId, Long clientId) {
//        validateAccess(userId);
//        return clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//    }
//
//    @Transactional
//    public ClientDetails approveClient(Long userId, Long clientId, ApproveClientRequest request) {
//        validateAccess(userId);
//
//        ClientDetails client = clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//
//        client.setIsApproved(request.getIsApproved());
//        client.setClientStatus(request.getIsApproved() ? "ACTIVE" : "REJECTED");
//
//        userTypeRepository.findByUserUserIdAndPtype(client.getUser().getUserId(), "CLIENT")
//                .ifPresent(ut -> {
//                    ut.setIsapproved(request.getIsApproved() ? "APPROVED" : "REJECTED");
//                    userTypeRepository.save(ut);
//                });
//
//        ClientDetails updated = clientDetailsRepository.save(client);
//
//        // Send improved email
//        sendClientApprovalEmail(client, request.getIsApproved(), request.getRejectionReason());
//
//        return updated;
//    }
//
//    @Transactional
//    public ClientDetails updateClient(Long userId, Long clientId, UpdateClientRequest request) {
//        validateAccess(userId);
//
//        ClientDetails client = clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//
//        if (request.getCompanyName() != null) client.setCompanyName(request.getCompanyName());
//        if (request.getCompanyMobileNumber() != null) client.setCompanyMobileNumber(request.getCompanyMobileNumber());
//        if (request.getCompanyEmail() != null) client.setCompanyEmail(request.getCompanyEmail());
//        if (request.getOrgType() != null) client.setOrgType(request.getOrgType());
//        if (request.getClientType() != null) client.setClientType(request.getClientType());
//        if (request.getClientWebsite() != null) client.setClientWebsite(request.getClientWebsite());
//
//        ClientDetails updated = clientDetailsRepository.save(client);
//
//        sendClientUpdateEmail(client);
//
//        return updated;
//    }
//
//    // ==================== BDE TEAM ====================
//
//    public List<EmployeeDetails> getBDETeam(Long userId) {
//        validateAccess(userId);
//        return employeeDetailsRepository.findAll().stream()
//                .filter(e -> e.getDepartmentRef() != null &&
//                        BDE_DEPARTMENT_ID.equals(e.getDepartmentRef().getDepartmentId()))
//                .collect(Collectors.toList());
//    }
//
//    // ==================== DASHBOARD ====================
//
//    public Map<String, Object> getDashboardKPIs(Long userId) {
//        validateAccess(userId);
//
//        List<ClientDetails> allClients = clientDetailsRepository.findAll();
//        List<ClientDetails> pendingClients = allClients.stream()
//                .filter(c -> c.getIsApproved() == null || !c.getIsApproved())
//                .collect(Collectors.toList());
//        List<ClientDetails> approvedClients = allClients.stream()
//                .filter(c -> c.getIsApproved() != null && c.getIsApproved())
//                .collect(Collectors.toList());
//        List<ClientDetails> rejectedClients = allClients.stream()
//                .filter(c -> c.getIsApproved() != null && !c.getIsApproved())
//                .collect(Collectors.toList());
//
//        List<ProjectDetails> bdProjects = projectRepository.findByDepartmentDepartmentId(BDE_DEPARTMENT_ID);
//        List<EmployeeDetails> bdeTeam = getBDETeam(userId);
//
//        double approvalRate = allClients.isEmpty() ? 0 : (approvedClients.size() * 100.0) / allClients.size();
//
//        Map<String, Long> clientTypeDistribution = allClients.stream()
//                .filter(c -> c.getClientType() != null)
//                .collect(Collectors.groupingBy(ClientDetails::getClientType, Collectors.counting()));
//
//        Map<String, Long> orgTypeDistribution = allClients.stream()
//                .filter(c -> c.getOrgType() != null)
//                .collect(Collectors.groupingBy(ClientDetails::getOrgType, Collectors.counting()));
//
//        Map<String, Object> stats = Map.of(
//                "totalClients", allClients.size(),
//                "pendingApprovals", pendingClients.size(),
//                "approvedClients", approvedClients.size(),
//                "rejectedClients", rejectedClients.size(),
//                "approvalRate", approvalRate,
//                "totalBDEProjects", bdProjects.size(),
//                "totalBDETeam", bdeTeam.size(),
//                "clientTypeDistribution", clientTypeDistribution,
//                "orgTypeDistribution", orgTypeDistribution
//        );
//
//        return stats;
//    }
//
//    // ==================== IMPROVED EMAIL METHODS ====================
//
//    private void sendClientApprovalEmail(ClientDetails client, boolean isApproved, String rejectionReason) {
//        try {
//            String subject = isApproved ? "✅ Client Registration Approved - ShardaOS" : "❌ Client Registration Rejected - ShardaOS";
//
//            String html;
//            if (isApproved) {
//                html = String.format("""
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta charset="UTF-8">
//                        <style>
//                            body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f0f2f5; margin: 0; padding: 0; }
//                            .container { max-width: 550px; margin: 30px auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
//                            .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 35px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 28px; }
//                            .header p { color: rgba(255,255,255,0.9); margin: 8px 0 0; }
//                            .content { padding: 35px 30px; background: white; }
//                            .greeting { font-size: 16px; color: #333; margin-bottom: 20px; }
//                            .highlight { background: #e8f5e9; padding: 20px; border-radius: 12px; margin: 20px 0; border-left: 4px solid #11998e; }
//                            .btn { display: inline-block; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 15px; }
//                            .footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>🎉 ShardaOS</h1>
//                                <p>Enterprise Project Management System</p>
//                            </div>
//                            <div class="content">
//                                <div class="greeting">
//                                    <h2 style="margin:0 0 10px; color:#333;">Dear %s,</h2>
//                                    <p>We are pleased to inform you that your client registration request has been <strong style="color:#11998e;">APPROVED</strong>!</p>
//                                </div>
//                                <div class="highlight">
//                                    <p style="margin:0 0 10px;"><strong>📋 Registration Details:</strong></p>
//                                    <p style="margin:5px 0;">🏢 <strong>Company:</strong> %s</p>
//                                    <p style="margin:5px 0;">📧 <strong>Email:</strong> %s</p>
//                                    <p style="margin:5px 0;">📱 <strong>Mobile:</strong> %s</p>
//                                    <p style="margin:5px 0;">📅 <strong>Approved On:</strong> %s</p>
//                                </div>
//                                <p>You now have full access to:</p>
//                                <ul style="margin:15px 0; padding-left:20px;">
//                                    <li>✅ Create and manage projects</li>
//                                    <li>✅ Track project progress</li>
//                                    <li>✅ Collaborate with teams</li>
//                                    <li>✅ Access all client features</li>
//                                </ul>
//                                <div style="text-align: center;">
//                                    <a href="https://shardaos.com/client/dashboard" class="btn">🚀 Go to Dashboard</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p>© 2024 ShardaOS - All Rights Reserved</p>
//                                <p>This is an automated message, please do not reply.</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        client.getUser().getName(),
//                        client.getCompanyName(),
//                        client.getCompanyEmail(),
//                        client.getCompanyMobileNumber(),
//                        LocalDateTime.now().format(formatter)
//                );
//            } else {
//                html = String.format("""
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta charset="UTF-8">
//                        <style>
//                            body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f0f2f5; margin: 0; padding: 0; }
//                            .container { max-width: 550px; margin: 30px auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
//                            .header { background: linear-gradient(135deg, #ee5a24 0%, #ff4757 100%); padding: 35px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 28px; }
//                            .content { padding: 35px 30px; background: white; }
//                            .footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e0e0e0; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>ShardaOS</h1>
//                                <p>Enterprise Project Management System</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;">Dear %s,</h2>
//                                <p>After careful review, your client registration request for <strong>%s</strong> has been <strong style="color:#ff4757;">REJECTED</strong>.</p>
//                                <div style="background:#ffebee; padding:15px; border-radius:8px; margin:20px 0; border-left:4px solid #ff4757;">
//                                    <p style="margin:0;"><strong>📝 Rejection Reason:</strong></p>
//                                    <p style="margin:5px 0 0;">%s</p>
//                                </div>
//                                <p>If you believe this is an error, please contact our support team with your application details.</p>
//                                <p>We encourage you to review the requirements and reapply after making necessary changes.</p>
//                            </div>
//                            <div class="footer">
//                                <p>© 2024 ShardaOS - All Rights Reserved</p>
//                                <p>Need help? <a href="mailto:support@shardaos.com">Contact Support</a></p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        client.getUser().getName(),
//                        client.getCompanyName(),
//                        rejectionReason != null ? rejectionReason : "Not specified"
//                );
//            }
//
//            emailService.sendEmail(client.getUser().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendClientUpdateEmail(ClientDetails client) {
//        try {
//            String subject = "📝 Client Profile Updated - ShardaOS";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head>
//                    <meta charset="UTF-8">
//                    <style>
//                        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f0f2f5; margin: 0; padding: 0; }
//                        .container { max-width: 550px; margin: 30px auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
//                        .header { background: linear-gradient(135deg, #2196F3 0%, #6dd5ed 100%); padding: 35px 30px; text-align: center; }
//                        .header h1 { color: white; margin: 0; font-size: 28px; }
//                        .content { padding: 35px 30px; background: white; }
//                        .info-box { background: #f5f5f5; padding: 20px; border-radius: 12px; margin: 20px 0; }
//                        .footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e0e0e0; }
//                    </style>
//                </head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h1>📝 Profile Update</h1>
//                            <p>ShardaOS - Client Portal</p>
//                        </div>
//                        <div class="content">
//                            <h2 style="color:#333;">Dear %s,</h2>
//                            <p>Your client profile has been successfully updated by the administrator.</p>
//                            <div class="info-box">
//                                <p style="margin:0 0 10px;"><strong>📋 Updated Information:</strong></p>
//                                <p style="margin:5px 0;">🏢 <strong>Company:</strong> %s</p>
//                                <p style="margin:5px 0;">📧 <strong>Email:</strong> %s</p>
//                                <p style="margin:5px 0;">📱 <strong>Mobile:</strong> %s</p>
//                            </div>
//                            <p>If you did not request these changes, please contact support immediately.</p>
//                        </div>
//                        <div class="footer">
//                            <p>© 2024 ShardaOS - All Rights Reserved</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    client.getUser().getName(),
//                    client.getCompanyName(),
//                    client.getCompanyEmail(),
//                    client.getCompanyMobileNumber()
//            );
//            emailService.sendEmail(client.getUser().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//}