package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientApplyRequest {
    private String companyName;
    private String companyMobileNumber;
    private String companyEmail;
    private String orgType;
    private String clientType;
    private String clientWebsite;
}