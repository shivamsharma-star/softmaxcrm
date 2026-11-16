package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllUsersResponseDTO {
    private List<UserWithRolesDTO> users;
    private int totalCount;
    private int employeeCount;
    private int consultantCount;
    private int clientCount;
}