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
public class ProjectRequestResponseDTO {
    private Long requestId;
    private Long projectId;
    private String projectName;
    private Long userId;
    private String userName;
    private String userEmail;
    private LocalDateTime createdAt;
}