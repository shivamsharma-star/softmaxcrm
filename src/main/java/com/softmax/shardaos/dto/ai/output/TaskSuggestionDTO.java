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
public class TaskSuggestionDTO {
    private String taskName;
    private String description;
    private Long suggestedConsultantId;
    private String suggestedConsultantName;
    private int estimatedHours;
    private String priority;
}