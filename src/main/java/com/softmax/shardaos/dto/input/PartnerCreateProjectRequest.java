package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PartnerCreateProjectRequest {

    private String projectName;          // required
    private String projectDescription;
    private String projectType;
    private Long departmentId;
    private String cost;
    private LocalDateTime deadline;
}