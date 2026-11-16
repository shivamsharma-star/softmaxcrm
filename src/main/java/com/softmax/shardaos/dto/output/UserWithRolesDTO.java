package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserWithRolesDTO {
    private Long userId;
    private String name;
    private String email;
    private String photoUrl;
    private String phoneNumber;
    private String address;
    private String gender;
    private List<String> roles;
    private String department;
    private String position;
    private Boolean isActive;
    private LocalDateTime joinedAt;
}