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
public class ProjectResponseDTO {
    private Long projectId;
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String status;
    private Long departmentId;
    private String departmentName;
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private Long assignedManagerId;
    private String assignedManagerName;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String cost;
}