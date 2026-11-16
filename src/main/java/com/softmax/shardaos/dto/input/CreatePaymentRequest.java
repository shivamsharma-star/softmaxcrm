package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentRequest {
    private String transactionId;
    private String amount;
    private String paymentNote;
    private Long projectId;      // For project payments
    private Long userId;         // For salary payments (employee)
    private String month;        // For salary payments
}