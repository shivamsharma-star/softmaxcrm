package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovePaymentRequest {
    private String status;           // APPROVED, REJECTED
    private String directorNote;
}