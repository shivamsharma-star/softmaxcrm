package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ConsultantApplyRequest;
import com.softmax.shardaos.dto.input.ConsultantUpdateRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.ConsultantApplicationResponse;
import com.softmax.shardaos.dto.output.ConsultantStatusHistoryDTO;
import com.softmax.shardaos.models.ConsultantDetails;
import com.softmax.shardaos.service.ConsultantManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultant")
public class ConsultantController {

    @Autowired
    private ConsultantManagementService consultantManagementService;

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ConsultantApplicationResponse>> applyForConsultant(
            @RequestAttribute("userId") Long userId,
            @ModelAttribute ConsultantApplyRequest request) {

        try {
            ConsultantApplicationResponse response = consultantManagementService.applyForConsultant(userId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-details")
    public ResponseEntity<ApiResponse<ConsultantDetails>> getMyConsultantDetails(
            @RequestAttribute("userId") Long userId) {

        try {
            ConsultantDetails details = consultantManagementService.getConsultantDetailsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success(details));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ConsultantDetails>> updateConsultantDetails(
            @RequestAttribute("userId") Long userId,
            @ModelAttribute ConsultantUpdateRequest request) {

        try {
            ConsultantDetails updated = consultantManagementService.updateConsultantDetails(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Consultant details updated successfully", updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-status-history")
    public ResponseEntity<ApiResponse<List<ConsultantStatusHistoryDTO>>> getMyStatusHistory(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantStatusHistoryDTO> history = consultantManagementService.getMyStatusHistory(userId);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-latest-status")
    public ResponseEntity<ApiResponse<ConsultantStatusHistoryDTO>> getMyLatestStatus(
            @RequestAttribute("userId") Long userId) {
        try {
            ConsultantStatusHistoryDTO status = consultantManagementService.getMyLatestStatus(userId);
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}