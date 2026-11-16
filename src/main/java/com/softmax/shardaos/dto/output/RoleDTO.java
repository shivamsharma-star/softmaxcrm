package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class RoleDTO {

    private Long id;
    private String ptype;
    private String stype;
    private String isApproved;
    private LocalDateTime createdAt;
}