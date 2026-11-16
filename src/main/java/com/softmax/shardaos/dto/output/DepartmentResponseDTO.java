package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class DepartmentResponseDTO {
    private Long departmentId;
    private String departmentName;
    // ❌ Remove createdAt and updatedAt if not in model
}