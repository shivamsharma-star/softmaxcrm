package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
public class PartnerResponseDTO {

    private Long partnerId;
    private Long userId;
    private String partnerCode;
    private String partnerName;
    private String partnerEmail;
    private String partnerPhotoUrl;

    // Company
    private String companyName;
    private String companyEmail;
    private String companyMobileNumber;
    private String companyWebsite;
    private String companyAddress;
    private String contactPersonName;
    private String contactPersonDesignation;

    // Services
    private Set<String> servicesOffered;

    // Agreement
    private String agreementType;
    private BigDecimal agreementValue;
    private String agreementPdfDriveLink;
    private String agreementStartDate;
    private String agreementEndDate;

    // Status
    private String partnerStatus;
    private Boolean isApproved;
    private String directorNote;

    // Stats
    private long totalClients;
    private long activeClients;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}