package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SendEmailRequest {
    private List<Long> userIds;      // Recipients user IDs
    private String subject;
    private String message;
    private String messageType;       // "INFORMATION", "ALERT", "TASK", "MEETING"
}