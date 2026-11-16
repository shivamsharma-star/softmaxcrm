package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class EmployeeDTO {
    private Long employeeId;
    private Long userId;           // ✅ ADDED
    private String name;           // ✅ ADDED
    private String email;          // ✅ ADDED
    private String photoUrl;       // ✅ ADDED
    private String phoneNumber;    // ✅ ADDED
    private String address;        // ✅ ADDED
    private String gender;         // ✅ ADDED
    private Boolean isDirectorVerified;
    private String positionName;
    private Double salary;
    private String department;
    private String skillSet;
    private Long departmentId;
    private String departmentName;
//    private String stype;
}