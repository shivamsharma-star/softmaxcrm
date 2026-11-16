package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCompanyTrendDTO {
    private String month;
    private int newEmployees;
    private int newClients;
    private int newProjects;
    private int projectsCompleted;
    private double revenue;
    private double expenses;
}