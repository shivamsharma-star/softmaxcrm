package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class HeadDirectorKPIDTO {
    private CompanySummaryDTO companySummary;
    private List<DepartmentKPISummaryDTO> departmentKPISummary;
    private List<MonthlyCompanyTrendDTO> monthlyTrends;
    private List<RecentActivityDTO> recentActivities;
    private TopPerformersDTO topPerformers;
//    private FinancialSummaryDTO financialSummary;
}