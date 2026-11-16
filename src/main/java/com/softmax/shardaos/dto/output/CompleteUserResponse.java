package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CompleteUserResponse {
    private UserDTO user;
    private List<RoleDTO> roles;
}