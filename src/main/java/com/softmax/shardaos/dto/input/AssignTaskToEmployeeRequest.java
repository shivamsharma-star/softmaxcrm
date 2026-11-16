package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class AssignTaskToEmployeeRequest {
    private Long assignedToUserId;
    private String description;
    private LocalDateTime deadline;
}