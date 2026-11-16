package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ConsultantRatingRequestDTO;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.ConsultantProjectResponseDTO;
import com.softmax.shardaos.dto.output.ConsultantRequestResponseDTO;
import com.softmax.shardaos.dto.output.ConsultantRatingResponseDTO;
import com.softmax.shardaos.dto.output.ConsultantWithRatingDTO;
import com.softmax.shardaos.service.ConsultantProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultant-project")
public class ConsultantProjectController {

    @Autowired
    private ConsultantProjectService consultantProjectService;

    // ==================== CONSULTANT APIs ====================

    @GetMapping("/department-projects")
    public ResponseEntity<ApiResponse<List<ConsultantProjectResponseDTO>>> getDepartmentProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantProjectResponseDTO> projects = consultantProjectService.getDepartmentProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-assigned-projects")
    public ResponseEntity<ApiResponse<List<ConsultantProjectResponseDTO>>> getMyAssignedProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantProjectResponseDTO> projects = consultantProjectService.getMyAssignedProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/request/{projectId}")
    public ResponseEntity<ApiResponse<String>> requestToJoinProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            String result = consultantProjectService.requestToJoinProject(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-requests")
    public ResponseEntity<ApiResponse<List<ConsultantRequestResponseDTO>>> getMyRequests(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantRequestResponseDTO> requests = consultantProjectService.getMyRequests(userId);
            return ResponseEntity.ok(ApiResponse.success(requests));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/has-requested/{projectId}")
    public ResponseEntity<ApiResponse<Boolean>> hasRequested(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            Boolean result = consultantProjectService.hasRequested(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-ratings")
    public ResponseEntity<ApiResponse<List<ConsultantRatingResponseDTO>>> getMyRatings(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantRatingResponseDTO> ratings = consultantProjectService.getConsultantRatings(userId);
            return ResponseEntity.ok(ApiResponse.success(ratings));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-average-rating")
    public ResponseEntity<ApiResponse<Double>> getMyAverageRating(
            @RequestAttribute("userId") Long userId) {
        try {
            Double avgRating = consultantProjectService.getConsultantAverageRating(userId);
            return ResponseEntity.ok(ApiResponse.success(avgRating));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== MANAGER APIs ====================

    @GetMapping("/manager/requests")
    public ResponseEntity<ApiResponse<List<ConsultantRequestResponseDTO>>> getProjectRequestsForManager(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantRequestResponseDTO> requests = consultantProjectService.getProjectRequestsForManager(userId);
            return ResponseEntity.ok(ApiResponse.success(requests));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

//    @PostMapping("/manager/rate")
//    public ResponseEntity<ApiResponse<ConsultantRatingResponseDTO>> rateConsultant(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody ConsultantRatingRequestDTO request) {
//        try {
//            ConsultantRatingResponseDTO rating = consultantProjectService.rateConsultant(userId, request);
//            return ResponseEntity.ok(ApiResponse.success("Rating submitted successfully", rating));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }

    @PostMapping("/manager/rate")
    public ResponseEntity<ApiResponse<ConsultantRatingResponseDTO>> rateConsultant(
            @RequestAttribute("userId") Long userId,
            @RequestBody ConsultantRatingRequestDTO request) {

        // ========== PRINT INPUT ==========
        System.out.println("========================================");
        System.out.println("⭐ MANAGER RATING CONSULTANT");
        System.out.println("========================================");
        System.out.println("📌 Manager ID (from cookie): " + userId);
        System.out.println("📌 Consultant ID: " + request.getConsultantId());
        System.out.println("📌 Rating Given: " + request.getRating() + " / 5.0");
        System.out.println("📌 Feedback: " + request.getFeedback());
        System.out.println("========================================\n");

        try {
            ConsultantRatingResponseDTO rating = consultantProjectService.rateConsultant(userId, request);

            // ========== PRINT OUTPUT ==========
            System.out.println("✅ RATING SAVED SUCCESSFULLY");
            System.out.println("========================================");
            System.out.println("📌 Review ID: " + rating.getReviewId());
            System.out.println("📌 Consultant ID: " + rating.getConsultantId());
            System.out.println("📌 Consultant Name: " + rating.getConsultantName());
            System.out.println("📌 Reviewer ID: " + rating.getReviewerId());
            System.out.println("📌 Reviewer Name: " + rating.getReviewerName());
            System.out.println("📌 Rating Given: " + rating.getReviewMarks() + " / 5.0");
            System.out.println("📌 Feedback: " + rating.getReviewNote());
            System.out.println("📌 Created At: " + rating.getCreatedAt());
            System.out.println("========================================\n");

            return ResponseEntity.ok(ApiResponse.success("Rating submitted successfully", rating));
        } catch (RuntimeException e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            System.out.println("========================================\n");
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }










    @GetMapping("/manager/consultants-with-ratings")
    public ResponseEntity<ApiResponse<List<ConsultantWithRatingDTO>>> getAllConsultantsWithRatings(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long departmentId) {
        try {
            List<ConsultantWithRatingDTO> consultants = consultantProjectService.getAllConsultantsWithRatings(userId, departmentId);
            return ResponseEntity.ok(ApiResponse.success(consultants));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/manager/my-department-consultants")
    public ResponseEntity<ApiResponse<List<ConsultantWithRatingDTO>>> getMyDepartmentConsultants(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantWithRatingDTO> consultants = consultantProjectService.getMyDepartmentConsultants(userId);
            return ResponseEntity.ok(ApiResponse.success(consultants));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}