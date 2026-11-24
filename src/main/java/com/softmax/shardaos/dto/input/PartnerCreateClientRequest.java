package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartnerCreateClientRequest {

    // ----- Login / user info -----
    private String email;           // required
    private String name;            // required
    private String phoneNumber;
    private String gender;
    private String dob;             // yyyy-MM-dd
    private String photoUrl;
    private String address;

    // ----- Company info -----
    private String companyName;     // required
    private String companyMobileNumber;
    private String companyEmail;
    private String orgType;
    private String clientType;
    private String clientWebsite;

    // ----- Partner notes -----
    private String notes;
}