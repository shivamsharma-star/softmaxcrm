package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateConsultantStatusRequest {
    private String status;  // APPLIED, DOCUMENT_VERIFIED, INTERVIEW_SCHEDULED, INTERVIEW_PASSED, INTERVIEW_FAILED, APPROVED, REJECTED, ACTIVE, INACTIVE, BLOCKED
    private String note;
}