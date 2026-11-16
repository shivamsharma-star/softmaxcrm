package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTaskStatusRequest {
    private String status;  // PENDING, IN_PROGRESS, COMPLETED
    private String note;
}