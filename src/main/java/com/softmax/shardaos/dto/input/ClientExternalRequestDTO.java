package com.softmax.shardaos.dto.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClientExternalRequestDTO {

    private String uid;
    private String photoUrl;
    private String phoneNumber;
    private String gender;
    private String dob;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Company mobile is required")
    private String companyMobileNumber;

    @NotBlank(message = "Company email is required")
    @Email(message = "Invalid company email")
    private String companyEmail;

    private String orgType;
    private String clientType;
    private String clientWebsite;
}