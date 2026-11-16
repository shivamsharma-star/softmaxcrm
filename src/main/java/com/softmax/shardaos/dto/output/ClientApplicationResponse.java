package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import com.softmax.shardaos.models.ClientDetails;

@Getter
@Builder
@AllArgsConstructor
public class ClientApplicationResponse {
    private String message;
    private UserDTO user;
    private ClientDetails clientDetails;
    private RoleDTO role;
}