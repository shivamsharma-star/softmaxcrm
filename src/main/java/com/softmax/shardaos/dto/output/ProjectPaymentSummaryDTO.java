package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPaymentSummaryDTO {
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
    private String assignedManagerName;
    private String deadline;
    private String totalProjectAmount;
    private String paidAmount;
    private String remainingAmount;
    private Double paidPercentage;
    private Boolean isFullyPaid;
    private List<PaymentResponseDTO> payments;
}