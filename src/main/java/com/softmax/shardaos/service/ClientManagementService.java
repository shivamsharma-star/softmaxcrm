package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ClientApplyRequest;
import com.softmax.shardaos.dto.input.ClientUpdateRequest;
import com.softmax.shardaos.dto.output.ClientApplicationResponse;
import com.softmax.shardaos.dto.output.UserDTO;
import com.softmax.shardaos.dto.output.RoleDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClientManagementService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientDetailsRepository clientDetailsRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private final DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    @Transactional
    public ClientApplicationResponse applyForClient(Long userId, ClientApplyRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserType> existingUserTypes = userTypeRepository.findByUserUserId(userId);
        boolean alreadyHasClientRole = existingUserTypes.stream()
                .anyMatch(ut -> "CLIENT".equals(ut.getPtype()));

        if (alreadyHasClientRole) {
            throw new RuntimeException("User already has a CLIENT role");
        }

        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
            ClientDetails existingDetails = clientDetailsRepository.findByUserUserId(userId).get();
            if ("PENDING".equals(existingDetails.getClientStatus())) {
                throw new RuntimeException("Client application already submitted and pending approval");
            }
        }

        ClientDetails clientDetails;
        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
            clientDetails = clientDetailsRepository.findByUserUserId(userId).get();
            clientDetails.setClientStatus("PENDING");
            clientDetails.setCompanyName(request.getCompanyName());
            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
            clientDetails.setCompanyEmail(request.getCompanyEmail());
            clientDetails.setOrgType(request.getOrgType());
            clientDetails.setClientType(request.getClientType());
            clientDetails.setIsApproved(true);
            clientDetails.setClientWebsite(request.getClientWebsite());
        } else {
            clientDetails = new ClientDetails();
            clientDetails.setUser(user);
            clientDetails.setClientStatus("PENDING");
            clientDetails.setCompanyName(request.getCompanyName());
            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
            clientDetails.setCompanyEmail(request.getCompanyEmail());
            clientDetails.setOrgType(request.getOrgType());
            clientDetails.setClientType(request.getClientType());
            clientDetails.setIsApproved(true); // this for auto true
            clientDetails.setClientWebsite(request.getClientWebsite());
        }

        clientDetails = clientDetailsRepository.save(clientDetails);

        UserType userType = new UserType();
        userType.setUser(user);
        userType.setPtype("CLIENT");
        userType.setStype(null);
        userType.setIsapproved("PENDING");
        userType.setCreatedAt(LocalDateTime.now());

        userType = userTypeRepository.save(userType);

        // Professional Email (Impetus-style)
        try {
            String subject = "Application Received - Client Registration";

            String body = "<p style='margin:0 0 16px 0;'>Dear " + user.getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>Thank you for your interest in <strong>Softmax Technologies and IT Services</strong>. We have received your client registration application and it is currently under review by our Business Development team.</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:20px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + request.getCompanyName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Email:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + request.getCompanyEmail() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Company Mobile:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + request.getCompanyMobileNumber() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Status:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>Pending Review</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Submitted On:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + LocalDateTime.now().format(dateTime) + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:0 0 16px 0;'>Our team will review your application and get back to you within <strong>2-3 business days</strong>. You will be notified by email once a decision is made.</p>" +
                    "<p style='margin:0 0 16px 0;'><strong>Note:</strong> You will retain your existing roles while your client application is being processed.</p>" +
                    "<p style='margin:20px 0 0 0;'>If you have any questions in the meantime, please feel free to contact us at <a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a>.</p>";

            String html = buildEmail(subject, "Your client registration application has been received.", body);
            emailService.sendEmail(user.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }

        UserDTO userDTO = new UserDTO(
                user.getUserId(), user.getUid(), user.getName(), user.getEmail(),
                user.getPhotoUrl(), user.getEmailVerified(), user.getIsProfileCompleted(),
                user.getPhoneNumber(), user.getAddress(), user.getGender(), user.getDob(),
                user.getFcmToken(), user.getIsDglockerVerified(), user.getCreatedAt(),
                user.getUpdatedAt(), user.getProfileCreatedAt()
        );

        RoleDTO roleDTO = new RoleDTO(
                userType.getId(), userType.getPtype(), userType.getStype(),
                userType.getIsapproved(), userType.getCreatedAt()
        );

        return ClientApplicationResponse.builder()
                .message("Client application submitted successfully. Waiting for BDE approval. Your existing roles remain active.")
                .user(userDTO)
                .clientDetails(clientDetails)
                .role(roleDTO)
                .build();
    }

    public ClientDetails getClientDetailsByUserId(Long userId) {
        ClientDetails client = clientDetailsRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Client details not found"));
        System.out.println("🔍 getClientDetailsByUserId - User ID: " + userId);
        System.out.println("🔍 Is Approved from DB: " + client.getIsApproved());
        System.out.println("🔍 Client Status: " + client.getClientStatus());
        return client;
    }

    @Transactional
    public ClientDetails updateClientDetails(Long userId, ClientUpdateRequest request) {
        ClientDetails clientDetails = clientDetailsRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Client details not found"));

        if (request.getCompanyName() != null) clientDetails.setCompanyName(request.getCompanyName());
        if (request.getCompanyMobileNumber() != null) clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
        if (request.getCompanyEmail() != null) clientDetails.setCompanyEmail(request.getCompanyEmail());
        if (request.getOrgType() != null) clientDetails.setOrgType(request.getOrgType());
        if (request.getClientType() != null) clientDetails.setClientType(request.getClientType());
        if (request.getClientWebsite() != null) clientDetails.setClientWebsite(request.getClientWebsite());

        return clientDetailsRepository.save(clientDetails);
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
                "<p style='margin:0 0 4px 0;'><strong>Client Relations Team</strong></p>" +
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
}






//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ClientApplyRequest;
//import com.softmax.shardaos.dto.input.ClientUpdateRequest;
//import com.softmax.shardaos.dto.output.ClientApplicationResponse;
//import com.softmax.shardaos.dto.output.UserDTO;
//import com.softmax.shardaos.dto.output.RoleDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//public class ClientManagementService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private ClientDetailsRepository clientDetailsRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    @Transactional
//    public ClientApplicationResponse applyForClient(Long userId, ClientApplyRequest request) {
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        // Check if user already has CLIENT role specifically (not blocking other roles)
//        List<UserType> existingUserTypes = userTypeRepository.findByUserUserId(userId);
//        boolean alreadyHasClientRole = existingUserTypes.stream()
//                .anyMatch(ut -> "CLIENT".equals(ut.getPtype()));
//
//        if (alreadyHasClientRole) {
//            throw new RuntimeException("User already has a CLIENT role");
//        }
//
//        // Check if client application already exists - allow reapplication if rejected
//        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
//            ClientDetails existingDetails = clientDetailsRepository.findByUserUserId(userId).get();
//            if ("PENDING".equals(existingDetails.getClientStatus())) {
//                throw new RuntimeException("Client application already submitted and pending approval");
//            }
//            // If application was rejected, we'll update it instead of throwing error
//        }
//
//        ClientDetails clientDetails;
//        // Update existing rejected application or create new one
//        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
//            clientDetails = clientDetailsRepository.findByUserUserId(userId).get();
//            clientDetails.setClientStatus("PENDING");
//            clientDetails.setCompanyName(request.getCompanyName());
//            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
//            clientDetails.setCompanyEmail(request.getCompanyEmail());
//            clientDetails.setOrgType(request.getOrgType());
//            clientDetails.setClientType(request.getClientType());
//            clientDetails.setIsApproved(false);
//            clientDetails.setClientWebsite(request.getClientWebsite());
//        } else {
//            clientDetails = new ClientDetails();
//            clientDetails.setUser(user);
//            clientDetails.setClientStatus("PENDING");
//            clientDetails.setCompanyName(request.getCompanyName());
//            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
//            clientDetails.setCompanyEmail(request.getCompanyEmail());
//            clientDetails.setOrgType(request.getOrgType());
//            clientDetails.setClientType(request.getClientType());
//            clientDetails.setIsApproved(false);
//            clientDetails.setClientWebsite(request.getClientWebsite());
//        }
//
//        clientDetails = clientDetailsRepository.save(clientDetails);
//
//        // Create new tuple in user_type table for CLIENT role
//        UserType userType = new UserType();
//        userType.setUser(user);
//        userType.setPtype("CLIENT");
//        userType.setStype(null);
//        userType.setIsapproved("PENDING");
//        userType.setCreatedAt(LocalDateTime.now());
//
//        userType = userTypeRepository.save(userType);
//
//        String emailHtml = "<h2>Welcome " + user.getName() + "!</h2>" +
//                "<p>Your client application for <strong>" + request.getCompanyName() + "</strong> has been submitted successfully.</p>" +
//                "<p>Our BDE team will review and approve your application soon.</p>" +
//                "<p><strong>Note:</strong> You will retain your existing roles while your client application is being processed.</p>";
//
//        emailService.sendEmail(user.getEmail(), "Client Registration Submitted - ShardaOS", emailHtml);
//
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
//        return ClientApplicationResponse.builder()
//                .message("Client application submitted successfully. Waiting for BDE approval. Your existing roles remain active.")
//                .user(userDTO)
//                .clientDetails(clientDetails)
//                .role(roleDTO)
//                .build();
//    }
//
//    // ✅ FIXED: This will return whatever is in database (including APPROVED)
//    public ClientDetails getClientDetailsByUserId(Long userId) {
//        ClientDetails client = clientDetailsRepository.findByUserUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Client details not found"));
//
//        // ✅ Add debug log to verify
//        System.out.println("🔍 getClientDetailsByUserId - User ID: " + userId);
//        System.out.println("🔍 Is Approved from DB: " + client.getIsApproved());
//        System.out.println("🔍 Client Status: " + client.getClientStatus());
//
//        return client;
//    }
//
//    @Transactional
//    public ClientDetails updateClientDetails(Long userId, ClientUpdateRequest request) {
//
//        ClientDetails clientDetails = clientDetailsRepository.findByUserUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Client details not found"));
//
//        if (request.getCompanyName() != null) clientDetails.setCompanyName(request.getCompanyName());
//        if (request.getCompanyMobileNumber() != null) clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
//        if (request.getCompanyEmail() != null) clientDetails.setCompanyEmail(request.getCompanyEmail());
//        if (request.getOrgType() != null) clientDetails.setOrgType(request.getOrgType());
//        if (request.getClientType() != null) clientDetails.setClientType(request.getClientType());
//        if (request.getClientWebsite() != null) clientDetails.setClientWebsite(request.getClientWebsite());
//
//        return clientDetailsRepository.save(clientDetails);
//    }
//}
//
//
//
//
//
//
//
//
//
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.ClientApplyRequest;
////import com.softmax.shardaos.dto.input.ClientUpdateRequest;
////import com.softmax.shardaos.dto.output.ClientApplicationResponse;
////import com.softmax.shardaos.dto.output.UserDTO;
////import com.softmax.shardaos.dto.output.RoleDTO;
////import com.softmax.shardaos.models.*;
////import com.softmax.shardaos.repository.*;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import org.springframework.transaction.annotation.Transactional;
////
////import java.time.LocalDateTime;
////import java.util.List;
////
////@Service
////public class ClientManagementService {
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private ClientDetailsRepository clientDetailsRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    @Transactional
////    public ClientApplicationResponse applyForClient(Long userId, ClientApplyRequest request) {
////
////        User user = userRepository.findById(userId)
////                .orElseThrow(() -> new RuntimeException("User not found"));
////
////        // Check if user already has CLIENT role specifically (not blocking other roles)
////        List<UserType> existingUserTypes = userTypeRepository.findByUserUserId(userId);
////        boolean alreadyHasClientRole = existingUserTypes.stream()
////                .anyMatch(ut -> "CLIENT".equals(ut.getPtype()));
////
////        if (alreadyHasClientRole) {
////            throw new RuntimeException("User already has a CLIENT role");
////        }
////
////        // Check if client application already exists - allow reapplication if rejected
////        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
////            ClientDetails existingDetails = clientDetailsRepository.findByUserUserId(userId).get();
////            if ("PENDING".equals(existingDetails.getClientStatus())) {
////                throw new RuntimeException("Client application already submitted and pending approval");
////            }
////            // If application was rejected, we'll update it instead of throwing error
////        }
////
////        ClientDetails clientDetails;
////        // Update existing rejected application or create new one
////        if (clientDetailsRepository.findByUserUserId(userId).isPresent()) {
////            clientDetails = clientDetailsRepository.findByUserUserId(userId).get();
////            clientDetails.setClientStatus("PENDING");
////            clientDetails.setCompanyName(request.getCompanyName());
////            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
////            clientDetails.setCompanyEmail(request.getCompanyEmail());
////            clientDetails.setOrgType(request.getOrgType());
////            clientDetails.setClientType(request.getClientType());
////            clientDetails.setIsApproved(false);
////            clientDetails.setClientWebsite(request.getClientWebsite());
////        } else {
////            clientDetails = new ClientDetails();
////            clientDetails.setUser(user);
////            clientDetails.setClientStatus("PENDING");
////            clientDetails.setCompanyName(request.getCompanyName());
////            clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
////            clientDetails.setCompanyEmail(request.getCompanyEmail());
////            clientDetails.setOrgType(request.getOrgType());
////            clientDetails.setClientType(request.getClientType());
////            clientDetails.setIsApproved(false);
////            clientDetails.setClientWebsite(request.getClientWebsite());
////        }
////
////        clientDetails = clientDetailsRepository.save(clientDetails);
////
////        // Create new tuple in user_type table for CLIENT role
////        UserType userType = new UserType();
////        userType.setUser(user);
////        userType.setPtype("CLIENT");
////        userType.setStype(null);
////        userType.setIsapproved("PENDING");
////        userType.setCreatedAt(LocalDateTime.now());
////
////        userType = userTypeRepository.save(userType);
////
////        String emailHtml = "<h2>Welcome " + user.getName() + "!</h2>" +
////                "<p>Your client application for <strong>" + request.getCompanyName() + "</strong> has been submitted successfully.</p>" +
////                "<p>Our BDE team will review and approve your application soon.</p>" +
////                "<p><strong>Note:</strong> You will retain your existing roles while your client application is being processed.</p>";
////
////        emailService.sendEmail(user.getEmail(), "Client Registration Submitted - ShardaOS", emailHtml);
////
////        // ✅ FIXED: UserDTO with ALL 15 fields
////        UserDTO userDTO = new UserDTO(
////                user.getUserId(),
////                user.getUid(),
////                user.getName(),
////                user.getEmail(),
////                user.getPhotoUrl(),
////                user.getEmailVerified(),
////                user.getIsProfileCompleted(),
////                user.getPhoneNumber(),
////                user.getAddress(),
////                user.getGender(),
////                user.getDob(),
////                user.getFcmToken(),
////                user.getIsDglockerVerified(),
////                user.getCreatedAt(),
////                user.getUpdatedAt(),
////                user.getProfileCreatedAt()
////        );
////
////        RoleDTO roleDTO = new RoleDTO(
////                userType.getId(),
////                userType.getPtype(),
////                userType.getStype(),
////                userType.getIsapproved(),
////                userType.getCreatedAt()
////        );
////
////        return ClientApplicationResponse.builder()
////                .message("Client application submitted successfully. Waiting for BDE approval. Your existing roles remain active.")
////                .user(userDTO)
////                .clientDetails(clientDetails)
////                .role(roleDTO)
////                .build();
////    }
////
////    public ClientDetails getClientDetailsByUserId(Long userId) {
////        return clientDetailsRepository.findByUserUserId(userId)
////                .orElseThrow(() -> new RuntimeException("Client details not found"));
////    }
////
////    @Transactional
////    public ClientDetails updateClientDetails(Long userId, ClientUpdateRequest request) {
////
////        ClientDetails clientDetails = clientDetailsRepository.findByUserUserId(userId)
////                .orElseThrow(() -> new RuntimeException("Client details not found"));
////
////        if (request.getCompanyName() != null) clientDetails.setCompanyName(request.getCompanyName());
////        if (request.getCompanyMobileNumber() != null) clientDetails.setCompanyMobileNumber(request.getCompanyMobileNumber());
////        if (request.getCompanyEmail() != null) clientDetails.setCompanyEmail(request.getCompanyEmail());
////        if (request.getOrgType() != null) clientDetails.setOrgType(request.getOrgType());
////        if (request.getClientType() != null) clientDetails.setClientType(request.getClientType());
////        if (request.getClientWebsite() != null) clientDetails.setClientWebsite(request.getClientWebsite());
////
////        return clientDetailsRepository.save(clientDetails);
////    }
////}