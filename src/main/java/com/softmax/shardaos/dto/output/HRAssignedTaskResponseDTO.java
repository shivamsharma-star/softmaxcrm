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
public class HRAssignedTaskResponseDTO {
    private Long taskId;
    private String description;
    private String status;
    private Long assignedToUserId;
    private String assignedToUserName;
    private String assignedToDepartment;
    private Long createdByUserId;
    private String createdByName;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}