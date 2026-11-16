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
public class EmployeeTaskResponseDTO {
    private Long taskId;
    private String description;
    private String status;
    private String priority;
    private Long createdByUserId;
    private String createdByName;
    private String createdByPhoto;
    private LocalDateTime deadline;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}