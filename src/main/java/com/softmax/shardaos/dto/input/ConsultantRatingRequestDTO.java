package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultantRatingRequestDTO {
    private Long consultantId;
    private Double rating;  // 1.0 to 5.0
    private String feedback;
}