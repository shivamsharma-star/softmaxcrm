package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ProjectAssignRequest;
import com.softmax.shardaos.dto.input.ProjectCreateRequest;
import com.softmax.shardaos.dto.input.ProjectUpdateRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.ProjectResponseDTO;
import com.softmax.shardaos.dto.output.ProjectStatusDTO;
import com.softmax.shardaos.service.ProjectManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

    @Autowired
    private ProjectManagementService projectService;

    // ==================== CLIENT APIs ====================

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> createProject(
            @RequestAttribute("userId") Long userId,
            @RequestBody ProjectCreateRequest request) {
        try {
            ProjectResponseDTO project = projectService.createProject(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Project created successfully", project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/update/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> updateProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestBody ProjectUpdateRequest request) {
        try {
            ProjectResponseDTO project = projectService.updateProject(userId, projectId, request);
            return ResponseEntity.ok(ApiResponse.success("Project updated successfully", project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            projectService.deleteProject(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getMyProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = projectService.getMyProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> getProjectById(
            @PathVariable Long projectId) {
        try {
            ProjectResponseDTO project = projectService.getProjectById(projectId);
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== DIRECTOR APIs ====================

    @GetMapping("/director/department-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getDepartmentProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = projectService.getDepartmentProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/director/pending-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getPendingProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = projectService.getPendingProjectsForDirector(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/director/assign/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> assignProjectToManager(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestBody ProjectAssignRequest request) {
        try {
            ProjectResponseDTO project = projectService.assignProjectToManager(userId, projectId, request);
            return ResponseEntity.ok(ApiResponse.success("Project assigned successfully", project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== MANAGER APIs ====================

    @GetMapping("/manager/my-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getMyAssignedProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = projectService.getMyAssignedProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/manager/update-status/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> updateProjectStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        try {
            ProjectResponseDTO project = projectService.updateProjectStatus(userId, projectId, status, note);
            return ResponseEntity.ok(ApiResponse.success("Project status updated to " + status, project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== STATUS HISTORY API (User can view) ====================

    @GetMapping("/{projectId}/history")
    public ResponseEntity<ApiResponse<List<ProjectStatusDTO>>> getProjectHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<ProjectStatusDTO> history = projectService.getProjectStatusHistory(projectId, userId);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== CHECK APIs ====================

    @GetMapping("/check/director")
    public ResponseEntity<ApiResponse<Boolean>> checkIsDirector(
            @RequestAttribute("userId") Long userId) {
        boolean isDirector = projectService.isDirector(userId);
        return ResponseEntity.ok(ApiResponse.success(isDirector));
    }

    @GetMapping("/check/manager")
    public ResponseEntity<ApiResponse<Boolean>> checkIsManager(
            @RequestAttribute("userId") Long userId) {
        boolean isManager = projectService.isManager(userId);
        return ResponseEntity.ok(ApiResponse.success(isManager));
    }
}