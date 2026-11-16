package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentKPISummaryDTO {
    private Long departmentId;
    private String departmentName;
    private int employeeCount;
    private int managerCount;
    private int projectCount;
    private int completedProjects;
    private double completionRate;
    private double departmentBudget;
    private double departmentExpenses;
}