package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentApprovalCountDTO {
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
}