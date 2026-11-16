// ProjectTeamDetailDTO.java
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
public class ProjectTeamDetailDTO {
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private List<ProjectTeamDTO> teams;
}