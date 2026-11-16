// ConsultantProjectMappingDTO.java
package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantProjectMappingDTO {
    private Long consultantId;
    private String consultantName;
    private Boolean isAssignedToAnyProject;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String roleInTeam;
}