package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignDepartmentToClientRequest {
    private Long departmentId;
    private Long assignedBDEId;
}