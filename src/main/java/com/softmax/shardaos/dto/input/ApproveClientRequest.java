package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveClientRequest {
    private Boolean isApproved;
    private String rejectionReason;
}