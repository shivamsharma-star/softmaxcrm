package com.softmax.shardaos.dto.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConsultantExternalRequestDTO {

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

    private String resumeLink;
    private String skill;
    private String skillType;
    private String stream;
    private String experience;
    private String ctc;

    private Double marks10;
    private Double marks12;
    private Double graduationMarks;

    private Long departmentId;
}