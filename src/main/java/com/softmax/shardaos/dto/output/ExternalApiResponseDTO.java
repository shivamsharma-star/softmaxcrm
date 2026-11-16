package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalApiResponseDTO {

    private boolean success;
    private String message;

    private Long userId;
    private Long consultantId;
    private Long clientId;
    private Long projectId;

    private String ptype;
    private String isApproved;

    private String referenceId;      // For project inquiry (tracking, not stored in DB)
    private String emailSentTo;
}