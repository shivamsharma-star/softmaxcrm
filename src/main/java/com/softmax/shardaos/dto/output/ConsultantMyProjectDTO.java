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
public class ConsultantMyProjectDTO {
    private Long projectId;
    private String projectName;
    private String projectDescription;
    private String projectType;
    private String status;
    private String departmentName;
    private String clientName;
    private String managerName;
    private LocalDateTime deadline;
    private List<FileResponseDTO> files;
    // ❌ NO cost field
    // ❌ NO history field
}