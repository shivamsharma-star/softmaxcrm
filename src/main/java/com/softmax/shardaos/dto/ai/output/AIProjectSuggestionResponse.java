package com.softmax.shardaos.dto.ai.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProjectSuggestionResponse {
    private ProjectAnalysisDTO projectAnalysis;
    private DepartmentSuggestionDTO suggestedDepartment;
    private ManagerSuggestionDTO suggestedManager;
    private List<ConsultantSuggestionDTO> suggestedConsultants;
    private List<TaskSuggestionDTO> suggestedTasks;
    private TimelineSuggestionDTO timeline;
    private RiskAssessmentDTO risks;
    private CostEstimationDTO costEstimation;
    private String rawAIResponse;
}