package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectCreateRequest {
    private String projectName;
    private String projectDescription;
    private String projectType;
    private Long departmentId;
    private String cost;
}