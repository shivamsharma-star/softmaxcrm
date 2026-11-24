package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class PartnerProjectDetailDTO {

    private Long projectId;
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String status;
    private String cost;
    private LocalDateTime deadline;
    private Long departmentId;
    private String departmentName;

    private Long clientId;
    private Long clientUserId;
    private String clientName;
    private String clientEmail;
    private String clientCompanyName;

    private Long assignedManagerId;
    private String assignedManagerName;
    private String assignedManagerEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<ProjectStatusDTO> history;

    // ✅ Ye use karo — entity nahi, DTO
    private List<PaymentResponseDTO> payments;
}