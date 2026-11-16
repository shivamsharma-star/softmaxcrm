package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private String phoneNumber;
    private String address;
    private String gender;
    private LocalDate dob;
    private String photoUrl;
}
