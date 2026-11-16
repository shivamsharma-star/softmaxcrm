package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MyDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    public MyDetailsResponseDTO getMyDetails(Long userId) {

        // 1. Get User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Create UserDTO with ALL fields
        UserDTO userDTO = new UserDTO(
                user.getUserId(),
                user.getUid(),
                user.getName(),
                user.getEmail(),
                user.getPhotoUrl(),
                user.getEmailVerified(),
                user.getIsProfileCompleted(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getGender(),
                user.getDob(),
                user.getFcmToken(),
                user.getIsDglockerVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getProfileCreatedAt()
        );

        // 3. Get ALL Roles (Multiple roles possible)
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);

        List<RoleDTO> roleDTOList = userTypes.stream()
                .map(ut -> new RoleDTO(
                        ut.getId(),
                        ut.getPtype(),
                        ut.getStype(),
                        ut.getIsapproved(),
                        ut.getCreatedAt()
                ))
                .collect(Collectors.toList());

        // 4. If user has EMP role, get employee details else null
        EmployeeDTO employeeDTO = null;
        boolean isEmployee = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));

        if (isEmployee) {
            EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);

            if (employee != null) {
                Long departmentId = null;
                String departmentName = null;
                if (employee.getDepartmentRef() != null) {
                    departmentId = employee.getDepartmentRef().getDepartmentId();
                    departmentName = employee.getDepartmentRef().getDepartmentName();
                }

                employeeDTO = EmployeeDTO.builder()
                        .employeeId(employee.getEmployeeId())
                        .isDirectorVerified(employee.getIsDirectorVerified())
                        .positionName(employee.getPositionName())
                        .salary(employee.getSalary())
                        .department(employee.getDepartment())
                        .skillSet(employee.getSkillSet())
                        .departmentId(departmentId)
                        .departmentName(departmentName)
                        .build();
            }
        }

        return MyDetailsResponseDTO.builder()
                .user(userDTO)
                .roles(roleDTOList)
                .employee(employeeDTO)
                .build();
    }
}