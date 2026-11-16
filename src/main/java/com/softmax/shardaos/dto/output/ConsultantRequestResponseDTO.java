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
public class ConsultantRequestResponseDTO {
    private Long requestId;
    private Long projectId;
    private String projectName;
    private Long consultantId;
    private String consultantName;
    private String consultantEmail;
    private String consultantPhoto;
    private String skill;
    private String experience;
    private Double marks10;
    private Double marks12;
    private Double graduationMarks;
    private String resumeLink;
    private Double averageRating;
    private Integer totalReviews;
    private LocalDateTime requestedAt;
}