package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TaskResponseDTO {
    private Long taskId;
    private Long projectId;
    private String projectName;
    private Long assignedToUserId;
    private String assignedToUserName;
    private String assignedToUserPhoto;
    private String taskDetails;
    private String description;
    private String taskStatus;
    private Long createdById;
    private String createdByName;
    private LocalDateTime deadline;
    private String managerNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}