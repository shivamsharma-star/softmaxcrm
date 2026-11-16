package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryDTO {
    private Double totalRevenue;
    private Double totalExpenses;
    private Double netProfit;
    private Integer pendingApprovals;
    private Integer totalTransactions;
    private Integer approvedTransactions;
}