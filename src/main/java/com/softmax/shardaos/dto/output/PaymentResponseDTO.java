package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {
    private Long paymentId;
    private String transactionId;
    private String amount;
    private String type;
    private String status;
    private String userName;
    private String userEmail;
    private String paymentNote;
    private String directorNote;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime dateTime;
}