package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.NewUserMailRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.service.SendMailByUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mail")
public class SendMailByUserController {

    @Autowired
    private SendMailByUserService sendMailByUserService;

    @PostMapping("/send-inquiry")
    public ResponseEntity<ApiResponse<String>> sendUserInquiry(
            @RequestAttribute("userId") Long userId,
            @RequestBody NewUserMailRequest request) {

        try {
            // Validate interest field
            if (request.getIntrest() == null || request.getIntrest().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Interest field is required"));
            }

            // Validate message field
            if (request.getMsg() == null || request.getMsg().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Message field is required"));
            }

            sendMailByUserService.sendUserInquiryEmail(userId, request);

            return ResponseEntity.ok(ApiResponse.success(
                    "Your inquiry has been sent successfully. Our team will contact you soon.",
                    "Email sent to Head Directors and confirmation sent to you"
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}