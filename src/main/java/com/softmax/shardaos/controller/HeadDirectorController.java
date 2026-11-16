package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.*;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.ClientDetails;
import com.softmax.shardaos.models.Department;
import com.softmax.shardaos.models.EmployeeDetails;
import com.softmax.shardaos.service.BDEApprovalService;
import com.softmax.shardaos.service.HRDirectorService;
import com.softmax.shardaos.service.HeadDirectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/head-director")
public class HeadDirectorController {

    @Autowired
    private HRDirectorService hrDirectorService;

    @Autowired
    private BDEApprovalService bdeApprovalService;

    @Autowired
    private HeadDirectorService headDirectorService;

    // ==================== AUTH ====================

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Boolean>> check(@RequestAttribute("userId") Long userId) {
        boolean isHeadDirector = headDirectorService.isHeadDirector(userId);
        return ResponseEntity.ok(ApiResponse.success(isHeadDirector));
    }

    // ==================== CONSULTANT MANAGEMENT (HR) ====================

    @GetMapping("/consultants")
    public ResponseEntity<ApiResponse<List<ConsultantListResponseDTO>>> getAllConsultants(@RequestAttribute("userId") Long userId) {
        try {
            List<ConsultantListResponseDTO> consultants = hrDirectorService.getAllConsultants(userId);
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
            ConsultantListResponseDTO consultant = hrDirectorService.getConsultantById(userId, consultantId);
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
            List<ConsultantStatusHistoryDTO> history = hrDirectorService.getConsultantStatusHistory(userId, consultantId);
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
            ConsultantStatusResponseDTO status = hrDirectorService.updateConsultantStatus(userId, consultantId, request);
            return ResponseEntity.ok(ApiResponse.success("Consultant status updated successfully", status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/consultants/{consultantId}/assign-department")
    public ResponseEntity<ApiResponse<ConsultantListResponseDTO>> assignDepartmentToConsultant(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId,
            @RequestBody AssignDepartmentRequest request) {
        try {
            ConsultantListResponseDTO consultant = hrDirectorService.assignDepartmentToConsultant(userId, consultantId, request);
            return ResponseEntity.ok(ApiResponse.success("Department assigned successfully", consultant));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/consultants/{consultantId}/convert-to-employee")
    public ResponseEntity<ApiResponse<EmployeeDTO>> convertConsultantToEmployee(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultantId,
            @RequestBody ConvertToEmployeeRequest request) {
        try {
            // Validate stype - only MANAGER or DIRECTOR allowed
            if (request.getStype() != null && !request.getStype().isEmpty()) {
                String stype = request.getStype().toUpperCase();
                if (!stype.equals("MANAGER") && !stype.equals("DIRECTOR")) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid stype. Allowed values: MANAGER, DIRECTOR (or null/empty for regular employee)"));
                }
                request.setStype(stype);
            }

            EmployeeDTO employee = hrDirectorService.convertConsultantToEmployee(userId, consultantId, request);
            return ResponseEntity.ok(ApiResponse.success("Consultant converted to employee successfully", employee));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== EMPLOYEE MANAGEMENT (HR) ====================

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<List<EmployeeDTO>>> getAllEmployees(@RequestAttribute("userId") Long userId) {
        try {
            List<EmployeeDTO> employees = hrDirectorService.getAllEmployees(userId);
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
            EmployeeDTO employee = hrDirectorService.getEmployeeById(userId, employeeId);
            return ResponseEntity.ok(ApiResponse.success(employee));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/employees/{employeeId}")
    public ResponseEntity<ApiResponse<EmployeeDTO>> updateEmployee(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long employeeId,
            @RequestBody UpdateEmployeeRequest request) {
        try {
            EmployeeDTO employee = hrDirectorService.updateEmployee(userId, employeeId, request);
            return ResponseEntity.ok(ApiResponse.success("Employee updated successfully", employee));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== CLIENT MANAGEMENT (BDE) ====================

    @GetMapping("/clients/pending")
    public ResponseEntity<ApiResponse<List<ClientDetails>>> getPendingClients(@RequestAttribute("userId") Long userId) {
        try {
            List<ClientDetails> clients = bdeApprovalService.getPendingClients(userId);
            return ResponseEntity.ok(ApiResponse.success(clients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<List<ClientDetails>>> getAllClients(@RequestAttribute("userId") Long userId) {
        try {
            List<ClientDetails> clients = bdeApprovalService.getAllClients(userId);
            return ResponseEntity.ok(ApiResponse.success(clients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ApiResponse<ClientDetails>> getClientById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId) {
        try {
            ClientDetails client = bdeApprovalService.getClientById(userId, clientId);
            return ResponseEntity.ok(ApiResponse.success(client));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/clients/{clientId}/approve")
    public ResponseEntity<ApiResponse<ClientDetails>> approveClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @RequestBody ApproveClientRequest request) {
        try {
            ClientDetails client = bdeApprovalService.approveClient(userId, clientId, request);
            return ResponseEntity.ok(ApiResponse.success("Client approved/rejected successfully", client));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/bde-team")
    public ResponseEntity<ApiResponse<List<EmployeeDetails>>> getBDETeam(@RequestAttribute("userId") Long userId) {
        try {
            List<EmployeeDetails> team = bdeApprovalService.getBDETeam(userId);
            return ResponseEntity.ok(ApiResponse.success(team));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/bde/dashboard/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBDEDashboardKPIs(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> kpis = bdeApprovalService.getDashboardKPIs(userId);
            return ResponseEntity.ok(ApiResponse.success(kpis));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== USERS (HEAD DIRECTOR) ====================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<AllUsersResponseDTO>> getAllUsers(@RequestAttribute("userId") Long userId) {
        try {
            AllUsersResponseDTO users = headDirectorService.getAllUsers(userId);
            return ResponseEntity.ok(ApiResponse.success(users));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/users/{targetId}")
    public ResponseEntity<ApiResponse<UserWithRolesDTO>> getUserById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long targetId) {
        try {
            UserWithRolesDTO user = headDirectorService.getUserById(userId, targetId);
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== DEPARTMENT MANAGEMENT (HEAD DIRECTOR) ====================

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<Department>>> getAllDepartments(@RequestAttribute("userId") Long userId) {
        try {
            List<Department> departments = headDirectorService.getAllDepartments(userId);
            return ResponseEntity.ok(ApiResponse.success(departments));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/departments/{deptId}")
    public ResponseEntity<ApiResponse<Department>> getDepartmentById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long deptId) {
        try {
            Department department = headDirectorService.getDepartmentById(userId, deptId);
            return ResponseEntity.ok(ApiResponse.success(department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<Department>> createDepartment(
            @RequestAttribute("userId") Long userId,
            @RequestParam String departmentName) {
        try {
            Department department = headDirectorService.createDepartment(userId, departmentName);
            return ResponseEntity.ok(ApiResponse.success("Department created successfully", department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/departments/{deptId}")
    public ResponseEntity<ApiResponse<Department>> updateDepartment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long deptId,
            @RequestParam String departmentName) {
        try {
            Department department = headDirectorService.updateDepartment(userId, deptId, departmentName);
            return ResponseEntity.ok(ApiResponse.success("Department updated successfully", department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/departments/{deptId}")
    public ResponseEntity<ApiResponse<String>> deleteDepartment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long deptId) {
        try {
            headDirectorService.deleteDepartment(userId, deptId);
            return ResponseEntity.ok(ApiResponse.success("Department deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/departments/kpis")
    public ResponseEntity<ApiResponse<List<DepartmentKPISummaryDTO>>> getAllDepartmentsWithKPIs(@RequestAttribute("userId") Long userId) {
        try {
            List<DepartmentKPISummaryDTO> kpis = headDirectorService.getAllDepartmentsWithKPIs(userId);
            return ResponseEntity.ok(ApiResponse.success(kpis));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== PROJECT MANAGEMENT (HEAD DIRECTOR) ====================

    @GetMapping("/all-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getAllProjectsFull(@RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = headDirectorService.getAllProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/department/{deptId}")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getProjectsByDepartment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long deptId) {
        try {
            List<ProjectResponseDTO> projects = headDirectorService.getProjectsByDepartment(userId, deptId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/status/{status}")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getProjectsByStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable String status) {
        try {
            List<ProjectResponseDTO> projects = headDirectorService.getProjectsByStatus(userId, status);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/all-projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> getProjectByIdFull(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            ProjectResponseDTO project = headDirectorService.getProjectById(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/projects/{projectId}/status")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> updateProjectStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        try {
            ProjectResponseDTO project = headDirectorService.updateProjectStatus(userId, projectId, status, note);
            return ResponseEntity.ok(ApiResponse.success("Project status updated successfully", project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<ApiResponse<List<ProjectStatusDTO>>> getProjectHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<ProjectStatusDTO> history = headDirectorService.getProjectHistory(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== TEAM MANAGEMENT (HEAD DIRECTOR) ====================

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getAllProjectTeams(@RequestAttribute("userId") Long userId) {
        try {
            List<TeamResponseDTO> teams = headDirectorService.getAllProjectTeams(userId);
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponseDTO>>> getProjectTeams(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<TeamResponseDTO> teams = headDirectorService.getProjectTeams(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(teams));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== TASK MANAGEMENT (HEAD DIRECTOR) ====================

    @GetMapping("/all-tasks")
    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getAllTasks(@RequestAttribute("userId") Long userId) {
        try {
            List<HRAssignedTaskResponseDTO> tasks = headDirectorService.getAllTasks(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getTasksByProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<HRAssignedTaskResponseDTO> tasks = headDirectorService.getTasksByProject(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/all-tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<HRAssignedTaskResponseDTO>> updateTaskStatusByHead(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        try {
            HRAssignedTaskResponseDTO task = headDirectorService.updateTaskStatus(userId, taskId, status, note);
            return ResponseEntity.ok(ApiResponse.success("Task status updated successfully", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== DASHBOARD (HEAD DIRECTOR) ====================

    @GetMapping("/dashboard/company-kpis")
    public ResponseEntity<ApiResponse<HeadDirectorKPIDTO>> getCompanyDashboardKPIs(@RequestAttribute("userId") Long userId) {
        try {
            HeadDirectorKPIDTO kpis = headDirectorService.getDashboardKPIs(userId);
            return ResponseEntity.ok(ApiResponse.success(kpis));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== HR DASHBOARD ====================

    @GetMapping("/dashboard/hr-kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHRDashboardKPIs(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> kpis = hrDirectorService.getHRDashboard(userId);
            return ResponseEntity.ok(ApiResponse.success(kpis));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}









//package com.softmax.shardaos.controller;
//
//import com.softmax.shardaos.dto.input.*;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.service.HRDirectorService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/head-director")
//public class HeadDirectorController {
//
//    @Autowired
//    private HRDirectorService hrDirectorService;
//
//    // ==================== AUTH ====================
//
//    @GetMapping("/check")
//    public ResponseEntity<ApiResponse<Boolean>> check(@RequestAttribute("userId") Long userId) {
//        boolean isHeadDirector = hrDirectorService.isHeadDirector(userId);
//        return ResponseEntity.ok(ApiResponse.success(isHeadDirector));
//    }
//
//    // ==================== CONSULTANT MANAGEMENT ====================
//
//    @GetMapping("/consultants")
//    public ResponseEntity<ApiResponse<List<ConsultantListResponseDTO>>> getAllConsultants(@RequestAttribute("userId") Long userId) {
//        try {
//            List<ConsultantListResponseDTO> consultants = hrDirectorService.getAllConsultants(userId);
//            return ResponseEntity.ok(ApiResponse.success(consultants));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/consultants/{consultantId}")
//    public ResponseEntity<ApiResponse<ConsultantListResponseDTO>> getConsultantById(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long consultantId) {
//        try {
//            ConsultantListResponseDTO consultant = hrDirectorService.getConsultantById(userId, consultantId);
//            return ResponseEntity.ok(ApiResponse.success(consultant));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/consultants/{consultantId}/status-history")
//    public ResponseEntity<ApiResponse<List<ConsultantStatusHistoryDTO>>> getConsultantStatusHistory(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long consultantId) {
//        try {
//            List<ConsultantStatusHistoryDTO> history = hrDirectorService.getConsultantStatusHistory(userId, consultantId);
//            return ResponseEntity.ok(ApiResponse.success(history));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PutMapping("/consultants/{consultantId}/status")
//    public ResponseEntity<ApiResponse<ConsultantStatusResponseDTO>> updateConsultantStatus(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long consultantId,
//            @RequestBody UpdateConsultantStatusRequest request) {
//        try {
//            ConsultantStatusResponseDTO status = hrDirectorService.updateConsultantStatus(userId, consultantId, request);
//            return ResponseEntity.ok(ApiResponse.success("Consultant status updated successfully", status));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PutMapping("/consultants/{consultantId}/assign-department")
//    public ResponseEntity<ApiResponse<ConsultantListResponseDTO>> assignDepartmentToConsultant(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long consultantId,
//            @RequestBody AssignDepartmentRequest request) {
//        try {
//            ConsultantListResponseDTO consultant = hrDirectorService.assignDepartmentToConsultant(userId, consultantId, request);
//            return ResponseEntity.ok(ApiResponse.success("Department assigned successfully", consultant));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PostMapping("/consultants/{consultantId}/convert-to-employee")
//    public ResponseEntity<ApiResponse<EmployeeDTO>> convertConsultantToEmployee(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long consultantId,
//            @RequestBody ConvertToEmployeeRequest request) {
//        try {
//            EmployeeDTO employee = hrDirectorService.convertConsultantToEmployee(userId, consultantId, request);
//            return ResponseEntity.ok(ApiResponse.success("Consultant converted to employee successfully", employee));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    // ==================== EMPLOYEE MANAGEMENT ====================
//
//    @GetMapping("/employees")
//    public ResponseEntity<ApiResponse<List<EmployeeDTO>>> getAllEmployees(@RequestAttribute("userId") Long userId) {
//        try {
//            List<EmployeeDTO> employees = hrDirectorService.getAllEmployees(userId);
//            return ResponseEntity.ok(ApiResponse.success(employees));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/employees/{employeeId}")
//    public ResponseEntity<ApiResponse<EmployeeDTO>> getEmployeeById(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long employeeId) {
//        try {
//            EmployeeDTO employee = hrDirectorService.getEmployeeById(userId, employeeId);
//            return ResponseEntity.ok(ApiResponse.success(employee));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PutMapping("/employees/{employeeId}")
//    public ResponseEntity<ApiResponse<EmployeeDTO>> updateEmployee(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long employeeId,
//            @RequestBody UpdateEmployeeRequest request) {
//        try {
//            EmployeeDTO employee = hrDirectorService.updateEmployee(userId, employeeId, request);
//            return ResponseEntity.ok(ApiResponse.success("Employee updated successfully", employee));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    // ==================== PROJECT MANAGEMENT ====================
//
//    @GetMapping("/projects")
//    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getAllProjects(@RequestAttribute("userId") Long userId) {
//        try {
//            List<ProjectResponseDTO> projects = hrDirectorService.getAllProjects(userId);
//            return ResponseEntity.ok(ApiResponse.success(projects));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/projects/{projectId}")
//    public ResponseEntity<ApiResponse<ProjectResponseDTO>> getProjectById(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long projectId) {
//        try {
//            ProjectResponseDTO project = hrDirectorService.getProjectById(userId, projectId);
//            return ResponseEntity.ok(ApiResponse.success(project));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    // ==================== TASK MANAGEMENT ====================
//
//    @PostMapping("/tasks/assign")
//    public ResponseEntity<ApiResponse<HRAssignedTaskResponseDTO>> assignTaskToAnyEmployee(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody AssignTaskToAnyEmployeeRequest request) {
//        try {
//            HRAssignedTaskResponseDTO task = hrDirectorService.assignTaskToAnyEmployee(userId, request);
//            return ResponseEntity.ok(ApiResponse.success("Task assigned successfully", task));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/tasks")
//    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getAllDepartmentTasks(@RequestAttribute("userId") Long userId) {
//        try {
//            List<HRAssignedTaskResponseDTO> tasks = hrDirectorService.getAllDepartmentTasks(userId);
//            return ResponseEntity.ok(ApiResponse.success(tasks));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @GetMapping("/tasks/employee/{employeeUserId}")
//    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getTasksByEmployee(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long employeeUserId) {
//        try {
//            List<HRAssignedTaskResponseDTO> tasks = hrDirectorService.getTasksByEmployee(userId, employeeUserId);
//            return ResponseEntity.ok(ApiResponse.success(tasks));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    @PutMapping("/tasks/{taskId}/status")
//    public ResponseEntity<ApiResponse<HRAssignedTaskResponseDTO>> updateTaskStatus(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long taskId,
//            @RequestBody UpdateTaskStatusRequest request) {
//        try {
//            HRAssignedTaskResponseDTO task = hrDirectorService.updateTaskStatus(userId, taskId, request);
//            return ResponseEntity.ok(ApiResponse.success("Task status updated successfully", task));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//
//    // ==================== DASHBOARD ====================
//
//    @GetMapping("/dashboard/kpis")
//    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardKPIs(@RequestAttribute("userId") Long userId) {
//        try {
//            Map<String, Object> kpis = hrDirectorService.getHRDashboard(userId);
//            return ResponseEntity.ok(ApiResponse.success(kpis));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//}
//
//
//
//
//
