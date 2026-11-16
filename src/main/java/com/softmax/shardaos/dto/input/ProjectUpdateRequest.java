package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectUpdateRequest {
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String cost;
}