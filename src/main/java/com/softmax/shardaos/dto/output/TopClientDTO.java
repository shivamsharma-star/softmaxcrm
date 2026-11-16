package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopClientDTO {
    private Long clientId;
    private String companyName;
    private int totalProjects;
    private double totalSpent;
}