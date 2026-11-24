package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
public class PartnerSelfRegisterRequest {

    // ❌ User fields REMOVED — JWT se milenge
    // (uid, email, name, phoneNumber, gender, dob, photoUrl)

    // ----- Company -----
    private String companyName;
    private String companyEmail;
    private String companyMobileNumber;
    private String companyWebsite;
    private String companyAddress;
    private String contactPersonName;
    private String contactPersonDesignation;

    // ----- Services -----
    // Allowed: CLIENT_RELATIONSHIP, SELLING, SECURITY, MARKETING, SUPPORT, DEVELOPMENT, OTHER
    private Set<String> servicesOffered;

    // ----- Agreement -----
    // agreementType allowed: PERCENTAGE, MONTHLY, FIXED
    private String agreementType;
    private BigDecimal agreementValue;
    private String agreementPdfDriveLink;
    private String agreementStartDate;
    private String agreementEndDate;

    // ----- Internal -----
    private String internalNotes;
}