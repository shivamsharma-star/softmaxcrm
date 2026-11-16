package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ClientApplyRequest;
import com.softmax.shardaos.dto.input.ClientUpdateRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.ClientApplicationResponse;
import com.softmax.shardaos.models.ClientDetails;
import com.softmax.shardaos.service.ClientManagementService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;

@RestController
@RequestMapping("/api/client")
public class ClientController {

    @Autowired
    private ClientManagementService clientManagementService;

    // 🔥 FULL DEBUG METHOD
    private void printDebug(HttpServletRequest request, Long userId, String apiName) {

        System.out.println("\n================ FULL DEBUG " + apiName + " ================");

        // 🔥 1. ALL HEADERS (RAW)
        System.out.println("\n👉 ALL HEADERS:");
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            System.out.println(name + " : " + value);
        }

        // 🔥 2. AUTH HEADER SPECIFIC
        String authHeader = request.getHeader("Authorization");

        System.out.println("\n👉 Authorization Header RAW:");
        System.out.println(authHeader);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String headerToken = authHeader.substring(7);

            System.out.println("\n🔥 ACTUAL TOKEN FROM HEADER:");
            System.out.println(headerToken);

            System.out.println("🔥 HEADER TOKEN LENGTH: " + headerToken.length());

            decodeJwt(headerToken);

        } else {
            System.out.println("❌ No valid Bearer token in header");
        }

        // 🔥 3. COOKIES (RAW + VALUE)
        System.out.println("\n👉 ALL COOKIES:");

        if (request.getCookies() != null) {

            for (Cookie cookie : request.getCookies()) {

                System.out.println("\n--- COOKIE ---");
                System.out.println("Name  : " + cookie.getName());
                System.out.println("Value : " + cookie.getValue());
                System.out.println("Path  : " + cookie.getPath());

                if ("auth_token".equals(cookie.getName())) {

                    System.out.println("\n🔥🔥 ACTUAL AUTH_TOKEN COOKIE:");
                    System.out.println(cookie.getValue());

                    System.out.println("🔥 COOKIE TOKEN LENGTH: " + cookie.getValue().length());

                    decodeJwt(cookie.getValue());
                }
            }

        } else {
            System.out.println("❌ No cookies received");
        }

        // 🔥 USER ID
        System.out.println("\n👉 RequestAttribute userId:");
        System.out.println(userId);

        System.out.println("================ DEBUG END =================\n");
    }

    // 🔥 JWT DECODE
    private void decodeJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");

            if (parts.length == 3) {
                

                String header = new String(java.util.Base64.getDecoder().decode(parts[0]));
                String payload = new String(java.util.Base64.getDecoder().decode(parts[1]));

                System.out.println("\n📌 JWT HEADER:");
                System.out.println(header);

                System.out.println("\n📌 JWT PAYLOAD:");
                System.out.println(payload);

            } else {
                System.out.println("❌ Invalid JWT format");
            }

        } catch (Exception e) {
            System.out.println("❌ JWT Decode Error: " + e.getMessage());
        }
    }

    // ================= APPLY =================
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ClientApplicationResponse>> applyForClient(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody ClientApplyRequest request,
            HttpServletRequest httpRequest
    ) {

        printDebug(httpRequest, userId, "/apply");

        try {
            ClientApplicationResponse response =
                    clientManagementService.applyForClient(userId, request);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ================= MY DETAILS =================
    @GetMapping("/my-details")
    public ResponseEntity<ApiResponse<ClientDetails>> getMyClientDetails(
            @RequestAttribute(value = "userId", required = false) Long userId,
            HttpServletRequest httpRequest
    ) {

        printDebug(httpRequest, userId, "/my-details");

        try {
            ClientDetails details =
                    clientManagementService.getClientDetailsByUserId(userId);

            return ResponseEntity.ok(ApiResponse.success(details));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ================= UPDATE =================
    @PutMapping("/update")
    public ResponseEntity<ApiResponse<ClientDetails>> updateClientDetails(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody ClientUpdateRequest request,
            HttpServletRequest httpRequest
    ) {

        printDebug(httpRequest, userId, "/update");

        try {
            ClientDetails updated =
                    clientManagementService.updateClientDetails(userId, request);

            return ResponseEntity.ok(
                    ApiResponse.success("Client details updated successfully", updated)
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}

//package com.softmax.shardaos.controller;
//
//import com.softmax.shardaos.dto.input.ClientApplyRequest;
//import com.softmax.shardaos.dto.input.ClientUpdateRequest;
//import com.softmax.shardaos.dto.output.ApiResponse;
//import com.softmax.shardaos.dto.output.ClientApplicationResponse;
//import com.softmax.shardaos.models.ClientDetails;
//import com.softmax.shardaos.service.ClientManagementService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/client")
//public class ClientController {
//
//    @Autowired
//    private ClientManagementService clientManagementService;
//
//    @PostMapping("/apply")
//    public ResponseEntity<ApiResponse<ClientApplicationResponse>> applyForClient(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody ClientApplyRequest request) {
//
//        try {
//            ClientApplicationResponse response = clientManagementService.applyForClient(userId, request);
//            return ResponseEntity.ok(ApiResponse.success(response));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/my-details")
//    public ResponseEntity<ApiResponse<ClientDetails>> getMyClientDetails(
//            @RequestAttribute("userId") Long userId) {
//
//        try {
//            ClientDetails details = clientManagementService.getClientDetailsByUserId(userId);
//            return ResponseEntity.ok(ApiResponse.success(details));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PutMapping("/update")
//    public ResponseEntity<ApiResponse<ClientDetails>> updateClientDetails(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody ClientUpdateRequest request) {
//
//        try {
//            ClientDetails updated = clientManagementService.updateClientDetails(userId, request);
//            return ResponseEntity.ok(ApiResponse.success("Client details updated successfully", updated));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//}