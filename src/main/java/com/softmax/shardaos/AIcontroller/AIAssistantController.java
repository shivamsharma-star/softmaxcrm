package com.softmax.shardaos.AIcontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softmax.shardaos.AIservice.AIAssistantService;
import com.softmax.shardaos.dto.ai.input.AIProjectAnalysisRequest;
import com.softmax.shardaos.dto.ai.input.AITaskSuggestionRequest;
import com.softmax.shardaos.dto.ai.output.AIProjectSuggestionResponse;
import com.softmax.shardaos.dto.ai.output.AITextResponse;
import com.softmax.shardaos.dto.ai.output.ConsultantProfileResponse;
import com.softmax.shardaos.dto.output.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIAssistantController {

    @Autowired
    private AIAssistantService aiAssistantService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== JSON RESPONSE APIs (Existing) ==========

    @PostMapping("/suggest-new-project-json")
    public ResponseEntity<ApiResponse<AIProjectSuggestionResponse>> suggestNewProjectJson(
            @RequestAttribute("userId") Long userId,
            @RequestBody AIProjectAnalysisRequest request) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /suggest-new-project-json");
        System.out.println("📌 User ID: " + userId);
        System.out.println("📌 Project Name: " + (request != null ? request.getProjectName() : "null"));
        System.out.println("========================================");

        try {
            AIProjectSuggestionResponse suggestion = aiAssistantService.suggestForNewProject(userId, request);

            String jsonResponse = objectMapper.writeValueAsString(suggestion);
            System.out.println("✅ FULL JSON RESPONSE:");
            System.out.println(jsonResponse);
            System.out.println("========================================\n");

            return ResponseEntity.ok(ApiResponse.success(suggestion));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/analyze-project-json/{projectId}")
    public ResponseEntity<ApiResponse<AIProjectSuggestionResponse>> analyzeProjectJson(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /analyze-project-json/" + projectId);
        System.out.println("📌 User ID: " + userId);
        System.out.println("========================================");

        try {
            AIProjectSuggestionResponse suggestion = aiAssistantService.analyzeExistingProject(userId, projectId);

            String jsonResponse = objectMapper.writeValueAsString(suggestion);
            System.out.println("✅ FULL JSON RESPONSE:");
            System.out.println(jsonResponse);
            System.out.println("========================================\n");

            return ResponseEntity.ok(ApiResponse.success(suggestion));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/suggest-consultant-for-task-json")
    public ResponseEntity<ApiResponse<String>> suggestConsultantForTaskJson(
            @RequestAttribute("userId") Long userId,
            @RequestBody AITaskSuggestionRequest request) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /suggest-consultant-for-task-json");
        System.out.println("📌 User ID: " + userId);
        System.out.println("📌 Task Name: " + (request != null ? request.getTaskName() : "null"));
        System.out.println("========================================");

        try {
            String suggestion = aiAssistantService.suggestConsultantForTask(userId, request);

            System.out.println("✅ SUGGESTION RESPONSE:");
            System.out.println(suggestion);
            System.out.println("========================================\n");

            return ResponseEntity.ok(ApiResponse.success(suggestion));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/consultant/profile/{consultantId}")
    public ResponseEntity<ApiResponse<ConsultantProfileResponse>> getConsultantProfile(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /consultant/profile/" + consultantId);
        System.out.println("📌 Requested by User ID: " + userId);
        System.out.println("========================================");

        try {
            ConsultantProfileResponse response = aiAssistantService.getConsultantProfile(userId, consultantId);

            System.out.println("✅ RESPONSE: " + response.getText());
            System.out.println("========================================\n");

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    // ========== TEXT RESPONSE APIs (For Frontend - Simple Text) ==========

    @PostMapping("/suggest-new-project")
    public ResponseEntity<ApiResponse<AITextResponse>> suggestNewProject(
            @RequestAttribute("userId") Long userId,
            @RequestBody AIProjectAnalysisRequest request) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /suggest-new-project (TEXT)");
        System.out.println("📌 User ID: " + userId);
        System.out.println("📌 Project Name: " + (request != null ? request.getProjectName() : "null"));
        System.out.println("========================================");

        try {
            AITextResponse response = aiAssistantService.suggestForNewProjectAsText(userId, request);
            System.out.println("✅ RESPONSE: " + response.getText());
            System.out.println("========================================\n");
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/analyze-project/{projectId}")
    public ResponseEntity<ApiResponse<AITextResponse>> analyzeProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /analyze-project/" + projectId + " (TEXT)");
        System.out.println("📌 User ID: " + userId);
        System.out.println("========================================");

        try {
            AITextResponse response = aiAssistantService.analyzeExistingProjectAsText(userId, projectId);
            System.out.println("✅ RESPONSE: " + response.getText());
            System.out.println("========================================\n");
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/suggest-consultant-for-task")
    public ResponseEntity<ApiResponse<AITextResponse>> suggestConsultantForTask(
            @RequestAttribute("userId") Long userId,
            @RequestBody AITaskSuggestionRequest request) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /suggest-consultant-for-task (TEXT)");
        System.out.println("📌 User ID: " + userId);
        System.out.println("📌 Task Name: " + (request != null ? request.getTaskName() : "null"));
        System.out.println("========================================");

        try {
            AITextResponse response = aiAssistantService.suggestConsultantForTaskAsText(userId, request);
            System.out.println("✅ RESPONSE: " + response.getText());
            System.out.println("========================================\n");
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/consultant/my-profile")
    public ResponseEntity<ApiResponse<AITextResponse>> getMyConsultantProfile(
            @RequestAttribute("userId") Long userId) {

        System.out.println("========================================");
        System.out.println("🤖 AI API CALLED: /consultant/my-profile (TEXT)");
        System.out.println("📌 User ID: " + userId);
        System.out.println("========================================");

        try {
            AITextResponse response = aiAssistantService.getConsultantProfileAsText(userId);
            System.out.println("✅ RESPONSE: " + response.getText());
            System.out.println("========================================\n");
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========================================\n");
            return ResponseEntity.status(500).body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }
}