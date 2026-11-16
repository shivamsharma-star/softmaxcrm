package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class EmailResponseDTO {
    private int totalRecipients;
    private int successCount;
    private int failedCount;
    private List<String> failedEmails;
    private String message;
}