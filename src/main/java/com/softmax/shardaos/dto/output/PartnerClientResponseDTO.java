package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PartnerClientResponseDTO {

    private Long mappingId;
    private Long clientId;
    private Long userId;

    private String name;
    private String email;
    private String phoneNumber;

    private String companyName;
    private String companyEmail;
    private String clientStatus;
    private Boolean isApproved;

    private LocalDateTime assignedAt;
    private String mappingStatus;
    private String notes;
}