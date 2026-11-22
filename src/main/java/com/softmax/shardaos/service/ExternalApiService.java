package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ClientExternalRequestDTO;
import com.softmax.shardaos.dto.input.ConsultantExternalRequestDTO;
import com.softmax.shardaos.dto.input.ProjectInquiryExternalDTO;
import com.softmax.shardaos.dto.output.ExternalApiResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private UserTypeRepository userTypeRepository;
    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
    @Autowired private ConsultantStatusRepository consultantStatusRepository;
    @Autowired private ClientDetailsRepository clientDetailsRepository;
    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
    @Autowired private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final Long HEAD_DEPARTMENT_ID = 1L;

    // ==========================================================
    // API 1: CONSULTANT APPLICATION
    // ==========================================================
    @Transactional
    public ExternalApiResponseDTO createConsultantFromExternal(ConsultantExternalRequestDTO request) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("▶ [API-1] CONSULTANT APPLICATION — INCOMING REQUEST");
        log.info("   uid          : {}", request.getUid());
        log.info("   email        : {}", request.getEmail());
        log.info("   name         : {}", request.getName());
        log.info("   phoneNumber  : {}", request.getPhoneNumber());
        log.info("   gender       : {}", request.getGender());
        log.info("   dob          : {}", request.getDob());
        log.info("   photoUrl     : {}", request.getPhotoUrl());
        log.info("   position     : {}", request.getPosition());
        log.info("   skill        : {}", request.getSkill());
        log.info("   skillType    : {}", request.getSkillType());
        log.info("   stream       : {}", request.getStream());
        log.info("   experience   : {}", request.getExperience());
        log.info("   ctc          : {}", request.getCtc());
        log.info("   marks10      : {}", request.getMarks10());
        log.info("   marks12      : {}", request.getMarks12());
        log.info("   graduation   : {}", request.getGraduationMarks());
        log.info("   resumeLink   : {}", request.getResumeLink());
        log.info("   departmentId : {}", request.getDepartmentId());
        log.info("───────────────────────────────────────────────────────────");

        // 1) Upsert User
        User user = upsertUser(
                request.getUid(),
                request.getEmail(),
                request.getName(),
                request.getPhotoUrl(),
                request.getPhoneNumber(),
                request.getGender(),
                request.getDob()
        );
        log.info("[API-1] User upserted → userId={}, email={}", user.getUserId(), user.getEmail());

        // 2) UserType CONSULTANT
        Optional<UserType> existingType =
                userTypeRepository.findByUserUserIdAndPtype(user.getUserId(), "CONSULTANT");

        if (existingType.isEmpty()) {
            UserType consultantRole = new UserType();
            consultantRole.setUser(user);
            consultantRole.setPtype("CONSULTANT");
            consultantRole.setStype(null);
            consultantRole.setIsapproved("PENDING");
            consultantRole.setCreatedAt(LocalDateTime.now());
            userTypeRepository.save(consultantRole);
            log.info("[API-1] UserType CONSULTANT created → status=PENDING");
        } else {
            UserType ut = existingType.get();
            log.info("[API-1] UserType CONSULTANT already exists → isapproved={}", ut.getIsapproved());
            if (!"APPROVED".equalsIgnoreCase(ut.getIsapproved())) {
                ut.setIsapproved("PENDING");
                userTypeRepository.save(ut);
                log.info("[API-1] UserType CONSULTANT reset to PENDING");
            }
        }

        // 3) Upsert ConsultantDetails
        Optional<ConsultantDetails> existingDetails =
                consultantDetailsRepository.findFirstByUserUserId(user.getUserId());

        log.info("[API-1] ConsultantDetails {} for userId={}",
                existingDetails.isPresent() ? "FOUND (update)" : "NOT FOUND (create new)",
                user.getUserId());

        ConsultantDetails details = existingDetails.orElseGet(() -> {
            ConsultantDetails d = new ConsultantDetails();
            d.setUser(user);
            return d;
        });

        // ──────────────────────────────────────────────────────────
        // POSITION — entity unchanged, so position is persisted in
        // the existing ConsultantDetails.skillType column.
        // ──────────────────────────────────────────────────────────
        String positionValue = (request.getPosition() != null && !request.getPosition().isBlank())
                ? request.getPosition().trim()
                : null;

        if (positionValue != null) {
            details.setSkillType(positionValue);
            log.info("[API-1] position persisted into skillType column → {}", positionValue);
        } else {
            log.info("[API-1] position NOT provided — skillType left as-is");
        }

        details.setSkill(request.getSkill());
        details.setStream(request.getStream());
        details.setExperience(request.getExperience());
        details.setCtc(request.getCtc());
        details.setMarks10(request.getMarks10());
        details.setMarks12(request.getMarks12());
        details.setGraduationMarks(request.getGraduationMarks());

        if (request.getResumeLink() != null && !request.getResumeLink().isBlank()) {
            details.setResumeLink(request.getResumeLink());
        }
        if (request.getDepartmentId() != null && details.getDepartment() == null) {
            log.info("[API-1] departmentId provided ({}) but no department mapping configured",
                    request.getDepartmentId());
        }

        details = consultantDetailsRepository.save(details);
        log.info("[API-1] ConsultantDetails saved → consultantId={}, skillType(position)={}",
                details.getConsultantId(), details.getSkillType());

        // 4) ConsultantStatus: APPLIED — note includes position (audit trail)
        String statusNote = positionValue != null
                ? "Application received from careers.softmax.in | Position: " + positionValue
                : "Application received from careers.softmax.in";

        ConsultantStatus status = new ConsultantStatus();
        status.setConsultant(details);
        status.setStatus("APPLIED");
        status.setNote(statusNote);
        status.setDateTime(LocalDateTime.now());
        consultantStatusRepository.save(status);
        log.info("[API-1] ConsultantStatus saved → status=APPLIED, consultantId={}, note={}",
                details.getConsultantId(), statusNote);

        // 5) Welcome email — includes Position Applied
        try {
            String positionForEmail = positionValue != null ? positionValue : "Not specified";

            String subject = "📩 Application Received - Softmax Technologies and IT Services";

            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <style>
                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
                    .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
                    .header{background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;text-align:center;}
                    .header h1{color:#fff;margin:0;font-size:26px;}
                    .header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}
                    .content{padding:40px 35px;}
                    .info-box{background:#f0f4ff;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;}
                    .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
                    .badge{display:inline-block;background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;}
                    .pos-badge{display:inline-block;background:#eef2ff;color:#4338ca;padding:6px 14px;border-radius:8px;font-size:13px;font-weight:600;}
                    .btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#fff !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;}
                    .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
                    .footer a{color:#667eea;text-decoration:none;font-weight:600;}
                </style></head><body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Application Received!</h1>
                        <p>Softmax Technologies and IT Services</p>
                    </div>
                    <div class="content">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">Thank you for applying to Softmax Technologies. Your application has been received and is under review.</p>
                        <div class="info-box">
                            <p>👤 <strong>Name:</strong> %s</p>
                            <p>💼 <strong>Position Applied:</strong> <span class="pos-badge">%s</span></p>
                            <p>📧 <strong>Email:</strong> %s</p>
                            <p>🛠️ <strong>Skill:</strong> %s</p>
                            <p>📈 <strong>Experience:</strong> %s</p>
                            <p>📊 <strong>Status:</strong> <span class="badge">⏳ PENDING REVIEW</span></p>
                        </div>
                        <p style="color:#4a5568;">Our HR team will review your application and get back to you soon.</p>
                        <div style="text-align:center;">
                            <a href="https://nexus.softmax.in" class="btn">🚀 Go to Dashboard</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                    </div>
                </div>
                </body></html>
                """,
                    user.getName(),
                    user.getName(),
                    positionForEmail,
                    user.getEmail(),
                    request.getSkill() != null ? request.getSkill() : "Not specified",
                    request.getExperience() != null ? request.getExperience() : "Not specified"
            );
            emailService.sendEmail(user.getEmail(), subject, html);
            log.info("[API-1] Welcome email sent → {} | position={}", user.getEmail(), positionForEmail);
        } catch (Exception e) {
            System.out.println("Consultant welcome email failed: " + e.getMessage());
            log.error("[API-1] Welcome email FAILED → {} | {}", user.getEmail(), e.getMessage());
        }

        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
                .success(true)
                .message("Consultant application received successfully. Pending HR review.")
                .userId(user.getUserId())
                .consultantId(details.getConsultantId())
                .ptype("CONSULTANT")
                .isApproved("PENDING")
                .build();

        log.info("◀ [API-1] RESPONSE → success={}, userId={}, consultantId={}, ptype={}, isApproved={}",
                response.isSuccess(), response.getUserId(), response.getConsultantId(),
                response.getPtype(), response.getIsApproved());
        log.info("═══════════════════════════════════════════════════════════");

        return response;
    }

    // ==========================================================
    // API 2: CLIENT REGISTRATION (unchanged)
    // ==========================================================
    @Transactional
    public ExternalApiResponseDTO createClientFromExternal(ClientExternalRequestDTO request) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("▶ [API-2] CLIENT REGISTRATION — INCOMING REQUEST");
        log.info("   uid                 : {}", request.getUid());
        log.info("   email               : {}", request.getEmail());
        log.info("   name                : {}", request.getName());
        log.info("   phoneNumber         : {}", request.getPhoneNumber());
        log.info("   gender              : {}", request.getGender());
        log.info("   dob                 : {}", request.getDob());
        log.info("   photoUrl            : {}", request.getPhotoUrl());
        log.info("   companyName         : {}", request.getCompanyName());
        log.info("   companyMobileNumber : {}", request.getCompanyMobileNumber());
        log.info("   companyEmail        : {}", request.getCompanyEmail());
        log.info("   orgType             : {}", request.getOrgType());
        log.info("   clientType          : {}", request.getClientType());
        log.info("   clientWebsite       : {}", request.getClientWebsite());
        log.info("───────────────────────────────────────────────────────────");

        User user = upsertUser(
                request.getUid(),
                request.getEmail(),
                request.getName(),
                request.getPhotoUrl(),
                request.getPhoneNumber(),
                request.getGender(),
                request.getDob()
        );
        log.info("[API-2] User upserted → userId={}, email={}", user.getUserId(), user.getEmail());

        Optional<UserType> existingType =
                userTypeRepository.findByUserUserIdAndPtype(user.getUserId(), "CLIENT");

        if (existingType.isEmpty()) {
            UserType clientRole = new UserType();
            clientRole.setUser(user);
            clientRole.setPtype("CLIENT");
            clientRole.setStype(null);
            clientRole.setIsapproved("PENDING");
            clientRole.setCreatedAt(LocalDateTime.now());
            userTypeRepository.save(clientRole);
            log.info("[API-2] UserType CLIENT created → status=PENDING");
        } else {
            log.info("[API-2] UserType CLIENT already exists → isapproved={}",
                    existingType.get().getIsapproved());
        }

        Optional<ClientDetails> existingDetails =
                clientDetailsRepository.findByUserUserId(user.getUserId());

        log.info("[API-2] ClientDetails {} for userId={}",
                existingDetails.isPresent() ? "FOUND (update)" : "NOT FOUND (create new)",
                user.getUserId());

        ClientDetails client = existingDetails.orElseGet(() -> {
            ClientDetails c = new ClientDetails();
            c.setUser(user);
            return c;
        });

        client.setCompanyName(request.getCompanyName());
        client.setCompanyMobileNumber(request.getCompanyMobileNumber());
        client.setCompanyEmail(request.getCompanyEmail());
        client.setOrgType(request.getOrgType());
        client.setClientType(request.getClientType());
        client.setClientWebsite(request.getClientWebsite());

        if (client.getIsApproved() == null) client.setIsApproved(false);
        if (client.getClientStatus() == null || "REJECTED".equalsIgnoreCase(client.getClientStatus())) {
            client.setClientStatus("PENDING");
        }

        client = clientDetailsRepository.save(client);
        log.info("[API-2] ClientDetails saved → clientId={}, isApproved={}, clientStatus={}",
                client.getId(), client.getIsApproved(), client.getClientStatus());

        try {
            String subject = "🏢 Client Registration Received - Softmax Technologies and IT Services";
            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <style>
                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
                    .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
                    .header{background:linear-gradient(135deg,#2196F3 0%%,#1a237e 100%%);padding:40px 30px;text-align:center;}
                    .header h1{color:#fff;margin:0;font-size:26px;}
                    .content{padding:40px 35px;}
                    .info-box{background:#e3f2fd;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #2196F3;}
                    .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
                    .badge{display:inline-block;background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;}
                    .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
                </style></head><body>
                <div class="container">
                    <div class="header"><h1>🏢 Welcome, %s!</h1><p>Softmax Technologies and IT Services</p></div>
                    <div class="content">
                        <p style="color:#2d3748;font-size:15px;">Your client registration for <strong>%s</strong> has been received successfully.</p>
                        <div class="info-box">
                            <p>🏢 <strong>Company:</strong> %s</p>
                            <p>📧 <strong>Email:</strong> %s</p>
                            <p>📱 <strong>Mobile:</strong> %s</p>
                            <p>📊 <strong>Status:</strong> <span class="badge">⏳ PENDING APPROVAL</span></p>
                        </div>
                        <p style="color:#4a5568;">Our BDE team will review your registration shortly.</p>
                    </div>
                    <div class="footer">
                        <p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                    </div>
                </div>
                </body></html>
                """,
                    user.getName(), request.getCompanyName(), request.getCompanyName(),
                    request.getCompanyEmail(), request.getCompanyMobileNumber()
            );
            emailService.sendEmail(user.getEmail(), subject, html);
            log.info("[API-2] Welcome email sent → {}", user.getEmail());
        } catch (Exception e) {
            System.out.println("Client welcome email failed: " + e.getMessage());
            log.error("[API-2] Welcome email FAILED → {} | {}", user.getEmail(), e.getMessage());
        }

        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
                .success(true)
                .message("Client registration received. Pending BDE approval.")
                .userId(user.getUserId())
                .clientId(client.getId())
                .ptype("CLIENT")
                .isApproved("PENDING")
                .build();

        log.info("◀ [API-2] RESPONSE → success={}, userId={}, clientId={}, ptype={}, isApproved={}",
                response.isSuccess(), response.getUserId(), response.getClientId(),
                response.getPtype(), response.getIsApproved());
        log.info("═══════════════════════════════════════════════════════════");

        return response;
    }

    // ==========================================================
    // API 3: PROJECT INQUIRY (unchanged)
    // ==========================================================
    public ExternalApiResponseDTO submitProjectInquiry(ProjectInquiryExternalDTO request) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("▶ [API-3] PROJECT INQUIRY — INCOMING REQUEST");
        log.info("   name               : {}", request.getName());
        log.info("   email              : {}", request.getEmail());
        log.info("   phoneNumber        : {}", request.getPhoneNumber());
        log.info("   projectName        : {}", request.getProjectName());
        log.info("   projectType        : {}", request.getProjectType());
        log.info("   budgetRange        : {}", request.getBudgetRange());
        log.info("   timeline           : {}", request.getTimeline());
        log.info("   projectDescription : {}", request.getProjectDescription());
        log.info("───────────────────────────────────────────────────────────");

        String referenceId = "PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[API-3] Generated referenceId={}", referenceId);

        List<String> headDirectorEmails = new ArrayList<>();

        employeeDetailsRepository.findAll().stream()
                .filter(e -> e.getDepartmentRef() != null
                        && HEAD_DEPARTMENT_ID.equals(e.getDepartmentRef().getDepartmentId()))
                .map(EmployeeDetails::getUser)
                .filter(u -> userTypeRepository.findByUserUserId(u.getUserId()).stream()
                        .anyMatch(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype())))
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .forEach(headDirectorEmails::add);

        log.info("[API-3] Head Director emails resolved → {}", headDirectorEmails);
        if (headDirectorEmails.isEmpty()) {
            log.warn("[API-3] ⚠ No Head Director (EMP + DIRECTOR + dept {}) found — director email will NOT be sent.",
                    HEAD_DEPARTMENT_ID);
        }

        for (String to : headDirectorEmails) {
            try {
                sendProjectInquiryEmail(to, request, referenceId);
                log.info("[API-3] Inquiry email sent → {}", to);
            } catch (Exception e) {
                System.out.println("Inquiry mail failed for " + to + ": " + e.getMessage());
                log.error("[API-3] Inquiry email FAILED → {} | {}", to, e.getMessage());
            }
        }

        try {
            sendInquiryAcknowledgementEmail(request, referenceId);
            log.info("[API-3] Acknowledgement email sent → {}", request.getEmail());
        } catch (Exception e) {
            System.out.println("Inquiry ack mail failed: " + e.getMessage());
            log.error("[API-3] Acknowledgement email FAILED → {} | {}", request.getEmail(), e.getMessage());
        }

        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
                .success(true)
                .message("Your project inquiry has been submitted. Our team will contact you soon.")
                .referenceId(referenceId)
                .emailSentTo(String.join(",", headDirectorEmails))
                .build();

        log.info("◀ [API-3] RESPONSE → success={}, referenceId={}, emailSentTo={}",
                response.isSuccess(), response.getReferenceId(), response.getEmailSentTo());
        log.info("═══════════════════════════════════════════════════════════");

        return response;
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    private User upsertUser(String uid, String email, String name,
                            String photoUrl, String phoneNumber,
                            String gender, String dob) {

        log.debug("[upsertUser] Lookup → uid={}, email={}", uid, email);

        User user = null;

        if (uid != null && !uid.isBlank()) {
            user = userRepository.findByUid(uid).orElse(null);
            log.debug("[upsertUser] findByUid({}) → {}", uid, user != null ? "found userId=" + user.getUserId() : "not found");
        }
        if (user == null && email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
            log.debug("[upsertUser] findByEmail({}) → {}", email, user != null ? "found userId=" + user.getUserId() : "not found");
        }

        if (user == null) {
            user = new User();
            user.setUid(uid != null && !uid.isBlank()
                    ? uid
                    : "EXT_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000));
            user.setEmail(email);
            log.debug("[upsertUser] Creating NEW user → uid={}, email={}", user.getUid(), user.getEmail());
        } else {
            log.debug("[upsertUser] Updating EXISTING user → userId={}", user.getUserId());
        }

        if (name != null && !name.isBlank()) user.setName(name);
        if (photoUrl != null && !photoUrl.isBlank()) user.setPhotoUrl(photoUrl);
        if (phoneNumber != null && !phoneNumber.isBlank()) user.setPhoneNumber(phoneNumber);
        if (gender != null && !gender.isBlank()) user.setGender(gender);
        if (dob != null && !dob.isBlank()) {
            try {
                user.setDob(LocalDate.parse(dob));
            } catch (Exception ex) {
                log.warn("[upsertUser] Invalid dob='{}' — ignored", dob);
            }
        }

        if (user.getEmailVerified() == null) user.setEmailVerified(true);
        if (user.getIsProfileCompleted() == null) user.setIsProfileCompleted(false);

        User saved = userRepository.save(user);
        log.debug("[upsertUser] Saved → userId={}, email={}, emailVerified={}",
                saved.getUserId(), saved.getEmail(), saved.getEmailVerified());
        return saved;
    }

    private void sendProjectInquiryEmail(String to, ProjectInquiryExternalDTO req, String referenceId) {

        log.debug("[sendProjectInquiryEmail] Preparing mail → to={}, ref={}", to, referenceId);

        String subject = "📥 New Project Inquiry: " + req.getProjectName()
                + " [Ref: " + referenceId + "] - Softmax Technologies and IT Services";

        String safeDesc = EmailService.makeLinksClickable(
                req.getProjectDescription() != null ? req.getProjectDescription() : "No description provided");

        String html = String.format("""
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8">
            <style>
                body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
                .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
                .header{background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);padding:40px 30px;text-align:center;}
                .header h1{color:#fff;margin:0;font-size:26px;letter-spacing:0.5px;}
                .header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}
                .content{padding:40px 35px;}
                .ref-badge{background:linear-gradient(135deg,#11998e15,#38ef7d15);border-left:4px solid #11998e;padding:12px 20px;border-radius:12px;margin-bottom:22px;font-family:monospace;font-size:14px;}
                .ref-badge strong{color:#11998e;}
                .info-box{background:#e8f5e9;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #11998e;}
                .info-box p{margin:8px 0;color:#2d3748;font-size:14px;line-height:1.6;}
                .info-box a{color:#2196F3;text-decoration:underline;word-break:break-all;}
                .btn{display:inline-block;background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);color:#fff !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(17,153,142,0.35);}
                .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
                .footer a{color:#11998e;text-decoration:none;font-weight:600;}
            </style></head><body>
            <div class="container">
                <div class="header">
                    <h1>📥 New Project Inquiry</h1>
                    <p>Softmax Technologies and IT Services</p>
                </div>
                <div class="content">
                    <div class="ref-badge">
                        <strong>🔖 Reference ID:</strong> %s
                    </div>
                    <h2 style="color:#333;margin-top:0;">Dear Head Director,</h2>
                    <p style="color:#4a5568;font-size:15px;">A new project inquiry has been submitted through the client portal.</p>
                    <div class="info-box">
                        <p>📁 <strong>Project:</strong> %s</p>
                        <p>📝 <strong>Type:</strong> %s</p>
                        <p>💰 <strong>Budget Range:</strong> %s</p>
                        <p>⏰ <strong>Timeline:</strong> %s</p>
                        <p>📄 <strong>Description:</strong></p>
                        <p>%s</p>
                        <hr style="border:none;border-top:1px solid #c8e6c9;margin:14px 0;">
                        <p>👤 <strong>Name:</strong> %s</p>
                        <p>📧 <strong>Email:</strong> %s</p>
                        <p>📱 <strong>Phone:</strong> %s</p>
                        <p>📅 <strong>Submitted:</strong> %s</p>
                    </div>
                    <p style="color:#4a5568;">Reach out to the client directly or create their account via the BDE dashboard.</p>
                    <div style="text-align:center;">
                        <a href="mailto:%s" class="btn">📧 Reply to Client</a>
                    </div>
                </div>
                <div class="footer">
                    <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                    <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                    <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                    <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                </div>
            </div>
            </body></html>
            """,
                referenceId,
                req.getProjectName(),
                req.getProjectType() != null ? req.getProjectType() : "Not specified",
                req.getBudgetRange() != null ? req.getBudgetRange() : "Not specified",
                req.getTimeline() != null ? req.getTimeline() : "Not specified",
                safeDesc,
                req.getName(),
                req.getEmail(),
                req.getPhoneNumber() != null ? req.getPhoneNumber() : "Not provided",
                LocalDateTime.now().format(formatter),
                req.getEmail()
        );

        emailService.sendEmail(to, subject, html);
    }

    private void sendInquiryAcknowledgementEmail(ProjectInquiryExternalDTO req, String referenceId) {

        log.debug("[sendInquiryAcknowledgementEmail] Preparing ack mail → to={}, ref={}", req.getEmail(), referenceId);

        String subject = "✅ We've received your project inquiry [Ref: " + referenceId + "]";
        String html = String.format("""
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8">
            <style>
                body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
                .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
                .header{background:linear-gradient(135deg,#22c55e 0%%,#16a34a 100%%);padding:40px 30px;text-align:center;}
                .header h1{color:#fff;margin:0;font-size:26px;}
                .content{padding:40px 35px;}
                .info-box{background:#f0fdf4;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #22c55e;}
                .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
                .ref-code{font-family:monospace;font-size:18px;font-weight:bold;color:#16a34a;background:#f0fdf4;padding:10px 16px;border-radius:8px;display:inline-block;margin-top:10px;}
                .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
            </style></head><body>
            <div class="container">
                <div class="header"><h1>✅ Inquiry Received!</h1></div>
                <div class="content">
                    <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                    <p style="color:#4a5568;font-size:15px;">Thank you for your interest in Softmax Technologies. We have received your project inquiry for <strong>%s</strong>.</p>
                    <div class="info-box">
                        <p><strong>🔖 Your Reference ID:</strong></p>
                        <div class="ref-code">%s</div>
                        <p style="margin-top:14px;font-size:13px;">Please keep this ID for future correspondence.</p>
                    </div>
                    <p style="color:#4a5568;">Our team will review your inquiry and get back to you within <strong>24–48 business hours</strong>.</p>
                </div>
                <div class="footer">
                    <p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                </div>
            </div>
            </body></html>
            """,
                req.getName(),
                req.getProjectName(),
                referenceId
        );
        emailService.sendEmail(req.getEmail(), subject, html);
    }
}











//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ClientExternalRequestDTO;
//import com.softmax.shardaos.dto.input.ConsultantExternalRequestDTO;
//import com.softmax.shardaos.dto.input.ProjectInquiryExternalDTO;
//import com.softmax.shardaos.dto.output.ExternalApiResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//@Service
//public class ExternalApiService {
//
//    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);
//
//    @Autowired private UserRepository userRepository;
//    @Autowired private UserTypeRepository userTypeRepository;
//    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
//    @Autowired private ConsultantStatusRepository consultantStatusRepository;
//    @Autowired private ClientDetailsRepository clientDetailsRepository;
//    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
//    @Autowired private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    // ==========================================================
//    // API 1: CONSULTANT APPLICATION
//    // ==========================================================
//    @Transactional
//    public ExternalApiResponseDTO createConsultantFromExternal(ConsultantExternalRequestDTO request) {
//
//        log.info("═══════════════════════════════════════════════════════════");
//        log.info("▶ [API-1] CONSULTANT APPLICATION — INCOMING REQUEST");
//        log.info("   uid          : {}", request.getUid());
//        log.info("   email        : {}", request.getEmail());
//        log.info("   name         : {}", request.getName());
//        log.info("   phoneNumber  : {}", request.getPhoneNumber());
//        log.info("   gender       : {}", request.getGender());
//        log.info("   dob          : {}", request.getDob());
//        log.info("   photoUrl     : {}", request.getPhotoUrl());
//        log.info("   skill        : {}", request.getSkill());
//        log.info("   skillType    : {}", request.getSkillType());
//        log.info("   stream       : {}", request.getStream());
//        log.info("   experience   : {}", request.getExperience());
//        log.info("   ctc          : {}", request.getCtc());
//        log.info("   marks10      : {}", request.getMarks10());
//        log.info("   marks12      : {}", request.getMarks12());
//        log.info("   graduation   : {}", request.getGraduationMarks());
//        log.info("   resumeLink   : {}", request.getResumeLink());
//        log.info("   departmentId : {}", request.getDepartmentId());
//        log.info("───────────────────────────────────────────────────────────");
//
//        // 1) Upsert User
//        User user = upsertUser(
//                request.getUid(),
//                request.getEmail(),
//                request.getName(),
//                request.getPhotoUrl(),
//                request.getPhoneNumber(),
//                request.getGender(),
//                request.getDob()
//        );
//
//        log.info("[API-1] User upserted → userId={}, email={}", user.getUserId(), user.getEmail());
//
//        // 2) UserType CONSULTANT
//        Optional<UserType> existingType =
//                userTypeRepository.findByUserUserIdAndPtype(user.getUserId(), "CONSULTANT");
//
//        if (existingType.isEmpty()) {
//            UserType consultantRole = new UserType();
//            consultantRole.setUser(user);
//            consultantRole.setPtype("CONSULTANT");
//            consultantRole.setStype(null);
//            consultantRole.setIsapproved("PENDING");
//            consultantRole.setCreatedAt(LocalDateTime.now());
//            userTypeRepository.save(consultantRole);
//            log.info("[API-1] UserType CONSULTANT created → status=PENDING");
//        } else {
//            UserType ut = existingType.get();
//            log.info("[API-1] UserType CONSULTANT already exists → isapproved={}", ut.getIsapproved());
//            if (!"APPROVED".equalsIgnoreCase(ut.getIsapproved())) {
//                ut.setIsapproved("PENDING");
//                userTypeRepository.save(ut);
//                log.info("[API-1] UserType CONSULTANT reset to PENDING");
//            }
//        }
//
//        // 3) Upsert ConsultantDetails
//        Optional<ConsultantDetails> existingDetails =
//                consultantDetailsRepository.findFirstByUserUserId(user.getUserId());
//
//        log.info("[API-1] ConsultantDetails {} for userId={}",
//                existingDetails.isPresent() ? "FOUND (update)" : "NOT FOUND (create new)",
//                user.getUserId());
//
//        ConsultantDetails details = existingDetails.orElseGet(() -> {
//            ConsultantDetails d = new ConsultantDetails();
//            d.setUser(user);
//            return d;
//        });
//
//        details.setSkill(request.getSkill());
//        details.setSkillType(request.getSkillType());
//        details.setStream(request.getStream());
//        details.setExperience(request.getExperience());
//        details.setCtc(request.getCtc());
//        details.setMarks10(request.getMarks10());
//        details.setMarks12(request.getMarks12());
//        details.setGraduationMarks(request.getGraduationMarks());
//
//        if (request.getResumeLink() != null && !request.getResumeLink().isBlank()) {
//            details.setResumeLink(request.getResumeLink());
//        }
//        if (request.getDepartmentId() != null && details.getDepartment() == null) {
//            // optional — leave null if not found
//            log.info("[API-1] departmentId provided ({}) but no department mapping configured", request.getDepartmentId());
//        }
//
//        details = consultantDetailsRepository.save(details);
//        log.info("[API-1] ConsultantDetails saved → consultantId={}", details.getConsultantId());
//
//        // 4) ConsultantStatus: APPLIED
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(details);
//        status.setStatus("APPLIED");
//        status.setNote("Application received from careers.softmax.in");
//        status.setDateTime(LocalDateTime.now());
//        consultantStatusRepository.save(status);
//        log.info("[API-1] ConsultantStatus saved → status=APPLIED, consultantId={}", details.getConsultantId());
//
//        // 5) Welcome email
//        try {
//            String subject = "📩 Application Received - Softmax Technologies and IT Services";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8">
//                <style>
//                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
//                    .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
//                    .header{background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;text-align:center;}
//                    .header h1{color:#fff;margin:0;font-size:26px;}
//                    .header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}
//                    .content{padding:40px 35px;}
//                    .info-box{background:#f0f4ff;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;}
//                    .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
//                    .badge{display:inline-block;background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;}
//                    .btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#fff !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;}
//                    .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
//                    .footer a{color:#667eea;text-decoration:none;font-weight:600;}
//                </style></head><body>
//                <div class="container">
//                    <div class="header"><h1>🎉 Application Received!</h1><p>Softmax Technologies and IT Services</p></div>
//                    <div class="content">
//                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                        <p style="color:#4a5568;font-size:15px;">Thank you for applying to Softmax Technologies. Your application has been received and is under review.</p>
//                        <div class="info-box">
//                            <p>👤 <strong>Name:</strong> %s</p>
//                            <p>📧 <strong>Email:</strong> %s</p>
//                            <p>🛠️ <strong>Skill:</strong> %s</p>
//                            <p>💼 <strong>Experience:</strong> %s</p>
//                            <p>📊 <strong>Status:</strong> <span class="badge">⏳ PENDING REVIEW</span></p>
//                        </div>
//                        <p style="color:#4a5568;">Our HR team will review your application and get back to you soon.</p>
//                        <div style="text-align:center;">
//                            <a href="https://nexus.softmax.in" class="btn">🚀 Go to Dashboard</a>
//                        </div>
//                    </div>
//                    <div class="footer">
//                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                    </div>
//                </div>
//                </body></html>
//                """,
//                    user.getName(), user.getName(), user.getEmail(),
//                    request.getSkill() != null ? request.getSkill() : "Not specified",
//                    request.getExperience() != null ? request.getExperience() : "Not specified"
//            );
//            emailService.sendEmail(user.getEmail(), subject, html);
//            log.info("[API-1] Welcome email sent → {}", user.getEmail());
//        } catch (Exception e) {
//            System.out.println("Consultant welcome email failed: " + e.getMessage());
//            log.error("[API-1] Welcome email FAILED → {} | {}", user.getEmail(), e.getMessage());
//        }
//
//        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
//                .success(true)
//                .message("Consultant application received successfully. Pending HR review.")
//                .userId(user.getUserId())
//                .consultantId(details.getConsultantId())
//                .ptype("CONSULTANT")
//                .isApproved("PENDING")
//                .build();
//
//        log.info("◀ [API-1] RESPONSE → success={}, userId={}, consultantId={}, ptype={}, isApproved={}",
//                response.isSuccess(), response.getUserId(), response.getConsultantId(),
//                response.getPtype(), response.getIsApproved());
//        log.info("═══════════════════════════════════════════════════════════");
//
//        return response;
//    }
//
//    // ==========================================================
//    // API 2: CLIENT REGISTRATION (formal — DB insert)
//    // ==========================================================
//    @Transactional
//    public ExternalApiResponseDTO createClientFromExternal(ClientExternalRequestDTO request) {
//
//        log.info("═══════════════════════════════════════════════════════════");
//        log.info("▶ [API-2] CLIENT REGISTRATION — INCOMING REQUEST");
//        log.info("   uid                 : {}", request.getUid());
//        log.info("   email               : {}", request.getEmail());
//        log.info("   name                : {}", request.getName());
//        log.info("   phoneNumber         : {}", request.getPhoneNumber());
//        log.info("   gender              : {}", request.getGender());
//        log.info("   dob                 : {}", request.getDob());
//        log.info("   photoUrl            : {}", request.getPhotoUrl());
//        log.info("   companyName         : {}", request.getCompanyName());
//        log.info("   companyMobileNumber : {}", request.getCompanyMobileNumber());
//        log.info("   companyEmail        : {}", request.getCompanyEmail());
//        log.info("   orgType             : {}", request.getOrgType());
//        log.info("   clientType          : {}", request.getClientType());
//        log.info("   clientWebsite       : {}", request.getClientWebsite());
//        log.info("───────────────────────────────────────────────────────────");
//
//        // 1) Upsert User
//        User user = upsertUser(
//                request.getUid(),
//                request.getEmail(),
//                request.getName(),
//                request.getPhotoUrl(),
//                request.getPhoneNumber(),
//                request.getGender(),
//                request.getDob()
//        );
//
//        log.info("[API-2] User upserted → userId={}, email={}", user.getUserId(), user.getEmail());
//
//        // 2) UserType CLIENT
//        Optional<UserType> existingType =
//                userTypeRepository.findByUserUserIdAndPtype(user.getUserId(), "CLIENT");
//
//        if (existingType.isEmpty()) {
//            UserType clientRole = new UserType();
//            clientRole.setUser(user);
//            clientRole.setPtype("CLIENT");
//            clientRole.setStype(null);
//            clientRole.setIsapproved("PENDING");
//            clientRole.setCreatedAt(LocalDateTime.now());
//            userTypeRepository.save(clientRole);
//            log.info("[API-2] UserType CLIENT created → status=PENDING");
//        } else {
//            log.info("[API-2] UserType CLIENT already exists → isapproved={}",
//                    existingType.get().getIsapproved());
//        }
//
//        // 3) Upsert ClientDetails
//        Optional<ClientDetails> existingDetails =
//                clientDetailsRepository.findByUserUserId(user.getUserId());
//
//        log.info("[API-2] ClientDetails {} for userId={}",
//                existingDetails.isPresent() ? "FOUND (update)" : "NOT FOUND (create new)",
//                user.getUserId());
//
//        ClientDetails client = existingDetails.orElseGet(() -> {
//            ClientDetails c = new ClientDetails();
//            c.setUser(user);
//            return c;
//        });
//
//        client.setCompanyName(request.getCompanyName());
//        client.setCompanyMobileNumber(request.getCompanyMobileNumber());
//        client.setCompanyEmail(request.getCompanyEmail());
//        client.setOrgType(request.getOrgType());
//        client.setClientType(request.getClientType());
//        client.setClientWebsite(request.getClientWebsite());
//
//        if (client.getIsApproved() == null) client.setIsApproved(false);
//        if (client.getClientStatus() == null || "REJECTED".equalsIgnoreCase(client.getClientStatus())) {
//            client.setClientStatus("PENDING");
//        }
//
//        client = clientDetailsRepository.save(client);
//        log.info("[API-2] ClientDetails saved → clientId={}, isApproved={}, clientStatus={}",
//                client.getId(), client.getIsApproved(), client.getClientStatus());
//
//        // 4) Welcome email
//        try {
//            String subject = "🏢 Client Registration Received - Softmax Technologies and IT Services";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8">
//                <style>
//                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
//                    .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
//                    .header{background:linear-gradient(135deg,#2196F3 0%%,#1a237e 100%%);padding:40px 30px;text-align:center;}
//                    .header h1{color:#fff;margin:0;font-size:26px;}
//                    .content{padding:40px 35px;}
//                    .info-box{background:#e3f2fd;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #2196F3;}
//                    .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
//                    .badge{display:inline-block;background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;}
//                    .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
//                </style></head><body>
//                <div class="container">
//                    <div class="header"><h1>🏢 Welcome, %s!</h1><p>Softmax Technologies and IT Services</p></div>
//                    <div class="content">
//                        <p style="color:#2d3748;font-size:15px;">Your client registration for <strong>%s</strong> has been received successfully.</p>
//                        <div class="info-box">
//                            <p>🏢 <strong>Company:</strong> %s</p>
//                            <p>📧 <strong>Email:</strong> %s</p>
//                            <p>📱 <strong>Mobile:</strong> %s</p>
//                            <p>📊 <strong>Status:</strong> <span class="badge">⏳ PENDING APPROVAL</span></p>
//                        </div>
//                        <p style="color:#4a5568;">Our BDE team will review your registration shortly.</p>
//                    </div>
//                    <div class="footer">
//                        <p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                    </div>
//                </div>
//                </body></html>
//                """,
//                    user.getName(), request.getCompanyName(), request.getCompanyName(),
//                    request.getCompanyEmail(), request.getCompanyMobileNumber()
//            );
//            emailService.sendEmail(user.getEmail(), subject, html);
//            log.info("[API-2] Welcome email sent → {}", user.getEmail());
//        } catch (Exception e) {
//            System.out.println("Client welcome email failed: " + e.getMessage());
//            log.error("[API-2] Welcome email FAILED → {} | {}", user.getEmail(), e.getMessage());
//        }
//
//        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
//                .success(true)
//                .message("Client registration received. Pending BDE approval.")
//                .userId(user.getUserId())
//                .clientId(client.getId())
//                .ptype("CLIENT")
//                .isApproved("PENDING")
//                .build();
//
//        log.info("◀ [API-2] RESPONSE → success={}, userId={}, clientId={}, ptype={}, isApproved={}",
//                response.isSuccess(), response.getUserId(), response.getClientId(),
//                response.getPtype(), response.getIsApproved());
//        log.info("═══════════════════════════════════════════════════════════");
//
//        return response;
//    }
//
//    // ==========================================================
//    // API 3: PROJECT INQUIRY (anonymous — NO DB INSERT)
//    //         Mail ONLY to Head Director
//    // ==========================================================
//    public ExternalApiResponseDTO submitProjectInquiry(ProjectInquiryExternalDTO request) {
//
//        log.info("═══════════════════════════════════════════════════════════");
//        log.info("▶ [API-3] PROJECT INQUIRY — INCOMING REQUEST");
//        log.info("   name               : {}", request.getName());
//        log.info("   email              : {}", request.getEmail());
//        log.info("   phoneNumber        : {}", request.getPhoneNumber());
//        log.info("   projectName        : {}", request.getProjectName());
//        log.info("   projectType        : {}", request.getProjectType());
//        log.info("   budgetRange        : {}", request.getBudgetRange());
//        log.info("   timeline           : {}", request.getTimeline());
//        log.info("   projectDescription : {}", request.getProjectDescription());
//        log.info("───────────────────────────────────────────────────────────");
//
//        // 1) Generate reference ID (for tracking in email only — NOT stored)
//        String referenceId = "PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
//        log.info("[API-3] Generated referenceId={}", referenceId);
//
//        // 2) Find Head Director email (EMP + DIRECTOR + dept 1)
//        List<String> headDirectorEmails = new ArrayList<>();
//
//        employeeDetailsRepository.findAll().stream()
//                .filter(e -> e.getDepartmentRef() != null
//                        && HEAD_DEPARTMENT_ID.equals(e.getDepartmentRef().getDepartmentId()))
//                .map(EmployeeDetails::getUser)
//                .filter(u -> userTypeRepository.findByUserUserId(u.getUserId()).stream()
//                        .anyMatch(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype())))
//                .map(User::getEmail)
//                .filter(e -> e != null && !e.isBlank())
//                .forEach(headDirectorEmails::add);
//
//        log.info("[API-3] Head Director emails resolved → {}", headDirectorEmails);
//        if (headDirectorEmails.isEmpty()) {
//            log.warn("[API-3] ⚠ No Head Director (EMP + DIRECTOR + dept {}) found — director email will NOT be sent.",
//                    HEAD_DEPARTMENT_ID);
//        }
//
//        // 3) Send email (NO DB WRITE)
//        for (String to : headDirectorEmails) {
//            try {
//                sendProjectInquiryEmail(to, request, referenceId);
//                log.info("[API-3] Inquiry email sent → {}", to);
//            } catch (Exception e) {
//                System.out.println("Inquiry mail failed for " + to + ": " + e.getMessage());
//                log.error("[API-3] Inquiry email FAILED → {} | {}", to, e.getMessage());
//            }
//        }
//
//        // 4) Send auto-acknowledgement to the inquirer (nice UX)
//        try {
//            sendInquiryAcknowledgementEmail(request, referenceId);
//            log.info("[API-3] Acknowledgement email sent → {}", request.getEmail());
//        } catch (Exception e) {
//            System.out.println("Inquiry ack mail failed: " + e.getMessage());
//            log.error("[API-3] Acknowledgement email FAILED → {} | {}", request.getEmail(), e.getMessage());
//        }
//
//        ExternalApiResponseDTO response = ExternalApiResponseDTO.builder()
//                .success(true)
//                .message("Your project inquiry has been submitted. Our team will contact you soon.")
//                .referenceId(referenceId)
//                .emailSentTo(String.join(",", headDirectorEmails))
//                .build();
//
//        log.info("◀ [API-3] RESPONSE → success={}, referenceId={}, emailSentTo={}",
//                response.isSuccess(), response.getReferenceId(), response.getEmailSentTo());
//        log.info("═══════════════════════════════════════════════════════════");
//
//        return response;
//    }
//
//    // ==========================================================
//    // HELPERS
//    // ==========================================================
//
//    private User upsertUser(String uid, String email, String name,
//                            String photoUrl, String phoneNumber,
//                            String gender, String dob) {
//
//        log.debug("[upsertUser] Lookup → uid={}, email={}", uid, email);
//
//        User user = null;
//
//        if (uid != null && !uid.isBlank()) {
//            user = userRepository.findByUid(uid).orElse(null);
//            log.debug("[upsertUser] findByUid({}) → {}", uid, user != null ? "found userId=" + user.getUserId() : "not found");
//        }
//        if (user == null && email != null && !email.isBlank()) {
//            user = userRepository.findByEmail(email).orElse(null);
//            log.debug("[upsertUser] findByEmail({}) → {}", email, user != null ? "found userId=" + user.getUserId() : "not found");
//        }
//
//        if (user == null) {
//            user = new User();
//            user.setUid(uid != null && !uid.isBlank()
//                    ? uid
//                    : "EXT_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000));
//            user.setEmail(email);
//            log.debug("[upsertUser] Creating NEW user → uid={}, email={}", user.getUid(), user.getEmail());
//        } else {
//            log.debug("[upsertUser] Updating EXISTING user → userId={}", user.getUserId());
//        }
//
//        if (name != null && !name.isBlank()) user.setName(name);
//        if (photoUrl != null && !photoUrl.isBlank()) user.setPhotoUrl(photoUrl);
//        if (phoneNumber != null && !phoneNumber.isBlank()) user.setPhoneNumber(phoneNumber);
//        if (gender != null && !gender.isBlank()) user.setGender(gender);
//        if (dob != null && !dob.isBlank()) {
//            try {
//                user.setDob(LocalDate.parse(dob));
//            } catch (Exception ex) {
//                log.warn("[upsertUser] Invalid dob='{}' — ignored", dob);
//            }
//        }
//
//        if (user.getEmailVerified() == null) user.setEmailVerified(true);
//        if (user.getIsProfileCompleted() == null) user.setIsProfileCompleted(false);
//
//        User saved = userRepository.save(user);
//        log.debug("[upsertUser] Saved → userId={}, email={}, emailVerified={}",
//                saved.getUserId(), saved.getEmail(), saved.getEmailVerified());
//        return saved;
//    }
//
//    private void sendProjectInquiryEmail(String to, ProjectInquiryExternalDTO req, String referenceId) {
//
//        log.debug("[sendProjectInquiryEmail] Preparing mail → to={}, ref={}", to, referenceId);
//
//        String subject = "📥 New Project Inquiry: " + req.getProjectName()
//                + " [Ref: " + referenceId + "] - Softmax Technologies and IT Services";
//
//        String safeDesc = EmailService.makeLinksClickable(
//                req.getProjectDescription() != null ? req.getProjectDescription() : "No description provided");
//
//        String html = String.format("""
//            <!DOCTYPE html>
//            <html><head><meta charset="UTF-8">
//            <style>
//                body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
//                .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
//                .header{background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);padding:40px 30px;text-align:center;}
//                .header h1{color:#fff;margin:0;font-size:26px;letter-spacing:0.5px;}
//                .header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}
//                .content{padding:40px 35px;}
//                .ref-badge{background:linear-gradient(135deg,#11998e15,#38ef7d15);border-left:4px solid #11998e;padding:12px 20px;border-radius:12px;margin-bottom:22px;font-family:monospace;font-size:14px;}
//                .ref-badge strong{color:#11998e;}
//                .info-box{background:#e8f5e9;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #11998e;}
//                .info-box p{margin:8px 0;color:#2d3748;font-size:14px;line-height:1.6;}
//                .info-box a{color:#2196F3;text-decoration:underline;word-break:break-all;}
//                .btn{display:inline-block;background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);color:#fff !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(17,153,142,0.35);}
//                .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
//                .footer a{color:#11998e;text-decoration:none;font-weight:600;}
//            </style></head><body>
//            <div class="container">
//                <div class="header">
//                    <h1>📥 New Project Inquiry</h1>
//                    <p>Softmax Technologies and IT Services</p>
//                </div>
//                <div class="content">
//                    <div class="ref-badge">
//                        <strong>🔖 Reference ID:</strong> %s
//                    </div>
//                    <h2 style="color:#333;margin-top:0;">Dear Head Director,</h2>
//                    <p style="color:#4a5568;font-size:15px;">A new project inquiry has been submitted through the client portal.</p>
//                    <div class="info-box">
//                        <p>📁 <strong>Project:</strong> %s</p>
//                        <p>📝 <strong>Type:</strong> %s</p>
//                        <p>💰 <strong>Budget Range:</strong> %s</p>
//                        <p>⏰ <strong>Timeline:</strong> %s</p>
//                        <p>📄 <strong>Description:</strong></p>
//                        <p>%s</p>
//                        <hr style="border:none;border-top:1px solid #c8e6c9;margin:14px 0;">
//                        <p>👤 <strong>Name:</strong> %s</p>
//                        <p>📧 <strong>Email:</strong> %s</p>
//                        <p>📱 <strong>Phone:</strong> %s</p>
//                        <p>📅 <strong>Submitted:</strong> %s</p>
//                    </div>
//                    <p style="color:#4a5568;">Reach out to the client directly or create their account via the BDE dashboard.</p>
//                    <div style="text-align:center;">
//                        <a href="mailto:%s" class="btn">📧 Reply to Client</a>
//                    </div>
//                </div>
//                <div class="footer">
//                    <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                    <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                    <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                    <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                </div>
//            </div>
//            </body></html>
//            """,
//                referenceId,
//                req.getProjectName(),
//                req.getProjectType() != null ? req.getProjectType() : "Not specified",
//                req.getBudgetRange() != null ? req.getBudgetRange() : "Not specified",
//                req.getTimeline() != null ? req.getTimeline() : "Not specified",
//                safeDesc,
//                req.getName(),
//                req.getEmail(),
//                req.getPhoneNumber() != null ? req.getPhoneNumber() : "Not provided",
//                LocalDateTime.now().format(formatter),
//                req.getEmail()
//        );
//
//        emailService.sendEmail(to, subject, html);
//    }
//
//    private void sendInquiryAcknowledgementEmail(ProjectInquiryExternalDTO req, String referenceId) {
//
//        log.debug("[sendInquiryAcknowledgementEmail] Preparing ack mail → to={}, ref={}", req.getEmail(), referenceId);
//
//        String subject = "✅ We've received your project inquiry [Ref: " + referenceId + "]";
//        String html = String.format("""
//            <!DOCTYPE html>
//            <html><head><meta charset="UTF-8">
//            <style>
//                body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}
//                .container{max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}
//                .header{background:linear-gradient(135deg,#22c55e 0%%,#16a34a 100%%);padding:40px 30px;text-align:center;}
//                .header h1{color:#fff;margin:0;font-size:26px;}
//                .content{padding:40px 35px;}
//                .info-box{background:#f0fdf4;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #22c55e;}
//                .info-box p{margin:8px 0;color:#2d3748;font-size:14px;}
//                .ref-code{font-family:monospace;font-size:18px;font-weight:bold;color:#16a34a;background:#f0fdf4;padding:10px 16px;border-radius:8px;display:inline-block;margin-top:10px;}
//                .footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}
//            </style></head><body>
//            <div class="container">
//                <div class="header"><h1>✅ Inquiry Received!</h1></div>
//                <div class="content">
//                    <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                    <p style="color:#4a5568;font-size:15px;">Thank you for your interest in Softmax Technologies. We have received your project inquiry for <strong>%s</strong>.</p>
//                    <div class="info-box">
//                        <p><strong>🔖 Your Reference ID:</strong></p>
//                        <div class="ref-code">%s</div>
//                        <p style="margin-top:14px;font-size:13px;">Please keep this ID for future correspondence.</p>
//                    </div>
//                    <p style="color:#4a5568;">Our team will review your inquiry and get back to you within <strong>24–48 business hours</strong>.</p>
//                </div>
//                <div class="footer">
//                    <p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                </div>
//            </div>
//            </body></html>
//            """,
//                req.getName(),
//                req.getProjectName(),
//                referenceId
//        );
//        emailService.sendEmail(req.getEmail(), subject, html);
//    }
//}