package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.SendEmailRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.EmailRecipientDTO;
import com.softmax.shardaos.dto.output.EmailResponseDTO;
import com.softmax.shardaos.service.EmailMessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/email")
public class EmailMessagingController {

    @Autowired
    private EmailMessagingService emailMessagingService;

    // ==================== GET RECIPIENTS ====================

    @GetMapping("/recipients/consultants")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getAllConsultants() {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getAllConsultants();
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recipients/employees")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getAllEmployees() {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getAllEmployees();
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recipients/managers")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getAllManagers() {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getAllManagers();
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recipients/directors")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getAllDirectors() {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getAllDirectors();
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recipients/all-users")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getAllUsers() {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getAllUsers();
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recipients/my-department-consultants")
    public ResponseEntity<ApiResponse<List<EmailRecipientDTO>>> getMyDepartmentConsultants(
            @RequestAttribute("userId") Long userId) {
        try {
            List<EmailRecipientDTO> recipients = emailMessagingService.getMyDepartmentConsultants(userId);
            return ResponseEntity.ok(ApiResponse.success(recipients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== SEND EMAIL ====================

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<EmailResponseDTO>> sendEmail(
            @RequestAttribute("userId") Long userId,
            @RequestBody SendEmailRequest request) {
        try {
            EmailResponseDTO response = emailMessagingService.sendEmail(userId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}