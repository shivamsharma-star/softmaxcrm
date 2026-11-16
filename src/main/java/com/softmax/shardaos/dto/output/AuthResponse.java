package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private UserDTO user;
    private List<RoleDTO> roles;
    // ❌ Token removed - cookie mein store hoga
}