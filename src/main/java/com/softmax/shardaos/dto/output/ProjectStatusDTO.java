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
public class ProjectStatusDTO {
    private Long statusId;
    private Long projectId;
    private String projectName;
    private String status;
    private LocalDateTime dateTime;
    private Long handledById;
    private String handledByName;
    private Long assignedToId;
    private String assignedToName;
    private String directorNote;
    private String managerNote;
    private LocalDateTime deadline;
}