package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectAssignRequest {
    private Long managerId;
    private String directorNote;
    private LocalDateTime deadline;
    private String cost;  // ✅ ADD THIS LINE
}