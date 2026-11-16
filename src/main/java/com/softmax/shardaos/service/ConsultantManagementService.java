package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ConsultantApplyRequest;
import com.softmax.shardaos.dto.input.ConsultantUpdateRequest;
import com.softmax.shardaos.dto.output.ConsultantApplicationResponse;
import com.softmax.shardaos.dto.output.ConsultantStatusHistoryDTO;
import com.softmax.shardaos.dto.output.UserDTO;
import com.softmax.shardaos.dto.output.RoleDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConsultantManagementService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ConsultantStatusRepository consultantStatusRepository;  // ✅ ADDED

    @Value("${file.upload.resumes:uploads/resumes/}")
    private String resumeUploadPath;

    @Transactional
    public ConsultantApplicationResponse applyForConsultant(Long userId, ConsultantApplyRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserType> existingTypes = userTypeRepository.findByUserUserId(userId);
        boolean alreadyConsultant = existingTypes.stream()
                .anyMatch(ut -> "CONSULTANT".equals(ut.getPtype()));

        if (alreadyConsultant) {
            throw new RuntimeException("User is already a consultant");
        }

        // Resume optional - null check
        String resumeLink = null;
        if (request.getResume() != null && !request.getResume().isEmpty()) {
            resumeLink = saveResume(request.getResume(), userId);
        }

        ConsultantDetails consultantDetails = new ConsultantDetails();
        consultantDetails.setUser(user);
        consultantDetails.setMarks10(request.getMarks10());
        consultantDetails.setMarks12(request.getMarks12());
        consultantDetails.setGraduationMarks(request.getGraduationMarks());
        consultantDetails.setStream(request.getStream());
        consultantDetails.setExperience(request.getExperience());
        consultantDetails.setSkill(request.getSkill());
        consultantDetails.setSkillType(request.getSkillType());
        consultantDetails.setResumeLink(resumeLink);

        consultantDetails = consultantDetailsRepository.save(consultantDetails);

        UserType userType = new UserType();
        userType.setUser(user);
        userType.setPtype("CONSULTANT");
        userType.setStype(null);
        userType.setIsapproved("PENDING");
        userType.setCreatedAt(LocalDateTime.now());

        userType = userTypeRepository.save(userType);

        String emailHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<style>" +
                "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}" +
                ".container{max-width:600px;margin:30px auto;background:white;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}" +
                ".header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:45px 30px;text-align:center;position:relative;}" +
                ".header h1{color:white;margin:0;font-size:26px;letter-spacing:0.5px;}" +
                ".header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}" +
                ".content{padding:40px 35px;}" +
                ".welcome-box{background:linear-gradient(135deg,#f0f4ff 0%,#e8eeff 100%);padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;}" +
                ".welcome-box p{margin:8px 0;color:#2d3748;font-size:14px;}" +
                ".steps-box{background:#f9fafb;padding:20px 24px;border-radius:12px;margin:22px 0;border:1px solid #e5e7eb;}" +
                ".steps-box p{margin:10px 0;color:#4a5568;font-size:14px;}" +
                ".step{display:flex;align-items:center;margin:10px 0;}" +
                ".step-num{background:#667eea;color:white;width:26px;height:26px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-weight:bold;font-size:13px;margin-right:12px;flex-shrink:0;}" +
                ".status-badge{display:inline-block;background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;margin-top:8px;}" +
                ".btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:white !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:20px;font-size:15px;box-shadow:0 6px 18px rgba(102,126,234,0.35);}" +
                ".footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}" +
                ".footer a{color:#667eea;text-decoration:none;font-weight:600;}" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🎉 Welcome " + user.getName() + "!</h1>" +
                "<p>Softmax Technologies and IT Services</p>" +
                "</div>" +
                "<div class='content'>" +
                "<p style='color:#2d3748;font-size:16px;font-weight:600;'>Thank you for registering as a consultant!</p>" +
                "<p style='color:#4a5568;font-size:15px;'>We're excited to have you on board. Your application has been successfully submitted.</p>" +
                "<div class='welcome-box'>" +
                "<p>👤 <strong>Name:</strong> " + user.getName() + "</p>" +
                "<p>📧 <strong>Email:</strong> " + user.getEmail() + "</p>" +
                "<p>🛠️ <strong>Skill:</strong> " + (request.getSkill() != null ? request.getSkill() : "Not specified") + "</p>" +
                "<p>💼 <strong>Experience:</strong> " + (request.getExperience() != null ? request.getExperience() : "Not specified") + "</p>" +
                "<p>📊 <strong>Status:</strong> <span class='status-badge'>⏳ PENDING REVIEW</span></p>" +
                "</div>" +
                "<div class='steps-box'>" +
                "<p style='font-weight:600;color:#2d3748;margin-bottom:12px;'>📋 What happens next?</p>" +
                "<div class='step'><span class='step-num'>1</span><span style='color:#4a5568;font-size:14px;'>Our HR team will review your application</span></div>" +
                "<div class='step'><span class='step-num'>2</span><span style='color:#4a5568;font-size:14px;'>You'll receive an update on your registered email</span></div>" +
                "<div class='step'><span class='step-num'>3</span><span style='color:#4a5568;font-size:14px;'>Once approved, you can start applying for projects</span></div>" +
                "</div>" +
                "<div style='text-align:center;'>" +
                "<a href='https://nexus.softmax.in' class='btn'>🚀 Go to Dashboard</a>" +
                "</div>" +
                "</div>" +
                "<div class='footer'>" +
                "<p style='margin:6px 0;'><strong>Softmax Technologies and IT Services</strong></p>" +
                "<p style='margin:6px 0;'>🌐 <a href='https://softmax.in'>softmax.in</a> | 💼 <a href='https://nexus.softmax.in'>nexus.softmax.in</a></p>" +
                "<p style='margin:6px 0;'>📧 <a href='mailto:softmaxdigitals@gmail.com'>softmaxdigitals@gmail.com</a></p>" +
                "<p style='margin-top:12px;'>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>" +
                "<p>This is an automated message, please do not reply.</p>" +
                "</div>" +
                "</div></body></html>";

        emailService.sendEmail(user.getEmail(), "Consultant Registration Submitted - Softmax Technologies and IT Services", emailHtml);

        // ✅ FIXED: UserDTO with ALL fields
        UserDTO userDTO = new UserDTO(
                user.getUserId(),
                user.getUid(),
                user.getName(),
                user.getEmail(),
                user.getPhotoUrl(),
                user.getEmailVerified(),
                user.getIsProfileCompleted(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getGender(),
                user.getDob(),
                user.getFcmToken(),
                user.getIsDglockerVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getProfileCreatedAt()
        );

        RoleDTO roleDTO = new RoleDTO(
                userType.getId(),
                userType.getPtype(),
                userType.getStype(),
                userType.getIsapproved(),
                userType.getCreatedAt()
        );

        return ConsultantApplicationResponse.builder()
                .message("Consultant application submitted successfully. Waiting for HR approval.")
                .user(userDTO)
                .consultantDetails(consultantDetails)
                .role(roleDTO)
                .build();
    }

    public ConsultantDetails getConsultantDetailsByUserId(Long userId) {
        List<ConsultantDetails> consultants = consultantDetailsRepository.findByUserUserId(userId);
        if (consultants.isEmpty()) {
            throw new RuntimeException("Consultant details not found");
        }
        return consultants.get(0);
    }

    @Transactional
    public ConsultantDetails updateConsultantDetails(Long userId, ConsultantUpdateRequest request) {

        ConsultantDetails consultantDetails = getConsultantDetailsByUserId(userId);

        if (request.getMarks10() != null) consultantDetails.setMarks10(request.getMarks10());
        if (request.getMarks12() != null) consultantDetails.setMarks12(request.getMarks12());
        if (request.getGraduationMarks() != null) consultantDetails.setGraduationMarks(request.getGraduationMarks());
        if (request.getStream() != null) consultantDetails.setStream(request.getStream());
        if (request.getExperience() != null) consultantDetails.setExperience(request.getExperience());
        if (request.getSkill() != null) consultantDetails.setSkill(request.getSkill());
        if (request.getSkillType() != null) consultantDetails.setSkillType(request.getSkillType());

        if (request.getResume() != null && !request.getResume().isEmpty()) {
            String resumeLink = saveResume(request.getResume(), userId);
            consultantDetails.setResumeLink(resumeLink);
        }

        return consultantDetailsRepository.save(consultantDetails);
    }

    private String saveResume(MultipartFile file, Long userId) {
        try {
            Path uploadPath = Paths.get(resumeUploadPath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "resume_" + userId + "_" + UUID.randomUUID().toString() + ".pdf";
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resume: " + e.getMessage());
        }
    }

    // ✅ NEW METHOD - Get consultant status history
    public List<ConsultantStatusHistoryDTO> getMyStatusHistory(Long userId) {
        ConsultantDetails consultant = getConsultantDetailsByUserId(userId);

        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultant.getConsultantId());

        return statusList.stream()
                .map(status -> ConsultantStatusHistoryDTO.builder()
                        .statusId(status.getStatusId())
                        .status(status.getStatus())
                        .note(status.getNote())
                        .dateTime(status.getDateTime())
                        .build())
                .collect(Collectors.toList());
    }

    public ConsultantStatusHistoryDTO getMyLatestStatus(Long userId) {
        ConsultantDetails consultant = getConsultantDetailsByUserId(userId);

        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultant.getConsultantId());

        if (statusList.isEmpty()) {
            return ConsultantStatusHistoryDTO.builder()
                    .statusId(null)
                    .status("PENDING")
                    .note("No status available")
                    .dateTime(LocalDateTime.now())
                    .build();
        }

        // Get latest status (first one after sorting by dateTime desc)
        ConsultantStatus latestStatus = statusList.stream()
                .max(Comparator.comparing(ConsultantStatus::getDateTime))
                .orElse(statusList.get(0));

        return ConsultantStatusHistoryDTO.builder()
                .statusId(latestStatus.getStatusId())
                .status(latestStatus.getStatus())
                .note(latestStatus.getNote())
                .dateTime(latestStatus.getDateTime())
                .build();
    }
}












//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ConsultantApplyRequest;
//import com.softmax.shardaos.dto.input.ConsultantUpdateRequest;
//import com.softmax.shardaos.dto.output.ConsultantApplicationResponse;
//import com.softmax.shardaos.dto.output.ConsultantStatusHistoryDTO;
//import com.softmax.shardaos.dto.output.UserDTO;
//import com.softmax.shardaos.dto.output.RoleDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.util.Comparator;
//import java.util.List;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@Service
//public class ConsultantManagementService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private ConsultantDetailsRepository consultantDetailsRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    @Autowired
//    private ConsultantStatusRepository consultantStatusRepository;  // ✅ ADDED
//
//    @Value("${file.upload.resumes:uploads/resumes/}")
//    private String resumeUploadPath;
//
//    @Transactional
//    public ConsultantApplicationResponse applyForConsultant(Long userId, ConsultantApplyRequest request) {
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        List<UserType> existingTypes = userTypeRepository.findByUserUserId(userId);
//        boolean alreadyConsultant = existingTypes.stream()
//                .anyMatch(ut -> "CONSULTANT".equals(ut.getPtype()));
//
//        if (alreadyConsultant) {
//            throw new RuntimeException("User is already a consultant");
//        }
//
//        // Resume optional - null check
//        String resumeLink = null;
//        if (request.getResume() != null && !request.getResume().isEmpty()) {
//            resumeLink = saveResume(request.getResume(), userId);
//        }
//
//        ConsultantDetails consultantDetails = new ConsultantDetails();
//        consultantDetails.setUser(user);
//        consultantDetails.setMarks10(request.getMarks10());
//        consultantDetails.setMarks12(request.getMarks12());
//        consultantDetails.setGraduationMarks(request.getGraduationMarks());
//        consultantDetails.setStream(request.getStream());
//        consultantDetails.setExperience(request.getExperience());
//        consultantDetails.setSkill(request.getSkill());
//        consultantDetails.setSkillType(request.getSkillType());
//        consultantDetails.setResumeLink(resumeLink);
//
//        consultantDetails = consultantDetailsRepository.save(consultantDetails);
//
//        UserType userType = new UserType();
//        userType.setUser(user);
//        userType.setPtype("CONSULTANT");
//        userType.setStype(null);
//        userType.setIsapproved("PENDING");
//        userType.setCreatedAt(LocalDateTime.now());
//
//        userType = userTypeRepository.save(userType);
//
//        String emailHtml = "<h2>Welcome " + user.getName() + "!</h2>" +
//                "<p>Thank you for registering as a consultant on ShardaOS!</p>" +
//                "<p>Your application is under review by our HR team.</p>";
//
//        emailService.sendEmail(user.getEmail(), "Consultant Registration Submitted - ShardaOS", emailHtml);
//
//        // ✅ FIXED: UserDTO with ALL fields
//        UserDTO userDTO = new UserDTO(
//                user.getUserId(),
//                user.getUid(),
//                user.getName(),
//                user.getEmail(),
//                user.getPhotoUrl(),
//                user.getEmailVerified(),
//                user.getIsProfileCompleted(),
//                user.getPhoneNumber(),
//                user.getAddress(),
//                user.getGender(),
//                user.getDob(),
//                user.getFcmToken(),
//                user.getIsDglockerVerified(),
//                user.getCreatedAt(),
//                user.getUpdatedAt(),
//                user.getProfileCreatedAt()
//        );
//
//        RoleDTO roleDTO = new RoleDTO(
//                userType.getId(),
//                userType.getPtype(),
//                userType.getStype(),
//                userType.getIsapproved(),
//                userType.getCreatedAt()
//        );
//
//        return ConsultantApplicationResponse.builder()
//                .message("Consultant application submitted successfully. Waiting for HR approval.")
//                .user(userDTO)
//                .consultantDetails(consultantDetails)
//                .role(roleDTO)
//                .build();
//    }
//
//    public ConsultantDetails getConsultantDetailsByUserId(Long userId) {
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByUserUserId(userId);
//        if (consultants.isEmpty()) {
//            throw new RuntimeException("Consultant details not found");
//        }
//        return consultants.get(0);
//    }
//
//    @Transactional
//    public ConsultantDetails updateConsultantDetails(Long userId, ConsultantUpdateRequest request) {
//
//        ConsultantDetails consultantDetails = getConsultantDetailsByUserId(userId);
//
//        if (request.getMarks10() != null) consultantDetails.setMarks10(request.getMarks10());
//        if (request.getMarks12() != null) consultantDetails.setMarks12(request.getMarks12());
//        if (request.getGraduationMarks() != null) consultantDetails.setGraduationMarks(request.getGraduationMarks());
//        if (request.getStream() != null) consultantDetails.setStream(request.getStream());
//        if (request.getExperience() != null) consultantDetails.setExperience(request.getExperience());
//        if (request.getSkill() != null) consultantDetails.setSkill(request.getSkill());
//        if (request.getSkillType() != null) consultantDetails.setSkillType(request.getSkillType());
//
//        if (request.getResume() != null && !request.getResume().isEmpty()) {
//            String resumeLink = saveResume(request.getResume(), userId);
//            consultantDetails.setResumeLink(resumeLink);
//        }
//
//        return consultantDetailsRepository.save(consultantDetails);
//    }
//
//    private String saveResume(MultipartFile file, Long userId) {
//        try {
//            Path uploadPath = Paths.get(resumeUploadPath);
//            if (!Files.exists(uploadPath)) {
//                Files.createDirectories(uploadPath);
//            }
//
//            String filename = "resume_" + userId + "_" + UUID.randomUUID().toString() + ".pdf";
//            Path filePath = uploadPath.resolve(filename);
//            Files.write(filePath, file.getBytes());
//
//            return filePath.toString();
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to save resume: " + e.getMessage());
//        }
//    }
//
//    // ✅ NEW METHOD - Get consultant status history
//    public List<ConsultantStatusHistoryDTO> getMyStatusHistory(Long userId) {
//        ConsultantDetails consultant = getConsultantDetailsByUserId(userId);
//
//        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultant.getConsultantId());
//
//        return statusList.stream()
//                .map(status -> ConsultantStatusHistoryDTO.builder()
//                        .statusId(status.getStatusId())
//                        .status(status.getStatus())
//                        .note(status.getNote())
//                        .dateTime(status.getDateTime())
//                        .build())
//                .collect(Collectors.toList());
//    }
//
//    public ConsultantStatusHistoryDTO getMyLatestStatus(Long userId) {
//        ConsultantDetails consultant = getConsultantDetailsByUserId(userId);
//
//        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultant.getConsultantId());
//
//        if (statusList.isEmpty()) {
//            return ConsultantStatusHistoryDTO.builder()
//                    .statusId(null)
//                    .status("PENDING")
//                    .note("No status available")
//                    .dateTime(LocalDateTime.now())
//                    .build();
//        }
//
//        // Get latest status (first one after sorting by dateTime desc)
//        ConsultantStatus latestStatus = statusList.stream()
//                .max(Comparator.comparing(ConsultantStatus::getDateTime))
//                .orElse(statusList.get(0));
//
//        return ConsultantStatusHistoryDTO.builder()
//                .statusId(latestStatus.getStatusId())
//                .status(latestStatus.getStatus())
//                .note(latestStatus.getNote())
//                .dateTime(latestStatus.getDateTime())
//                .build();
//    }
//}