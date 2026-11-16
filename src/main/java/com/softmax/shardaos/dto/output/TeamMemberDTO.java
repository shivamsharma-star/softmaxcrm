package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TeamMemberDTO {
    private Long memberId;
    private String memberName;
    private String memberEmail;
    private String photoUrl;
    private String role;
    private String description;
}