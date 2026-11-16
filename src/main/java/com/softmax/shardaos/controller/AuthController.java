package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.SignInRequest;
import com.softmax.shardaos.dto.output.AuthResponse;
import com.softmax.shardaos.dto.output.RoleDTO;
import com.softmax.shardaos.dto.output.UserDTO;
import com.softmax.shardaos.models.User;
import com.softmax.shardaos.models.UserType;
import com.softmax.shardaos.service.AuthService;
import com.softmax.shardaos.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/signin")
    public ResponseEntity<Map<String, Object>> signIn(
            @RequestBody SignInRequest request,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {

        AuthResponse authResponse = authService.signIn(request);
        String token = jwtUtil.generateToken(authResponse.getUser().getUserId());

        String domain = httpRequest.getHeader("Host");
        if (domain != null && domain.contains(":")) {
            domain = domain.substring(0, domain.indexOf(":"));
        }
        if (domain != null && domain.startsWith("www.")) {
            domain = domain.substring(4);
        }

        Cookie cookie = new Cookie("auth_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);

        if (domain != null && !domain.equals("localhost") && !domain.equals("127.0.0.1")) {
            cookie.setDomain(domain);
        }
        response.addCookie(cookie);

        String cookieHeader = String.format(
                "auth_token=%s; Path=/; HttpOnly; Secure; Max-Age=86400; SameSite=None%s",
                token,
                (domain != null && !domain.equals("localhost") && !domain.equals("127.0.0.1"))
                        ? "; Domain=" + domain
                        : ""
        );
        response.setHeader("Set-Cookie", cookieHeader);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResponse.getUser());
        responseBody.put("roles", authResponse.getRoles());
        responseBody.put("message", "Login successful");

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("auth_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.setHeader("Set-Cookie",
                "auth_token=; Path=/; HttpOnly; Secure; Max-Age=0; SameSite=None"
        );

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "Logged out successfully");

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "authenticated", false,
                    "message", "Not authenticated"
            ));
        }

        try {
            User user = authService.getUserById(userId);
            List<UserType> userTypes = authService.getUserRoles(userId);

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

            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "user", userDTO,
                    "roles", roleDTOList
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "authenticated", false,
                    "message", "Internal server error"
            ));
        }
    }
}