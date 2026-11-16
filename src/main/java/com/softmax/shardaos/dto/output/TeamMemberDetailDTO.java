// TeamMemberDetailDTO.java
package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TeamMemberDetailDTO {
    private Long userId;
    private String name;
    private String email;
    private String photoUrl;
    private String role;
    private Boolean isEmployee;
    private Boolean isConsultant;
}