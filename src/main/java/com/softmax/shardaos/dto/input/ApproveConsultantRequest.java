package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveConsultantRequest {
    private String isApproved; // APPROVED, REJECTED, PENDING
    private String note;
}