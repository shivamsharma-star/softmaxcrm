package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantRatingResponseDTO {
    private Long reviewId;
    private Long consultantId;
    private String consultantName;
    private Long reviewerId;
    private String reviewerName;
    private Double reviewMarks;
    private String reviewNote;
    private LocalDateTime createdAt;
}