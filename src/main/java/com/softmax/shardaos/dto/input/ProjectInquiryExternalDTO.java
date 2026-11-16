package com.softmax.shardaos.dto.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectInquiryExternalDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;      // optional

    @NotBlank(message = "Project name is required")
    private String projectName;

    private String projectDescription;
    private String projectType;      // WEB_APP / MOBILE_APP / etc.
    private String budgetRange;      // "10k-50k" etc. (free text, optional)
    private String timeline;         // "1 month", "urgent" (optional)
}