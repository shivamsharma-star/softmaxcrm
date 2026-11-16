package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.UpdateProfileRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.CompleteUserResponse;
import com.softmax.shardaos.dto.output.UserDTO;
import com.softmax.shardaos.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    /**
     * API 1: Get complete user profile with roles
     * GET /api/users/complete
     */
    @GetMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteUserResponse>> getCompleteUserProfile(
            @RequestAttribute("userId") Long userId) {

        try {
            CompleteUserResponse response = userProfileService.getCompleteUserProfile(userId);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error fetching user profile: " + e.getMessage()));
        }
    }

    /**
     * API 2: Get basic user profile
     * GET /api/users/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(
            @RequestAttribute("userId") Long userId) {

        try {
            UserDTO userDTO = userProfileService.getUserProfile(userId);
            return ResponseEntity.ok(ApiResponse.success(userDTO));

        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error fetching profile: " + e.getMessage()));
        }
    }

    /**
     * API 3: Update user profile
     * PUT /api/users/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateUserProfile(
            @RequestBody UpdateProfileRequest request,
            @RequestAttribute("userId") Long userId) {

        try {
            UserDTO updatedUser = userProfileService.updateUserProfile(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updatedUser));

        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error updating profile: " + e.getMessage()));
        }
    }

    /**
     * API 4: Delete user account
     * DELETE /api/users/account
     */
    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Void>> deleteUserAccount(
            @RequestAttribute("userId") Long userId) {

        try {
            userProfileService.deleteUserAccount(userId);
            return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));

        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error deleting account: " + e.getMessage()));
        }
    }
}