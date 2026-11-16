package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class TeamCreateRequest {
    private Long projectId;
    private String teamName;
    private List<Long> memberIds; // Consultant IDs
    private List<String> memberRoles;
}