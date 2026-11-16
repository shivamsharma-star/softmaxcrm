package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class MonthlyFinancialTrendDTO {
    private String month;
    private double revenue;
    private double expenses;
    private double profit;
}