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
public class ConsultantSuggestionDTO {
    private Long consultantId;
    private String name;
    private String email;
    private String skill;
    private String experience;
    private double rating;
    private int currentLoad;
    private String reason;
}