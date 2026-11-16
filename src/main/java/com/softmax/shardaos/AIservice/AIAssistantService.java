package com.softmax.shardaos.AIservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softmax.shardaos.dto.ai.input.AIProjectAnalysisRequest;
import com.softmax.shardaos.dto.ai.input.AITaskSuggestionRequest;
import com.softmax.shardaos.dto.ai.output.AIProjectSuggestionResponse;
import com.softmax.shardaos.dto.ai.output.AITextResponse;
import com.softmax.shardaos.dto.ai.output.ConsultantProfileResponse;
import com.softmax.shardaos.dto.ai.output.ProjectAnalysisDTO;
import com.softmax.shardaos.models.ConsultantDetails;
import com.softmax.shardaos.models.ProjectDetails;
import com.softmax.shardaos.repository.ProjectDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AIAssistantService {

    @Autowired
    private OllamaApiService ollamaApiService;

    @Autowired
    private AIDataFetcherService aiDataFetcherService;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== JSON RESPONSE METHODS ==========

    public AIProjectSuggestionResponse suggestForNewProject(Long userId, AIProjectAnalysisRequest request) {
        String prompt = buildNewProjectPrompt(userId, request);
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return parseAIResponse(aiResponse);
    }

    public AIProjectSuggestionResponse analyzeExistingProject(Long userId, Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String prompt = buildExistingProjectPrompt(userId, project);
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return parseAIResponse(aiResponse);
    }

    public String suggestConsultantForTask(Long userId, AITaskSuggestionRequest request) {
        String prompt = buildTaskSuggestionPrompt(userId, request);
        return ollamaApiService.generateResponse(prompt);
    }

    public ConsultantProfileResponse getConsultantProfile(Long loggedInUserId, Long consultantId) {
        String prompt = buildConsultantProfilePrompt(loggedInUserId, consultantId);
        String aiResponse = ollamaApiService.generateResponse(prompt);

        ConsultantDetails consultant = aiDataFetcherService.getConsultantById(consultantId);
        String consultantName = consultant != null && consultant.getUser() != null ? consultant.getUser().getName() : "Unknown";

        return ConsultantProfileResponse.builder()
                .text(aiResponse)
                .consultantId(consultantId)
                .consultantName(consultantName)
                .build();
    }

    // ========== TEXT RESPONSE METHODS (For Frontend) ==========

    public AITextResponse suggestForNewProjectAsText(Long userId, AIProjectAnalysisRequest request) {
        String prompt = buildNewProjectPromptAsText(userId, request);
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return new AITextResponse(aiResponse);
    }

    public AITextResponse analyzeExistingProjectAsText(Long userId, Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        String prompt = buildExistingProjectPromptAsText(userId, project);
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return new AITextResponse(aiResponse);
    }

    public AITextResponse suggestConsultantForTaskAsText(Long userId, AITaskSuggestionRequest request) {
        String prompt = buildTaskSuggestionPromptAsText(userId, request);
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return new AITextResponse(aiResponse);
    }

    public AITextResponse getConsultantProfileAsText(Long loggedInUserId) {
        ConsultantDetails consultant = aiDataFetcherService.getConsultantByUserId(loggedInUserId);

        if (consultant == null) {
            return new AITextResponse("Consultant not found for this user");
        }

        String prompt = buildConsultantProfilePromptAsText(loggedInUserId, consultant.getConsultantId());
        String aiResponse = ollamaApiService.generateResponse(prompt);
        return new AITextResponse(aiResponse);
    }

    // ========== TEXT PROMPT BUILDERS ==========

    private String buildNewProjectPromptAsText(Long userId, AIProjectAnalysisRequest request) {
        return String.format("""
            You are an AI Project Management Assistant. Analyze this project and give response in plain text.
            
            PROJECT: %s - %s (%s)
            
            DEPARTMENTS: %s
            MANAGERS: %s
            CONSULTANTS: %s
            COMPLETED PROJECTS: %s
            PERFORMANCE: %s
            
            Give response in 2-3 sentences covering: department suggestion, manager suggestion, consultant suggestion, timeline, cost, risks.
            Return only plain text, no JSON.
            """,
                request.getProjectName(),
                request.getProjectDescription(),
                request.getProjectType(),
                aiDataFetcherService.getAllDepartmentsInfo(userId),
                aiDataFetcherService.getAllManagersInfo(userId),
                aiDataFetcherService.getAllConsultantsInfo(userId),
                aiDataFetcherService.getCompletedProjectsInfo(userId),
                aiDataFetcherService.getConsultantPerformance(userId)
        );
    }

    private String buildExistingProjectPromptAsText(Long userId, ProjectDetails project) {
        return String.format("""
            You are an AI Project Management Assistant. Analyze this existing project and give response in plain text.
            
            PROJECT: %s - %s (Status: %s)
            
            MANAGERS: %s
            CONSULTANTS: %s
            
            Give response in 2-3 sentences with suggestions for improvement.
            Return only plain text, no JSON.
            """,
                project.getProjectName(),
                project.getProjectDescription(),
                project.getStatus(),
                aiDataFetcherService.getAllManagersInfo(userId),
                aiDataFetcherService.getAllConsultantsInfo(userId)
        );
    }

    private String buildTaskSuggestionPromptAsText(Long userId, AITaskSuggestionRequest request) {
        return String.format("""
            You are an AI Task Assignment Assistant.
            
            TASK: %s - %s
            
            CONSULTANTS: %s
            
            Suggest best consultant. Return consultant name, ID and reason in plain text.
            Return only plain text, no JSON.
            """,
                request.getTaskName(),
                request.getTaskDescription(),
                aiDataFetcherService.getAllConsultantsInfo(userId)
        );
    }

    private String buildConsultantProfilePromptAsText(Long loggedInUserId, Long consultantId) {
        return String.format("""
            You are an AI Assistant. Generate a detailed profile summary for this consultant in 2-3 sentences.
            
            BASIC INFO: %s
            TASKS: %s
            PROJECTS: %s
            PERFORMANCE: %s
            TEAM: %s
            
            Return only plain text summary covering name, department, skills, tasks completed, current projects, rating, and team.
            """,
                aiDataFetcherService.getConsultantBasicInfo(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantTasks(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantProjects(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantPerformanceMetrics(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantTeamInfo(loggedInUserId, consultantId)
        );
    }

    // ========== JSON PROMPT BUILDERS ==========

    private String buildNewProjectPrompt(Long userId, AIProjectAnalysisRequest request) {
        return String.format("""
            You are an AI Project Management Assistant. Analyze this project.
            
            PROJECT: %s - %s (%s)
            
            DEPARTMENTS: %s
            MANAGERS: %s
            CONSULTANTS: %s
            COMPLETED PROJECTS: %s
            PERFORMANCE: %s
            
            Return JSON only with: projectAnalysis, suggestedDepartment, suggestedManager, suggestedConsultants, suggestedTasks, timeline, costEstimation, risks
            """,
                request.getProjectName(),
                request.getProjectDescription(),
                request.getProjectType(),
                aiDataFetcherService.getAllDepartmentsInfo(userId),
                aiDataFetcherService.getAllManagersInfo(userId),
                aiDataFetcherService.getAllConsultantsInfo(userId),
                aiDataFetcherService.getCompletedProjectsInfo(userId),
                aiDataFetcherService.getConsultantPerformance(userId)
        );
    }

    private String buildExistingProjectPrompt(Long userId, ProjectDetails project) {
        return String.format("""
            You are an AI Project Management Assistant. Analyze this existing project.
            
            PROJECT: %s - %s (Status: %s)
            
            MANAGERS: %s
            CONSULTANTS: %s
            
            Return JSON with suggestions for improvement.
            """,
                project.getProjectName(),
                project.getProjectDescription(),
                project.getStatus(),
                aiDataFetcherService.getAllManagersInfo(userId),
                aiDataFetcherService.getAllConsultantsInfo(userId)
        );
    }

    private String buildTaskSuggestionPrompt(Long userId, AITaskSuggestionRequest request) {
        return String.format("""
            You are an AI Task Assignment Assistant.
            
            TASK: %s - %s
            
            CONSULTANTS: %s
            
            Suggest best consultant. Return consultant ID and reason.
            """,
                request.getTaskName(),
                request.getTaskDescription(),
                aiDataFetcherService.getAllConsultantsInfo(userId)
        );
    }

    private String buildConsultantProfilePrompt(Long loggedInUserId, Long consultantId) {
        return String.format("""
            You are an AI Assistant. Generate a detailed profile summary for this consultant in 2-3 sentences.
            
            BASIC INFO: %s
            TASKS: %s
            PROJECTS: %s
            PERFORMANCE: %s
            TEAM: %s
            
            Return only plain text summary covering name, department, skills, tasks completed, current projects, rating, and team.
            """,
                aiDataFetcherService.getConsultantBasicInfo(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantTasks(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantProjects(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantPerformanceMetrics(loggedInUserId, consultantId),
                aiDataFetcherService.getConsultantTeamInfo(loggedInUserId, consultantId)
        );
    }

    // ========== PARSER ==========

    private AIProjectSuggestionResponse parseAIResponse(String aiResponse) {
        AIProjectSuggestionResponse response = new AIProjectSuggestionResponse();
        try {
            int start = aiResponse.indexOf("{");
            int end = aiResponse.lastIndexOf("}");
            if (start != -1 && end != -1) {
                String jsonStr = aiResponse.substring(start, end + 1);
                response = objectMapper.readValue(jsonStr, AIProjectSuggestionResponse.class);
            }
        } catch (Exception e) {
            ProjectAnalysisDTO analysis = new ProjectAnalysisDTO();
            analysis.setComplexity("Medium");
            analysis.setEstimatedDuration("2-3 months");
            response.setProjectAnalysis(analysis);
            response.setRawAIResponse(aiResponse);
        }
        return response;
    }
}