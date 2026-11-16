package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPaymentStatsDTO {
    private PaymentSummaryDTO summary;
    private List<MonthlyRevenueDTO> monthlyRevenue;
    private List<PaymentResponseDTO> recentTransactions;
    private List<ProjectPaymentSummaryDTO> topProjects;
    private Map<String, Double> revenueByType;
}