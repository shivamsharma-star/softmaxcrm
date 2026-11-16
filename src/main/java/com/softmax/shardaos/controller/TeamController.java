package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.TeamCreateRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.TeamResponseDTO;
import com.softmax.shardaos.service.TeamManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    @Autowired
    private TeamManagementService teamService;

    /**
     * Create a new team (Manager only)
     * POST /api/team/create
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<TeamResponseDTO>> createTeam(
            @RequestAttribute("userId") Long userId,
            @RequestBody TeamCreateRequest request) {
        try {
            System.out.println("🔍 [CONTROLLER] ========== CREATE TEAM API CALLED ==========");
            System.out.println("📌 [CONTROLLER] userId: " + userId);
            System.out.println("📌 [CONTROLLER] request: " + request);
            System.out.println("📌 [CONTROLLER] projectId: " + (request != null ? request.getProjectId() : "null"));
            System.out.println("📌 [CONTROLLER] teamName: " + (request != null ? request.getTeamName() : "null"));
            System.out.println("📌 [CONTROLLER] memberIds: " + (request != null ? request.getMemberIds() : "null"));
            System.out.println("📌 [CONTROLLER] memberRoles: " + (request != null ? request.getMemberRoles() : "null"));

            TeamResponseDTO team = teamService.createTeam(userId, request);

            System.out.println("✅ [CONTROLLER] Team created successfully with ID: " + team.getTeamId());
            System.out.println("🔍 [CONTROLLER] ========== CREATE TEAM API END ==========");
            return ResponseEntity.ok(ApiResponse.success("Team created successfully", team));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error creating team: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Add member to team (Manager only)
     * POST /api/team/{teamId}/add-member?memberId=123&role=Developer
     */
    @PostMapping("/{teamId}/add-member")
    public ResponseEntity<ApiResponse<TeamResponseDTO>> addTeamMember(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long teamId,
            @RequestParam Long memberId,
            @RequestParam(required = false) String role) {
        try {
            System.out.println("🔍 [CONTROLLER] addTeamMember - userId: " + userId + ", teamId: " + teamId + ", memberId: " + memberId + ", role: " + role);

            TeamResponseDTO team = teamService.addTeamMember(userId, teamId, memberId, role);

            System.out.println("✅ [CONTROLLER] Member added successfully");
            return ResponseEntity.ok(ApiResponse.success("Member added successfully", team));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error adding member: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Remove member from team (Manager only)
     * DELETE /api/team/{teamId}/remove-member/{memberId}
     */
    @DeleteMapping("/{teamId}/remove-member/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeTeamMember(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        try {
            System.out.println("🔍 [CONTROLLER] removeTeamMember - userId: " + userId + ", teamId: " + teamId + ", memberId: " + memberId);

            teamService.removeTeamMember(userId, teamId, memberId);

            System.out.println("✅ [CONTROLLER] Member removed successfully");
            return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error removing member: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete team (Manager only)
     * DELETE /api/team/delete/{teamId}
     */
    @DeleteMapping("/delete/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long teamId) {
        try {
            System.out.println("🔍 [CONTROLLER] deleteTeam - userId: " + userId + ", teamId: " + teamId);

            teamService.deleteTeam(userId, teamId);

            System.out.println("✅ [CONTROLLER] Team deleted successfully");
            return ResponseEntity.ok(ApiResponse.success("Team deleted successfully", null));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error deleting team: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all teams of a project (Client/Manager/Director/Consultant)
     * GET /api/team/project/{projectId}
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getProjectTeams(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            System.out.println("🔍 [CONTROLLER] getProjectTeams - userId: " + userId + ", projectId: " + projectId);

            List<TeamResponseDTO> teams = teamService.getProjectTeams(userId, projectId);

            System.out.println("✅ [CONTROLLER] Found " + teams.size() + " teams");
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error getting project teams: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all teams created by me (Manager only)
     * GET /api/team/my-teams
     */
    @GetMapping("/my-teams")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getMyTeams(
            @RequestAttribute("userId") Long userId) {
        try {
            System.out.println("🔍 [CONTROLLER] getMyTeams - userId: " + userId);

            List<TeamResponseDTO> teams = teamService.getMyTeams(userId);

            System.out.println("✅ [CONTROLLER] Found " + teams.size() + " teams created by me");
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error getting my teams: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all teams where I am a member (Consultant only)
     * GET /api/team/my-consultant-teams
     */
    @GetMapping("/my-consultant-teams")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getMyConsultantTeams(
            @RequestAttribute("userId") Long userId) {
        try {
            System.out.println("🔍 [CONTROLLER] getMyConsultantTeams - userId: " + userId);

            List<TeamResponseDTO> teams = teamService.getMyConsultantTeams(userId);

            System.out.println("✅ [CONTROLLER] Found " + teams.size() + " teams where I am a member");
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            System.out.println("❌ [CONTROLLER] Error getting consultant teams: " + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}