// EmployeeProjectMappingDTO.java
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
public class EmployeeProjectMappingDTO {
    private Long employeeId;
    private String employeeName;
    private List<ProjectResponseDTO> managedProjects;
    private List<EmployeeTeamMappingDTO> teams;
}