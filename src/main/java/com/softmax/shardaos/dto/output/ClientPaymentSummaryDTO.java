package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ClientPaymentSummaryDTO {
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private Integer totalProjects;
    private Double totalBudget;
    private Double totalPaid;
    private Double totalRemaining;
    private List<ProjectPaymentSummaryDTO> projects;
}