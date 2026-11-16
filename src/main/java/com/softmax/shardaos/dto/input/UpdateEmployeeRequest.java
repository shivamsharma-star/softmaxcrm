// UpdateEmployeeRequest.java
package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmployeeRequest {
    private String positionName;
    private Double salary;
    private String skillSet;
    private Long departmentId;
}