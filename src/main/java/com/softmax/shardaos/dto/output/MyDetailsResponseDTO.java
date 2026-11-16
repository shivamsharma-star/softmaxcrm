package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class MyDetailsResponseDTO {
    private UserDTO user;
    private List<RoleDTO> roles;      // ✅ Array of roles (multiple)
    private EmployeeDTO employee;      // null if not employee
}