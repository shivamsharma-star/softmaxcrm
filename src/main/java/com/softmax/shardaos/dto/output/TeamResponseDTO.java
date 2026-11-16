package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TeamResponseDTO {
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private Long managerId;
    private String managerName;
    private List<TeamMemberDTO> members;
    private LocalDateTime createdAt;
}