package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.UpdateProfileRequest;
import com.softmax.shardaos.dto.output.CompleteUserResponse;
import com.softmax.shardaos.dto.output.RoleDTO;
import com.softmax.shardaos.dto.output.UserDTO;
import com.softmax.shardaos.models.User;
import com.softmax.shardaos.models.UserType;
import com.softmax.shardaos.repository.UserRepository;
import com.softmax.shardaos.repository.UserTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    /**
     * Get complete user data with roles (like signin response)
     */
    public CompleteUserResponse getCompleteUserProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);

        // ✅ Convert to DTO with ALL fields
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

        List<RoleDTO> roleDTOs = userTypes.stream()
                .map(role -> new RoleDTO(
                        role.getId(),
                        role.getPtype(),
                        role.getStype(),
                        role.getIsapproved(),
                        role.getCreatedAt()
                ))
                .toList();

        return CompleteUserResponse.builder()
                .user(userDTO)
                .roles(roleDTOs)
                .build();
    }

    /**
     * Get basic user profile with ALL fields
     */
    public UserDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // ✅ Return ALL fields
        return new UserDTO(
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
    }

    /**
     * Update user profile
     */
    @Transactional
    public UserDTO updateUserProfile(Long userId, UpdateProfileRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (request.getName() != null && !request.getName().isEmpty()) {
            user.setName(request.getName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getDob() != null) {
            user.setDob(request.getDob());
        }
        if (request.getPhotoUrl() != null) {
            user.setPhotoUrl(request.getPhotoUrl());
        }

        boolean isComplete = isProfileComplete(user);
        user.setIsProfileCompleted(isComplete);

        User updatedUser = userRepository.save(user);

        // ✅ Return ALL fields after update
        return new UserDTO(
                updatedUser.getUserId(),
                updatedUser.getUid(),
                updatedUser.getName(),
                updatedUser.getEmail(),
                updatedUser.getPhotoUrl(),
                updatedUser.getEmailVerified(),
                updatedUser.getIsProfileCompleted(),
                updatedUser.getPhoneNumber(),
                updatedUser.getAddress(),
                updatedUser.getGender(),
                updatedUser.getDob(),
                updatedUser.getFcmToken(),
                updatedUser.getIsDglockerVerified(),
                updatedUser.getCreatedAt(),
                updatedUser.getUpdatedAt(),
                updatedUser.getProfileCreatedAt()
        );
    }

    @Transactional
    public void deleteUserAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    private boolean isProfileComplete(User user) {
        return user.getName() != null && !user.getName().isEmpty()
                && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()
                && user.getAddress() != null && !user.getAddress().isEmpty()
                && user.getGender() != null
                && user.getDob() != null;
    }
}