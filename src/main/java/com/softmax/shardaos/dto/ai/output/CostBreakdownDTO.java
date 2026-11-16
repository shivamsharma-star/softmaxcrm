package com.softmax.shardaos.dto.ai.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdownDTO {
    private double development;
    private double design;
    private double testing;
    private double projectManagement;
}