package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DepartmentPaymentSummaryDTO {
    private Long departmentId;
    private String departmentName;
    private Integer totalProjects;
    private Double totalBudget;
    private Double totalPaid;
    private Double totalRemaining;
    private List<ProjectPaymentSummaryDTO> projects;
}