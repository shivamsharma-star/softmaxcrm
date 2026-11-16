package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.*;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.service.HRDirectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr-director")
public class HRDirectorController {

    @Autowired
    private HRDirectorService hrDirectorService;

    // ==================== AUTH ====================

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Boolean>> check(@RequestAttribute("userId") Long userId) {
        boolean isHRDirector = hrDirectorService.isHRDirector(userId);
        return ResponseEntity.ok(ApiResponse.success(isHRDirector));
    }

    // ==================== CONSULTANT MANAGEMENT ====================

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

    // ==================== EMPLOYEE MANAGEMENT ====================

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

    // ==================== PROJECT MANAGEMENT ====================

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDTO>>> getAllProjects(@RequestAttribute("userId") Long userId) {
        try {
            List<ProjectResponseDTO> projects = hrDirectorService.getAllProjects(userId);
            return ResponseEntity.ok(ApiResponse.success(projects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponseDTO>> getProjectById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            ProjectResponseDTO project = hrDirectorService.getProjectById(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== TASK MANAGEMENT ====================

    @PostMapping("/tasks/assign")
    public ResponseEntity<ApiResponse<HRAssignedTaskResponseDTO>> assignTaskToAnyEmployee(
            @RequestAttribute("userId") Long userId,
            @RequestBody AssignTaskToAnyEmployeeRequest request) {
        try {
            HRAssignedTaskResponseDTO task = hrDirectorService.assignTaskToAnyEmployee(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Task assigned successfully", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getAllDepartmentTasks(@RequestAttribute("userId") Long userId) {
        try {
            List<HRAssignedTaskResponseDTO> tasks = hrDirectorService.getAllDepartmentTasks(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/tasks/employee/{employeeUserId}")
    public ResponseEntity<ApiResponse<List<HRAssignedTaskResponseDTO>>> getTasksByEmployee(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long employeeUserId) {
        try {
            List<HRAssignedTaskResponseDTO> tasks = hrDirectorService.getTasksByEmployee(userId, employeeUserId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<HRAssignedTaskResponseDTO>> updateTaskStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskStatusRequest request) {
        try {
            HRAssignedTaskResponseDTO task = hrDirectorService.updateTaskStatus(userId, taskId, request);
            return ResponseEntity.ok(ApiResponse.success("Task status updated successfully", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHRDashboard(@RequestAttribute("userId") Long userId) {
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
//@RequestMapping("/api/hr-director")
//public class HRDirectorController {
//
//    @Autowired
//    private HRDirectorService hrDirectorService;
//
//    // ==================== AUTH ====================
//
//    @GetMapping("/check")
//    public ResponseEntity<ApiResponse<Boolean>> check(@RequestAttribute("userId") Long userId) {
//        boolean isHRDirector = hrDirectorService.isHRDirector(userId);
//        return ResponseEntity.ok(ApiResponse.success(isHRDirector));
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
//    public ResponseEntity<ApiResponse<Map<String, Object>>> getHRDashboard(@RequestAttribute("userId") Long userId) {
//        try {
//            Map<String, Object> kpis = hrDirectorService.getHRDashboard(userId);
//            return ResponseEntity.ok(ApiResponse.success(kpis));
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//    }
//}