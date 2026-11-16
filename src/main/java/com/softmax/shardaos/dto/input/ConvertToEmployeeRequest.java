package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConvertToEmployeeRequest {
    private String positionName;
    private Double salary;
    private String skillSet;
    private Long departmentId;
    private String stype;  // ✅ ADDED - "MANAGER" or "DIRECTOR" or null
}