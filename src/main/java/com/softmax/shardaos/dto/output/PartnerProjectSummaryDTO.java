package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PartnerProjectSummaryDTO {

    private Long projectId;
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String status;              // PENDING, IN_PROGRESS, COMPLETED, etc.
    private String cost;
    private LocalDateTime deadline;

    private Long departmentId;
    private String departmentName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}