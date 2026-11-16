package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientPaymentDashboardDTO {
    private int totalProjects;
    private String totalProjectCost;
    private String totalPaidAmount;
    private String totalRemainingAmount;
    private double overallPaidPercentage;
    private int completedProjects;
    private int pendingProjects;
    private List<ProjectPaymentSummaryDTO> projects;
}