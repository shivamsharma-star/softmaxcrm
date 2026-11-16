package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserDTO {

    private Long userId;
    private String uid;
    private String name;
    private String email;
    private String photoUrl;
    private Boolean emailVerified;
    private Boolean isProfileCompleted;

    // ✅ ADD THESE FIELDS
    private String phoneNumber;
    private String address;
    private String gender;
    private LocalDate dob;
    private String fcmToken;
    private Boolean isDglockerVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate profileCreatedAt;
}