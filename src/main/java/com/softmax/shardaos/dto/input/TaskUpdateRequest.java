package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskUpdateRequest {
    private String taskDetails;
    private String description;
    private String taskStatus;
    private String managerNote;
}