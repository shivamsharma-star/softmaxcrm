package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import com.softmax.shardaos.models.ConsultantDetails;

@Getter
@Builder
@AllArgsConstructor
public class ConsultantApplicationResponse {
    private String message;
    private UserDTO user;
    private ConsultantDetails consultantDetails;
    private RoleDTO role;
}