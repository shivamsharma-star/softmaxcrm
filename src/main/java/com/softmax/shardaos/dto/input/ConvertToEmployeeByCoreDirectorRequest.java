package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConvertToEmployeeByCoreDirectorRequest {
    private String positionName;
    private Double salary;
    private String skillSet;
}