package com.softmax.shardaos.service;

import com.google.firebase.auth.FirebaseToken;
import com.softmax.shardaos.dto.input.SignInRequest;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.User;
import com.softmax.shardaos.models.UserType;
import com.softmax.shardaos.repository.UserRepository;
import com.softmax.shardaos.repository.UserTypeRepository;
import com.softmax.shardaos.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuthService {

    @Autowired
    private FirebaseAuthService firebaseAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    public AuthResponse signIn(SignInRequest request) {

        FirebaseToken decoded = firebaseAuthService.verifyToken(request.getToken());

        String uid = decoded.getUid();
        String email = decoded.getEmail();

        User user = userRepository.findByUid(uid).orElse(null);
        boolean isNewUser = false;

        if (user == null) {
            // UID se nahi mila — career portal se aaye user ka UID alag ho sakta hai
            // Ab email se fallback try karo
            user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                // Purana user mila (career portal se) — UID update karo
                user.setUid(uid);
                user = userRepository.save(user);
                // isNewUser = false hi rahega — welcome email dobara nahi jayegi
            } else {
                // Bilkul naya user
                user = new User();
                user.setUid(uid);
                user.setEmail(email);

                // Firebase token se name aur photo lena
                user.setName(decoded.getName());
                user.setPhotoUrl((String) decoded.getClaims().get("picture"));

                user.setEmailVerified(true);
                user.setIsProfileCompleted(false);

                user = userRepository.save(user);
                isNewUser = true;
            }
        }

        List<UserType> userTypes = userTypeRepository.findByUserUserId(user.getUserId());

        List<RoleDTO> roleDTOList = userTypes.stream()
                .map(r -> new RoleDTO(
                        r.getId(),
                        r.getPtype(),
                        r.getStype(),
                        r.getIsapproved(),
                        r.getCreatedAt()
                ))
                .toList();

        // ✅ FIXED: UserDTO with ALL 15 fields
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

        // ✅ Send email ONLY for new user creation (not on every login)
        if (isNewUser) {
            try {

                String time = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

                emailService.sendEmail(
                        user.getEmail(),
                        "Welcome to Softmax Technologies and IT Services",
                        getWelcomeEmail(user.getName(), time)
                );

            } catch (Exception e) {
                System.out.println("Email error: " + e.getMessage());
            }
        }

        return new AuthResponse(userDTO, roleDTOList);
    }

    public User getUserById(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<UserType> getUserRoles(Long userId) {

        return userTypeRepository.findByUserUserId(userId);
    }

    private String getWelcomeEmail(String name, String time) {

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<style>" +
                "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}" +
                ".container{max-width:600px;margin:30px auto;background:white;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}" +
                ".header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:40px 30px;text-align:center;}" +
                ".header h1{color:white;margin:0;font-size:28px;letter-spacing:0.5px;}" +
                ".header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}" +
                ".content{padding:40px 35px;text-align:center;}" +
                ".info-box{background:#f0f4ff;padding:20px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;text-align:left;}" +
                ".info-box p{margin:8px 0;color:#2d3748;font-size:14px;}" +
                ".btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:white !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(102,126,234,0.35);}" +
                ".footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🎉 Welcome to Softmax</h1>" +
                "<p>Softmax Technologies and IT Services</p>" +
                "</div>" +
                "<div class='content'>" +
                "<h2 style='color:#333;margin-top:0;'>Dear " + name + ",</h2>" +
                "<p style='color:#4a5568;font-size:15px;'>Your account has been successfully created.</p>" +
                "<div class='info-box'>" +
                "<p>👤 <strong>Name:</strong> " + name + "</p>" +
                "<p>📅 <strong>Member since:</strong> " + time + "</p>" +
                "</div>" +
                "<p style='color:#4a5568;'>Thank you for joining us! We're excited to have you on board.</p>" +
                "<a href='https://softmax.in' class='btn'>🚀 Go to Softmax</a>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>" +
                "<p>Team Softmax Technologies and IT Services</p>" +
                "</div>" +
                "</div></body></html>";
    }
}










//package com.softmax.shardaos.service;
//
//import com.google.firebase.auth.FirebaseToken;
//import com.softmax.shardaos.dto.input.SignInRequest;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.User;
//import com.softmax.shardaos.models.UserType;
//import com.softmax.shardaos.repository.UserRepository;
//import com.softmax.shardaos.repository.UserTypeRepository;
//import com.softmax.shardaos.security.JwtUtil;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//
//@Service
//public class AuthService {
//
//    @Autowired
//    private FirebaseAuthService firebaseAuthService;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Autowired
//    private EmailService emailService;
//
//    public AuthResponse signIn(SignInRequest request) {
//
//        FirebaseToken decoded = firebaseAuthService.verifyToken(request.getToken());
//
//        String uid = decoded.getUid();
//        String email = decoded.getEmail();
//
//        User user = userRepository.findByUid(uid).orElse(null);
//        boolean isNewUser = false;
//
//        if (user == null) {
//
//            user = new User();
//            user.setUid(uid);
//            user.setEmail(email);
//
//            // Firebase token se name aur photo lena
//            user.setName(decoded.getName());
//            user.setPhotoUrl((String) decoded.getClaims().get("picture"));
//
//            user.setEmailVerified(true);
//            user.setIsProfileCompleted(false);
//
//            user = userRepository.save(user);
//            isNewUser = true;
//        }
//
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(user.getUserId());
//
//        List<RoleDTO> roleDTOList = userTypes.stream()
//                .map(r -> new RoleDTO(
//                        r.getId(),
//                        r.getPtype(),
//                        r.getStype(),
//                        r.getIsapproved(),
//                        r.getCreatedAt()
//                ))
//                .toList();
//
//        // ✅ FIXED: UserDTO with ALL 15 fields
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
//        // ✅ Send email ONLY for new user creation (not on every login)
//        if (isNewUser) {
//            try {
//
//                String time = LocalDateTime.now()
//                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
//
//                emailService.sendEmail(
//                        user.getEmail(),
//                        "Welcome to Softmax Technologies and IT Services",
//                        getWelcomeEmail(user.getName(), time)
//                );
//
//            } catch (Exception e) {
//                System.out.println("Email error: " + e.getMessage());
//            }
//        }
//
//        return new AuthResponse(userDTO, roleDTOList);
//    }
//
//    public User getUserById(Long userId) {
//
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//    }
//
//    public List<UserType> getUserRoles(Long userId) {
//
//        return userTypeRepository.findByUserUserId(userId);
//    }
//
//    private String getWelcomeEmail(String name, String time) {
//
//        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
//                "<style>" +
//                "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}" +
//                ".container{max-width:600px;margin:30px auto;background:white;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}" +
//                ".header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:40px 30px;text-align:center;}" +
//                ".header h1{color:white;margin:0;font-size:28px;letter-spacing:0.5px;}" +
//                ".header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}" +
//                ".content{padding:40px 35px;text-align:center;}" +
//                ".info-box{background:#f0f4ff;padding:20px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;text-align:left;}" +
//                ".info-box p{margin:8px 0;color:#2d3748;font-size:14px;}" +
//                ".btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:white !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(102,126,234,0.35);}" +
//                ".footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}" +
//                "</style></head><body>" +
//                "<div class='container'>" +
//                "<div class='header'>" +
//                "<h1>🎉 Welcome to Softmax</h1>" +
//                "<p>Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "<div class='content'>" +
//                "<h2 style='color:#333;margin-top:0;'>Dear " + name + ",</h2>" +
//                "<p style='color:#4a5568;font-size:15px;'>Your account has been successfully created.</p>" +
//                "<div class='info-box'>" +
//                "<p>👤 <strong>Name:</strong> " + name + "</p>" +
//                "<p>📅 <strong>Member since:</strong> " + time + "</p>" +
//                "</div>" +
//                "<p style='color:#4a5568;'>Thank you for joining us! We're excited to have you onboard.</p>" +
//                "<a href='https://softmax.in' class='btn'>🚀 Go to Softmax</a>" +
//                "</div>" +
//                "<div class='footer'>" +
//                "<p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>" +
//                "<p>Team Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "</div></body></html>";
//    }
//}
//
















//package com.softmax.shardaos.service;
//
//import com.google.firebase.auth.FirebaseToken;
//import com.softmax.shardaos.dto.input.SignInRequest;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.User;
//import com.softmax.shardaos.models.UserType;
//import com.softmax.shardaos.repository.UserRepository;
//import com.softmax.shardaos.repository.UserTypeRepository;
//import com.softmax.shardaos.security.JwtUtil;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//
//@Service
//public class AuthService {
//
//    @Autowired
//    private FirebaseAuthService firebaseAuthService;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Autowired
//    private EmailService emailService;
//
//    public AuthResponse signIn(SignInRequest request) {
//
//        FirebaseToken decoded = firebaseAuthService.verifyToken(request.getToken());
//
//        String uid = decoded.getUid();
//        String email = decoded.getEmail();
//
//        User user = userRepository.findByUid(uid).orElse(null);
//        boolean isNewUser = false;
//
//        if (user == null) {
//
//            user = new User();
//            user.setUid(uid);
//            user.setEmail(email);
//
//            // Firebase token se name aur photo lena
//            user.setName(decoded.getName());
//            user.setPhotoUrl((String) decoded.getClaims().get("picture"));
//
//            user.setEmailVerified(true);
//            user.setIsProfileCompleted(false);
//
//            user = userRepository.save(user);
//            isNewUser = true;
//        }
//
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(user.getUserId());
//
//        List<RoleDTO> roleDTOList = userTypes.stream()
//                .map(r -> new RoleDTO(
//                        r.getId(),
//                        r.getPtype(),
//                        r.getStype(),
//                        r.getIsapproved(),
//                        r.getCreatedAt()
//                ))
//                .toList();
//
//        // ✅ FIXED: UserDTO with ALL 15 fields
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
//        try {
//
//            String time = LocalDateTime.now()
//                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
//
//            if (isNewUser) {
//
//                emailService.sendEmail(
//                        user.getEmail(),
//                        "Welcome to Softmax Technologies and IT Services",
//                        getWelcomeEmail(user.getName(), time)
//                );
//
//            } else {
//
//                emailService.sendEmail(
//                        user.getEmail(),
//                        "Login Alert - Softmax Technologies and IT Services",
//                        getLoginEmail(user.getName(), user.getEmail(), time)
//                );
//            }
//
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//
//        return new AuthResponse(userDTO, roleDTOList);
//    }
//
//    public User getUserById(Long userId) {
//
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//    }
//
//    public List<UserType> getUserRoles(Long userId) {
//
//        return userTypeRepository.findByUserUserId(userId);
//    }
//
//    private String getWelcomeEmail(String name, String time) {
//
//        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
//                "<style>" +
//                "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}" +
//                ".container{max-width:600px;margin:30px auto;background:white;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}" +
//                ".header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:40px 30px;text-align:center;}" +
//                ".header h1{color:white;margin:0;font-size:28px;letter-spacing:0.5px;}" +
//                ".header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}" +
//                ".content{padding:40px 35px;text-align:center;}" +
//                ".info-box{background:#f0f4ff;padding:20px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;text-align:left;}" +
//                ".info-box p{margin:8px 0;color:#2d3748;font-size:14px;}" +
//                ".btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:white !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(102,126,234,0.35);}" +
//                ".footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}" +
//                "</style></head><body>" +
//                "<div class='container'>" +
//                "<div class='header'>" +
//                "<h1>🎉 Welcome to Softmax</h1>" +
//                "<p>Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "<div class='content'>" +
//                "<h2 style='color:#333;margin-top:0;'>Dear " + name + ",</h2>" +
//                "<p style='color:#4a5568;font-size:15px;'>Your account has been successfully created.</p>" +
//                "<div class='info-box'>" +
//                "<p>👤 <strong>Name:</strong> " + name + "</p>" +
//                "<p>📅 <strong>Member since:</strong> " + time + "</p>" +
//                "</div>" +
//                "<p style='color:#4a5568;'>Thank you for joining us! We're excited to have you onboard.</p>" +
//                "<a href='https://nexus.softmax.in/dashboard' class='btn'>🚀 Go to Dashboard</a>" +
//                "</div>" +
//                "<div class='footer'>" +
//                "<p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>" +
//                "<p>Team Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "</div></body></html>";
//    }
//
//    private String getLoginEmail(String name, String email, String time) {
//
//        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
//                "<style>" +
//                "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;}" +
//                ".container{max-width:600px;margin:30px auto;background:white;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);}" +
//                ".header{background:linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%);padding:40px 30px;text-align:center;}" +
//                ".header h1{color:white;margin:0;font-size:28px;letter-spacing:0.5px;}" +
//                ".header p{color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;}" +
//                ".content{padding:40px 35px;text-align:center;}" +
//                ".info-box{background:#e8f4f8;padding:20px;border-radius:12px;margin:22px 0;border-left:5px solid #2193b0;text-align:left;}" +
//                ".info-box p{margin:8px 0;color:#2d3748;font-size:14px;}" +
//                ".btn{display:inline-block;background:linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%);color:white !important;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;margin-top:18px;font-size:15px;box-shadow:0 6px 18px rgba(33,147,176,0.35);}" +
//                ".footer{background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;}" +
//                ".footer a{color:#2193b0;text-decoration:none;}" +
//                "</style></head><body>" +
//                "<div class='container'>" +
//                "<div class='header'>" +
//                "<h1>🔐 Login Alert</h1>" +
//                "<p>Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "<div class='content'>" +
//                "<h2 style='color:#333;margin-top:0;'>Hello " + name + ",</h2>" +
//                "<p style='color:#4a5568;font-size:15px;'>We detected a successful login to your account.</p>" +
//                "<div class='info-box'>" +
//                "<p>📧 <strong>Email:</strong> " + email + "</p>" +
//                "<p>⏰ <strong>Time:</strong> " + time + "</p>" +
//                "</div>" +
//                "<p style='color:#4a5568;'>If this wasn't you, please contact support immediately.</p>" +
//                "<a href='mailto:softmaxdigitals@gmail.com' class='btn'>📧 Contact Support</a>" +
//                "</div>" +
//                "<div class='footer'>" +
//                "<p>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>" +
//                "<p>Team Softmax Technologies and IT Services</p>" +
//                "</div>" +
//                "</div></body></html>";
//    }
//}
//
//























//package com.softmax.shardaos.service;
//
//import com.google.firebase.auth.FirebaseToken;
//import com.softmax.shardaos.dto.input.SignInRequest;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.User;
//import com.softmax.shardaos.models.UserType;
//import com.softmax.shardaos.repository.UserRepository;
//import com.softmax.shardaos.repository.UserTypeRepository;
//import com.softmax.shardaos.security.JwtUtil;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//
//@Service
//public class AuthService {
//
//    @Autowired
//    private FirebaseAuthService firebaseAuthService;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Autowired
//    private EmailService emailService;
//
//    public AuthResponse signIn(SignInRequest request) {
//
//        FirebaseToken decoded = firebaseAuthService.verifyToken(request.getToken());
//
//        String uid = decoded.getUid();
//        String email = decoded.getEmail();
//
//        User user = userRepository.findByUid(uid).orElse(null);
//        boolean isNewUser = false;
//
//        if (user == null) {
//
//            user = new User();
//            user.setUid(uid);
//            user.setEmail(email);
//
//            // Firebase token se name aur photo lena
//            user.setName(decoded.getName());
//            user.setPhotoUrl((String) decoded.getClaims().get("picture"));
//
//            user.setEmailVerified(true);
//            user.setIsProfileCompleted(false);
//
//            user = userRepository.save(user);
//            isNewUser = true;
//        }
//
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(user.getUserId());
//
//        List<RoleDTO> roleDTOList = userTypes.stream()
//                .map(r -> new RoleDTO(
//                        r.getId(),
//                        r.getPtype(),
//                        r.getStype(),
//                        r.getIsapproved(),
//                        r.getCreatedAt()
//                ))
//                .toList();
//
//        // ✅ FIXED: UserDTO with ALL 15 fields
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
//        try {
//
//            String time = LocalDateTime.now()
//                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
//
//            if (isNewUser) {
//
//                emailService.sendEmail(
//                        user.getEmail(),
//                        "Welcome to Gaurion Bharat",
//                        getWelcomeEmail(user.getName(), time)
//                );
//
//            } else {
//
//                emailService.sendEmail(
//                        user.getEmail(),
//                        "Login Alert - Gaurion Bharat",
//                        getLoginEmail(user.getName(), user.getEmail(), time)
//                );
//            }
//
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//
//        return new AuthResponse(userDTO, roleDTOList);
//    }
//
//    public User getUserById(Long userId) {
//
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//    }
//
//    public List<UserType> getUserRoles(Long userId) {
//
//        return userTypeRepository.findByUserUserId(userId);
//    }
//
//    private String getWelcomeEmail(String name, String time) {
//
//        return "<html><body style='font-family:Arial;text-align:center;padding:20px'>" +
//                "<h2 style='color:#667eea'>Welcome to Gaurion Bharat!</h2>" +
//                "<p>Dear <strong>" + name + "</strong>,</p>" +
//                "<p>Your account has been successfully created.</p>" +
//                "<p>Member since: <strong>" + time + "</strong></p>" +
//                "<a href='https://gaurionbharat.com/dashboard' style='background:#667eea;color:white;padding:10px 20px;text-decoration:none;border-radius:5px'>Go to Dashboard</a>" +
//                "<p style='margin-top:20px'>Thank you for joining us!</p>" +
//                "<p>Team Gaurion Bharat</p></body></html>";
//    }
//
//    private String getLoginEmail(String name, String email, String time) {
//
//        return "<html><body style='font-family:Arial;text-align:center;padding:20px'>" +
//                "<h2 style='color:#2193b0'>Login Alert</h2>" +
//                "<p>Hello <strong>" + name + "</strong>,</p>" +
//                "<p>We detected a successful login to your account.</p>" +
//                "<p><strong>Email:</strong> " + email + "</p>" +
//                "<p><strong>Time:</strong> " + time + "</p>" +
//                "<p>If this wasn't you, please contact support immediately.</p>" +
//                "<a href='mailto:support@gaurionbharat.com' style='background:#2193b0;color:white;padding:10px 20px;text-decoration:none;border-radius:5px'>Contact Support</a>" +
//                "<p style='margin-top:20px'>Team Gaurion Bharat</p></body></html>";
//    }
//}