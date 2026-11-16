// ProjectTeamDTO.java
package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ProjectTeamDTO {
    private Long teamId;
    private String teamName;
    private Long managerId;
    private String managerName;
    private List<TeamMemberDetailDTO> members;
}