package com.softmax.shardaos.dto.ai.input;

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
public class AIProjectAnalysisRequest {
    private String projectName;
    private String projectDescription;
    private String projectType;
}