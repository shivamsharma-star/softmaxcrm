package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopPerformersDTO {
    private List<TopEmployeeDTO> topEmployees;
    private List<TopClientDTO> topClients;
    private List<TopDepartmentDTO> topDepartments;
}