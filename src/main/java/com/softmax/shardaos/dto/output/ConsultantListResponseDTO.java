package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantListResponseDTO {
    private Long consultantId;
    private Long userId;
    private String name;
    private String email;
    private String photoUrl;
    private String skill;
    private String experience;
    private String currentStatus;  // Latest status from ConsultantStatus table
    private Long departmentId;
    private String departmentName;
    private String isApproved;  // From UserType
}