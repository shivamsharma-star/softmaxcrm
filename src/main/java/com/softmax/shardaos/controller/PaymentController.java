package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
import com.softmax.shardaos.dto.input.CreatePaymentRequest;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // Helper method to print controller response
    private void printControllerResponse(String methodName, ResponseEntity<?> response) {
        try {
            System.out.println("\n========== CONTROLLER RESPONSE ==========");
            System.out.println("Endpoint: " + methodName);
            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
            System.out.println("HTTP Status: " + response.getStatusCode());
            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody());
            System.out.println("Response Body JSON:");
            System.out.println(jsonResponse);
            System.out.println("==========================================\n");
        } catch (Exception e) {
            System.out.println("Error printing controller response: " + e.getMessage());
        }
    }

    // ==================== CLIENT APIs (Payment Status View) ====================

    // Client: Get all payments for their specific project
    @GetMapping("/client/project/{projectId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getMyProjectPayments(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
        try {
            List<PaymentResponseDTO> payments = paymentService.getMyProjectPayments(userId, projectId);
            response = ResponseEntity.ok(ApiResponse.success("Project payments retrieved successfully", payments));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/client/project/" + projectId + "/payments", response);
        return response;
    }

    // Client: Get payment summary for their project
    @GetMapping("/client/project/{projectId}/summary")
    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getMyProjectPaymentSummary(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> response;
        try {
            ProjectPaymentSummaryDTO summary = paymentService.getMyProjectPaymentSummary(userId, projectId);
            response = ResponseEntity.ok(ApiResponse.success("Project payment summary retrieved successfully", summary));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/client/project/" + projectId + "/summary", response);
        return response;
    }

    // Client: Get all their projects with payment summaries
    @GetMapping("/client/my-projects")
    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getAllMyProjectsPayments(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> response;
        try {
            List<ProjectPaymentSummaryDTO> projects = paymentService.getAllMyProjectsPayments(userId);
            response = ResponseEntity.ok(ApiResponse.success("Your projects payment status retrieved successfully", projects));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/client/my-projects", response);
        return response;
    }

    // Client: Get payment status for a specific transaction
    @GetMapping("/client/transaction/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getMyPaymentByTransactionId(
            @RequestAttribute("userId") Long userId,
            @PathVariable String transactionId) {
        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
        try {
            PaymentResponseDTO payment = paymentService.getMyPaymentByTransactionId(userId, transactionId);
            response = ResponseEntity.ok(ApiResponse.success("Payment details retrieved successfully", payment));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/client/transaction/" + transactionId, response);
        return response;
    }

    // Client: Get dashboard stats for client (only their projects)
    @GetMapping("/client/dashboard")
    public ResponseEntity<ApiResponse<ClientPaymentDashboardDTO>> getMyPaymentDashboard(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<ClientPaymentDashboardDTO>> response;
        try {
            ClientPaymentDashboardDTO dashboard = paymentService.getMyPaymentDashboard(userId);
            response = ResponseEntity.ok(ApiResponse.success("Dashboard stats retrieved successfully", dashboard));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/client/dashboard", response);
        return response;
    }

    // ==================== CLIENT APIs (Create Payment) ====================

    @PostMapping("/project")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createProjectPayment(
            @RequestAttribute("userId") Long userId,
            @RequestBody CreatePaymentRequest request) {
        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
        try {
            PaymentResponseDTO payment = paymentService.createProjectPayment(userId, request);
            response = ResponseEntity.ok(ApiResponse.success("Project payment submitted successfully", payment));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("POST /api/payment/project", response);
        return response;
    }

    // ==================== HEAD DIRECTOR APIs ====================

    @PostMapping("/salary")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createSalaryPayment(
            @RequestAttribute("userId") Long userId,
            @RequestBody CreatePaymentRequest request) {
        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
        try {
            PaymentResponseDTO payment = paymentService.createSalaryPayment(userId, request);
            response = ResponseEntity.ok(ApiResponse.success("Salary payment submitted successfully", payment));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("POST /api/payment/salary", response);
        return response;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getPendingPayments(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
        try {
            List<PaymentResponseDTO> payments = paymentService.getPendingPayments(userId);
            response = ResponseEntity.ok(ApiResponse.success(payments));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/pending", response);
        return response;
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getAllPayments(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
        try {
            List<PaymentResponseDTO> payments = paymentService.getAllPayments(userId);
            response = ResponseEntity.ok(ApiResponse.success(payments));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/all", response);
        return response;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getPaymentById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long paymentId) {
        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
        try {
            PaymentResponseDTO payment = paymentService.getPaymentById(userId, paymentId);
            response = ResponseEntity.ok(ApiResponse.success(payment));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/" + paymentId, response);
        return response;
    }

    @PutMapping("/{paymentId}/approve")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePayment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long paymentId,
            @RequestBody ApprovePaymentRequest request) {
        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
        try {
            PaymentResponseDTO payment = paymentService.approvePayment(userId, paymentId, request);
            String message = request.getStatus().equals("APPROVED") ? "Payment approved successfully" : "Payment rejected successfully";
            response = ResponseEntity.ok(ApiResponse.success(message, payment));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("PUT /api/payment/" + paymentId + "/approve", response);
        return response;
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getProjectPayments(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> response;
        try {
            ProjectPaymentSummaryDTO summary = paymentService.getProjectPayments(userId, projectId);
            response = ResponseEntity.ok(ApiResponse.success(summary));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/project/" + projectId, response);
        return response;
    }

    @GetMapping("/salary/user/{userId}")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getSalaryPaymentsByUser(
            @RequestAttribute("userId") Long directorId,
            @PathVariable Long userId) {
        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
        try {
            List<PaymentResponseDTO> payments = paymentService.getSalaryPaymentsByUser(directorId, userId);
            response = ResponseEntity.ok(ApiResponse.success(payments));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/salary/user/" + userId, response);
        return response;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardPaymentStatsDTO>> getDashboardStats(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<DashboardPaymentStatsDTO>> response;
        try {
            DashboardPaymentStatsDTO stats = paymentService.getDashboardStats(userId);
            response = ResponseEntity.ok(ApiResponse.success(stats));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/dashboard/stats", response);
        return response;
    }

    // ==================== NEW HEAD DIRECTOR APIs - Project Wise Payment Summary ====================

    /**
     * Head Director: Get all projects with complete payment summaries
     * Shows project-wise remaining amounts, department info, client info
     */
    @GetMapping("/director/projects")
    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getAllProjectsWithPaymentSummary(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> response;
        try {
            List<ProjectPaymentSummaryDTO> projects = paymentService.getAllProjectsWithPaymentSummary(userId);
            response = ResponseEntity.ok(ApiResponse.success("All projects payment summary retrieved successfully", projects));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/projects", response);
        return response;
    }

    /**
     * Head Director: Get projects filtered by department with payment summaries
     */
    @GetMapping("/director/departments/{departmentId}/projects")
    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getProjectsByDepartmentWithPaymentSummary(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long departmentId) {
        ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> response;
        try {
            List<ProjectPaymentSummaryDTO> projects = paymentService.getProjectsByDepartmentWithPaymentSummary(userId, departmentId);
            response = ResponseEntity.ok(ApiResponse.success("Department projects payment summary retrieved successfully", projects));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/departments/" + departmentId + "/projects", response);
        return response;
    }

    /**
     * Head Director: Get single project payment summary with all details
     */
    @GetMapping("/director/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getSingleProjectPaymentSummaryForDirector(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> response;
        try {
            ProjectPaymentSummaryDTO summary = paymentService.getProjectPayments(userId, projectId);
            response = ResponseEntity.ok(ApiResponse.success("Project payment summary retrieved successfully", summary));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/projects/" + projectId, response);
        return response;
    }

    /**
     * Head Director: Get payment approval counts (pending/approved/rejected)
     */
    @GetMapping("/director/counts")
    public ResponseEntity<ApiResponse<PaymentApprovalCountDTO>> getPaymentApprovalCount(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<PaymentApprovalCountDTO>> response;
        try {
            PaymentApprovalCountDTO counts = paymentService.getPaymentApprovalCount(userId);
            response = ResponseEntity.ok(ApiResponse.success("Payment counts retrieved successfully", counts));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/counts", response);
        return response;
    }

    /**
     * Head Director: Get department-wise payment summary
     */
    @GetMapping("/director/departments/summary")
    public ResponseEntity<ApiResponse<List<DepartmentPaymentSummaryDTO>>> getDepartmentWisePaymentSummary(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<DepartmentPaymentSummaryDTO>>> response;
        try {
            List<DepartmentPaymentSummaryDTO> summary = paymentService.getDepartmentWisePaymentSummary(userId);
            response = ResponseEntity.ok(ApiResponse.success("Department wise payment summary retrieved successfully", summary));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/departments/summary", response);
        return response;
    }

    /**
     * Head Director: Get client-wise payment summary
     */
    @GetMapping("/director/clients/summary")
    public ResponseEntity<ApiResponse<List<ClientPaymentSummaryDTO>>> getClientWisePaymentSummary(
            @RequestAttribute("userId") Long userId) {
        ResponseEntity<ApiResponse<List<ClientPaymentSummaryDTO>>> response;
        try {
            List<ClientPaymentSummaryDTO> summary = paymentService.getClientWisePaymentSummary(userId);
            response = ResponseEntity.ok(ApiResponse.success("Client wise payment summary retrieved successfully", summary));
        } catch (RuntimeException e) {
            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        printControllerResponse("GET /api/payment/director/clients/summary", response);
        return response;
    }
}
































//package com.softmax.shardaos.controller;
//
//import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
//import com.softmax.shardaos.dto.input.CreatePaymentRequest;
//import com.softmax.shardaos.dto.output.ApiResponse;
//import com.softmax.shardaos.dto.output.ClientPaymentDashboardDTO;
//import com.softmax.shardaos.dto.output.DashboardPaymentStatsDTO;
//import com.softmax.shardaos.dto.output.PaymentResponseDTO;
//import com.softmax.shardaos.dto.output.ProjectPaymentSummaryDTO;
//import com.softmax.shardaos.service.PaymentService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/payment")
//public class PaymentController {
//
//    @Autowired
//    private PaymentService paymentService;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//
//    // Helper method to print controller response
//    private void printControllerResponse(String methodName, ResponseEntity<?> response) {
//        try {
//            System.out.println("\n========== CONTROLLER RESPONSE ==========");
//            System.out.println("Endpoint: " + methodName);
//            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
//            System.out.println("HTTP Status: " + response.getStatusCode());
//            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.getBody());
//            System.out.println("Response Body JSON:");
//            System.out.println(jsonResponse);
//            System.out.println("==========================================\n");
//        } catch (Exception e) {
//            System.out.println("Error printing controller response: " + e.getMessage());
//        }
//    }
//
//    // ==================== CLIENT APIs (Payment Status View) ====================
//
//    // Client: Get all payments for their specific project
//    @GetMapping("/client/project/{projectId}/payments")
//    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getMyProjectPayments(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long projectId) {
//        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
//        try {
//            List<PaymentResponseDTO> payments = paymentService.getMyProjectPayments(userId, projectId);
//            response = ResponseEntity.ok(ApiResponse.success("Project payments retrieved successfully", payments));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/client/project/" + projectId + "/payments", response);
//        return response;
//    }
//
//    // Client: Get payment summary for their project
//    @GetMapping("/client/project/{projectId}/summary")
//    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getMyProjectPaymentSummary(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long projectId) {
//        ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> response;
//        try {
//            ProjectPaymentSummaryDTO summary = paymentService.getMyProjectPaymentSummary(userId, projectId);
//            response = ResponseEntity.ok(ApiResponse.success("Project payment summary retrieved successfully", summary));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/client/project/" + projectId + "/summary", response);
//        return response;
//    }
//
//    // Client: Get all their projects with payment summaries
//    @GetMapping("/client/my-projects")
//    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getAllMyProjectsPayments(
//            @RequestAttribute("userId") Long userId) {
//        ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> response;
//        try {
//            List<ProjectPaymentSummaryDTO> projects = paymentService.getAllMyProjectsPayments(userId);
//            response = ResponseEntity.ok(ApiResponse.success("Your projects payment status retrieved successfully", projects));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/client/my-projects", response);
//        return response;
//    }
//
//    // Client: Get payment status for a specific transaction
//    @GetMapping("/client/transaction/{transactionId}")
//    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getMyPaymentByTransactionId(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable String transactionId) {
//        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
//        try {
//            PaymentResponseDTO payment = paymentService.getMyPaymentByTransactionId(userId, transactionId);
//            response = ResponseEntity.ok(ApiResponse.success("Payment details retrieved successfully", payment));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/client/transaction/" + transactionId, response);
//        return response;
//    }
//
//    // Client: Get dashboard stats for client (only their projects)
//    @GetMapping("/client/dashboard")
//    public ResponseEntity<ApiResponse<ClientPaymentDashboardDTO>> getMyPaymentDashboard(
//            @RequestAttribute("userId") Long userId) {
//        ResponseEntity<ApiResponse<ClientPaymentDashboardDTO>> response;
//        try {
//            ClientPaymentDashboardDTO dashboard = paymentService.getMyPaymentDashboard(userId);
//            response = ResponseEntity.ok(ApiResponse.success("Dashboard stats retrieved successfully", dashboard));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/client/dashboard", response);
//        return response;
//    }
//
//    // ==================== CLIENT APIs (Create Payment) ====================
//
//    @PostMapping("/project")
//    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createProjectPayment(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody CreatePaymentRequest request) {
//        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
//        try {
//            PaymentResponseDTO payment = paymentService.createProjectPayment(userId, request);
//            response = ResponseEntity.ok(ApiResponse.success("Project payment submitted successfully", payment));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("POST /api/payment/project", response);
//        return response;
//    }
//
//    // ==================== HEAD DIRECTOR APIs ====================
//
//    @PostMapping("/salary")
//    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createSalaryPayment(
//            @RequestAttribute("userId") Long userId,
//            @RequestBody CreatePaymentRequest request) {
//        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
//        try {
//            PaymentResponseDTO payment = paymentService.createSalaryPayment(userId, request);
//            response = ResponseEntity.ok(ApiResponse.success("Salary payment submitted successfully", payment));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("POST /api/payment/salary", response);
//        return response;
//    }
//
//    @GetMapping("/pending")
//    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getPendingPayments(
//            @RequestAttribute("userId") Long userId) {
//        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
//        try {
//            List<PaymentResponseDTO> payments = paymentService.getPendingPayments(userId);
//            response = ResponseEntity.ok(ApiResponse.success(payments));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/pending", response);
//        return response;
//    }
//
//    @GetMapping("/all")
//    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getAllPayments(
//            @RequestAttribute("userId") Long userId) {
//        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
//        try {
//            List<PaymentResponseDTO> payments = paymentService.getAllPayments(userId);
//            response = ResponseEntity.ok(ApiResponse.success(payments));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/all", response);
//        return response;
//    }
//
//    @GetMapping("/{paymentId}")
//    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getPaymentById(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long paymentId) {
//        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
//        try {
//            PaymentResponseDTO payment = paymentService.getPaymentById(userId, paymentId);
//            response = ResponseEntity.ok(ApiResponse.success(payment));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/" + paymentId, response);
//        return response;
//    }
//
//    @PutMapping("/{paymentId}/approve")
//    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePayment(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long paymentId,
//            @RequestBody ApprovePaymentRequest request) {
//        ResponseEntity<ApiResponse<PaymentResponseDTO>> response;
//        try {
//            PaymentResponseDTO payment = paymentService.approvePayment(userId, paymentId, request);
//            String message = request.getStatus().equals("APPROVED") ? "Payment approved successfully" : "Payment rejected successfully";
//            response = ResponseEntity.ok(ApiResponse.success(message, payment));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("PUT /api/payment/" + paymentId + "/approve", response);
//        return response;
//    }
//
//    @GetMapping("/project/{projectId}")
//    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getProjectPayments(
//            @RequestAttribute("userId") Long userId,
//            @PathVariable Long projectId) {
//        ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> response;
//        try {
//            ProjectPaymentSummaryDTO summary = paymentService.getProjectPayments(userId, projectId);
//            response = ResponseEntity.ok(ApiResponse.success(summary));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/project/" + projectId, response);
//        return response;
//    }
//
//    @GetMapping("/salary/user/{userId}")
//    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getSalaryPaymentsByUser(
//            @RequestAttribute("userId") Long directorId,
//            @PathVariable Long userId) {
//        ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> response;
//        try {
//            List<PaymentResponseDTO> payments = paymentService.getSalaryPaymentsByUser(directorId, userId);
//            response = ResponseEntity.ok(ApiResponse.success(payments));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/salary/user/" + userId, response);
//        return response;
//    }
//
//    @GetMapping("/dashboard/stats")
//    public ResponseEntity<ApiResponse<DashboardPaymentStatsDTO>> getDashboardStats(
//            @RequestAttribute("userId") Long userId) {
//        ResponseEntity<ApiResponse<DashboardPaymentStatsDTO>> response;
//        try {
//            DashboardPaymentStatsDTO stats = paymentService.getDashboardStats(userId);
//            response = ResponseEntity.ok(ApiResponse.success(stats));
//        } catch (RuntimeException e) {
//            response = ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
//        }
//        printControllerResponse("GET /api/payment/dashboard/stats", response);
//        return response;
//    }
//}
//
//
//
//
//
//
//
//
//
//
////package com.softmax.shardaos.controller;
////
////import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
////import com.softmax.shardaos.dto.input.CreatePaymentRequest;
////import com.softmax.shardaos.dto.output.ApiResponse;
////import com.softmax.shardaos.dto.output.DashboardPaymentStatsDTO;
////import com.softmax.shardaos.dto.output.PaymentResponseDTO;
////import com.softmax.shardaos.dto.output.ProjectPaymentSummaryDTO;
////import com.softmax.shardaos.service.PaymentService;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.http.ResponseEntity;
////import org.springframework.web.bind.annotation.*;
////
////import java.util.List;
////
////@RestController
////@RequestMapping("/api/payment")
////public class PaymentController {
////
////    @Autowired
////    private PaymentService paymentService;
////
////    // ==================== CLIENT APIs ====================
////
////    @PostMapping("/project")
////    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createProjectPayment(
////            @RequestAttribute("userId") Long userId,
////            @RequestBody CreatePaymentRequest request) {
////        try {
////            PaymentResponseDTO payment = paymentService.createProjectPayment(userId, request);
////            return ResponseEntity.ok(ApiResponse.success("Project payment submitted successfully", payment));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    // ==================== HEAD DIRECTOR APIs ====================
////
////    @PostMapping("/salary")
////    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createSalaryPayment(
////            @RequestAttribute("userId") Long userId,
////            @RequestBody CreatePaymentRequest request) {
////        try {
////            PaymentResponseDTO payment = paymentService.createSalaryPayment(userId, request);
////            return ResponseEntity.ok(ApiResponse.success("Salary payment submitted successfully", payment));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/pending")
////    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getPendingPayments(
////            @RequestAttribute("userId") Long userId) {
////        try {
////            List<PaymentResponseDTO> payments = paymentService.getPendingPayments(userId);
////            return ResponseEntity.ok(ApiResponse.success(payments));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/all")
////    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getAllPayments(
////            @RequestAttribute("userId") Long userId) {
////        try {
////            List<PaymentResponseDTO> payments = paymentService.getAllPayments(userId);
////            return ResponseEntity.ok(ApiResponse.success(payments));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/{paymentId}")
////    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getPaymentById(
////            @RequestAttribute("userId") Long userId,
////            @PathVariable Long paymentId) {
////        try {
////            PaymentResponseDTO payment = paymentService.getPaymentById(userId, paymentId);
////            return ResponseEntity.ok(ApiResponse.success(payment));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @PutMapping("/{paymentId}/approve")
////    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePayment(
////            @RequestAttribute("userId") Long userId,
////            @PathVariable Long paymentId,
////            @RequestBody ApprovePaymentRequest request) {
////        try {
////            PaymentResponseDTO payment = paymentService.approvePayment(userId, paymentId, request);
////            String message = request.getStatus().equals("APPROVED") ? "Payment approved successfully" : "Payment rejected successfully";
////            return ResponseEntity.ok(ApiResponse.success(message, payment));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/project/{projectId}")
////    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getProjectPayments(
////            @RequestAttribute("userId") Long userId,
////            @PathVariable Long projectId) {
////        try {
////            ProjectPaymentSummaryDTO summary = paymentService.getProjectPayments(userId, projectId);
////            return ResponseEntity.ok(ApiResponse.success(summary));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/salary/user/{userId}")
////    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getSalaryPaymentsByUser(
////            @RequestAttribute("userId") Long directorId,
////            @PathVariable Long userId) {
////        try {
////            List<PaymentResponseDTO> payments = paymentService.getSalaryPaymentsByUser(directorId, userId);
////            return ResponseEntity.ok(ApiResponse.success(payments));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////
////    @GetMapping("/dashboard/stats")
////    public ResponseEntity<ApiResponse<DashboardPaymentStatsDTO>> getDashboardStats(
////            @RequestAttribute("userId") Long userId) {
////        try {
////            DashboardPaymentStatsDTO stats = paymentService.getDashboardStats(userId);
////            return ResponseEntity.ok(ApiResponse.success(stats));
////        } catch (RuntimeException e) {
////            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
////        }
////    }
////}