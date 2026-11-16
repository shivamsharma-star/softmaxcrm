package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantProjectResponseDTO {
    private Long projectId;
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String status;
    private Long departmentId;
    private String departmentName;
    private Long clientId;
    private String clientName;
    private Long managerId;
    private String managerName;
    private Boolean hasRequested;
    private Boolean isAssigned;
}