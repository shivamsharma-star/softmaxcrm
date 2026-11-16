package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class TaskCreateRequest {
    private Long projectId;
    private Long assignedToUserId;
    private String taskDetails;
    private String description;
    private LocalDateTime deadline;
}