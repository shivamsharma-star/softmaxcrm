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
public class ProjectAnalysisDTO {
    private String projectType;
    private String domain;
    private String complexity;
    private String estimatedDuration;
    private String keyTechnologies;
    private String recommendedTeamSize;
}