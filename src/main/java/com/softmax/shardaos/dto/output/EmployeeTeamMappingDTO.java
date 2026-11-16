// EmployeeTeamMappingDTO.java
package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class EmployeeTeamMappingDTO {
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private String role;
}