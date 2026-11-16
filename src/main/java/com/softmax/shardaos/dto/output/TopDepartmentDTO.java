package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopDepartmentDTO {
    private Long departmentId;
    private String departmentName;
    private double completionRate;
    private double revenue;
}