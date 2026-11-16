package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.*;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.service.CoreDirectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/core-director")
public class CoreDirectorController {

    @Autowired
    private CoreDirectorService coreDirectorService;

    // ==================== AUTH ====================

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Boolean>> check(@RequestAttribute("userId") Long userId) {
        boolean isDirector = coreDirectorService.isCoreDirector(userId);
        return ResponseEntity.ok(ApiResponse.success(isDirector));
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> stats = coreDirectorService.getDashboardStats(userId);
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== CONSULTANT MANAGEMENT ====================

    @GetMapping("/consultants")
    public ResponseEntity<ApiResponse<List<ConsultantListResponseDTO>>> getConsultants(@RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantListResponseDTO> consultants = coreDirectorService.getMyDepartmentConsultants(userId);
            return ResponseEntity.ok(ApiResponse.success(consultants));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/consultants/{consultantId}")
    public ResponseEntity<ApiResponse<ConsultantListResponseDTO>> getConsultantById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId) {
        try {
            ConsultantListResponseDTO consultant = coreDirectorService.getConsultantById(userId, consultantId);
            return ResponseEntity.ok(ApiResponse.success(consultant));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/consultants/{consultantId}/status-history")
    public ResponseEntity<ApiResponse<List<ConsultantStatusHistoryDTO>>> getConsultantStatusHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId) {
        try {
            List<ConsultantStatusHistoryDTO> history = coreDirectorService.getConsultantStatusHistory(userId, consultantId);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/consultants/{consultantId}/status")
    public ResponseEntity<ApiResponse<ConsultantStatusResponseDTO>> updateConsultantStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId,
            @RequestBody UpdateConsultantStatusRequest request) {
        try {
            ConsultantStatusResponseDTO status = coreDirectorService.updateConsultantStatus(userId, consultantId, request);
            return ResponseEntity.ok(ApiResponse.success("Consultant status updated successfully", status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/consultants/{consultantId}/convert-to-employee")
    public ResponseEntity<ApiResponse<String>> convertConsultantToEmployee(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId,
            @RequestBody ConvertToEmployeeByCoreDirectorRequest request) {
        try {
            String result = coreDirectorService.convertConsultantToEmployeeByCoreDirector(userId, consultantId, request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== EMPLOYEE MANAGEMENT (WITH NAMES) ====================

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<List<EmployeeDTO>>> getEmployees(@RequestAttribute("userId") Long userId) {
        try {
            List<EmployeeDTO> employees = coreDirectorService.getMyDepartmentEmployees(userId);
            return ResponseEntity.ok(ApiResponse.success(employees));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<ApiResponse<EmployeeDTO>> getEmployeeById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long employeeId) {
        try {
            EmployeeDTO employee = coreDirectorService.getEmployeeById(userId, employeeId);
            return ResponseEntity.ok(ApiResponse.success(employee));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== PROJECT MANAGEMENT ====================

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getProjects(@RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = coreDirectorService.getMyDepartmentProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/pending")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getPendingProjects(@RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = coreDirectorService.getPendingProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/projects/{projectId}/approve")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> approveProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        try {
            System.out.println("🔍 [CoreDirector] approveProject - userId: " + userId + ", projectId: " + projectId + ", status: " + status);
            ProjectResponseDTO project = coreDirectorService.approveProject(userId, projectId, status, note);
            String message = status.equals("ASSIGNED") ? "Project approved and ready for assignment" : "Project " + status.toLowerCase() + " successfully";
            return ResponseEntity.ok(ApiResponse.success(message, project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/projects/{projectId}/assign")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> assignProjectToManager(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestBody ProjectAssignRequest request) {
        try {
            System.out.println("🔍 [CoreDirector] assignProjectToManager - userId: " + userId + ", projectId: " + projectId + ", managerId: " + request.getManagerId());
            ProjectResponseDTO project = coreDirectorService.assignProjectToManager(userId, projectId, request);
            return ResponseEntity.ok(ApiResponse.success("Project assigned to manager successfully", project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> getProjectById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            ProjectResponseDTO project = coreDirectorService.getProjectById(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<ApiResponse<List<ProjectStatusDTO>>> getProjectHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<ProjectStatusDTO> history = coreDirectorService.getProjectHistory(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getProjectTeams(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<TeamResponseDTO> teams = coreDirectorService.getProjectTeams(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== TASK MANAGEMENT ====================

    @PostMapping("/tasks/assign")
    public ResponseEntity<ApiResponse<AssignedTaskResponseDTO>> assignTaskToEmployee(
            @RequestAttribute("userId") Long userId,
            @RequestBody AssignTaskToEmployeeRequest request) {
        try {
            AssignedTaskResponseDTO task = coreDirectorService.assignTaskToEmployee(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Task assigned successfully", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<AssignedTaskResponseDTO>>> getDepartmentTasks(@RequestAttribute("userId") Long userId) {
        try {
            List<AssignedTaskResponseDTO> tasks = coreDirectorService.getTasksByDepartment(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<AssignedTaskResponseDTO>> updateTaskStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestParam String status) {
        try {
            AssignedTaskResponseDTO task = coreDirectorService.updateTaskStatus(userId, taskId, status);
            return ResponseEntity.ok(ApiResponse.success("Task status updated to " + status, task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}