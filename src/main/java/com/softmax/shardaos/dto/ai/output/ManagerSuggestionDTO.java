package com.softmax.shardaos.dto.ai.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerSuggestionDTO {
    private Long managerId;
    private String managerName;
    private double performanceScore;
    private int projectsCompleted;
    private double averageRating;
    private String reason;
}