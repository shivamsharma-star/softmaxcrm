package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanySummaryDTO {
    private int totalEmployees;
    private int totalManagers;
    private int totalConsultants;
    private int totalClients;
    private int totalProjects;
    private int completedProjects;
    private int inProgressProjects;
    private int pendingProjects;
    private double overallProjectCompletionRate;
    private double totalRevenue;
    private double totalExpenses;
    private double netProfit;
}