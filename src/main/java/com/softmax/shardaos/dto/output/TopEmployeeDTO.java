package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopEmployeeDTO {
    private Long userId;
    private String name;
    private String position;
    private String department;
    private int tasksCompleted;
    private double averageRating;
}