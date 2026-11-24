package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.PartnerCreateClientRequest;
import com.softmax.shardaos.dto.input.PartnerCreateProjectRequest;
import com.softmax.shardaos.dto.input.PartnerSelfRegisterRequest;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.service.PartnerManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partner")
public class PartnerController {

    @Autowired
    private PartnerManagementService partnerService;

    // ============================================================
    // REGISTER (JWT required — user must be signed in first)
    // ============================================================

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<PartnerResponseDTO>> selfRegister(
            @RequestAttribute("userId") Long userId,
            @RequestBody PartnerSelfRegisterRequest request) {
        try {
            PartnerResponseDTO dto = partnerService.selfRegister(userId, request);
            return ResponseEntity.ok(ApiResponse.success(
                    "Partner registration submitted. Pending Head Director approval.", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // HEAD DIRECTOR — approvals & listing
    // ============================================================

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<PartnerResponseDTO>>> getAllPartners(
            @RequestAttribute("userId") Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(partnerService.getAllPartners(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{partnerId}")
    public ResponseEntity<ApiResponse<PartnerResponseDTO>> getPartnerById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long partnerId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getPartnerById(userId, partnerId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{partnerId}/status")
    public ResponseEntity<ApiResponse<PartnerResponseDTO>> updatePartnerStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long partnerId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        try {
            PartnerResponseDTO dto = partnerService.updatePartnerStatus(userId, partnerId, status, note);
            return ResponseEntity.ok(ApiResponse.success("Partner status updated", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER self — profile & dashboard
    // ============================================================

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PartnerResponseDTO>> getMyProfile(
            @RequestAttribute("userId") Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(partnerService.getMyProfile(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<ApiResponse<PartnerDashboardDTO>> getMyDashboard(
            @RequestAttribute("userId") Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(partnerService.getMyDashboard(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — clients (create only if APPROVED)
    // ============================================================

    @PostMapping("/clients")
    public ResponseEntity<ApiResponse<PartnerClientResponseDTO>> createClient(
            @RequestAttribute("userId") Long userId,
            @RequestBody PartnerCreateClientRequest request) {
        try {
            PartnerClientResponseDTO dto = partnerService.createClientForMe(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Client created successfully", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<List<PartnerClientResponseDTO>>> getMyClients(
            @RequestAttribute("userId") Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(partnerService.getMyClients(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ApiResponse<PartnerClientResponseDTO>> getMyClientDetails(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientDetails(userId, clientId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — client's PROJECTS (simple list)
    // ============================================================

    @GetMapping("/clients/{clientId}/projects")
    public ResponseEntity<ApiResponse<List<PartnerProjectSummaryDTO>>> getClientProjects(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientProjects(userId, clientId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — client's PROJECTS with payment summary (LIKE CLIENT DASHBOARD)
    // ============================================================

    /**
     * GET /api/partner/clients/{clientId}/projects/all
     * All projects of this client with payment summary + payment records
     */
    @GetMapping("/clients/{clientId}/projects/all")
    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getAllProjectsForClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientProjectsWithDetails(userId, clientId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/partner/clients/{clientId}/projects/{projectId}
     * Single project detail with payment summary + payment records
     */
    @GetMapping("/clients/{clientId}/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectPaymentSummaryDTO>> getProjectDetailForClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @PathVariable Long projectId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientProjectDetail(userId, clientId, projectId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/partner/clients/{clientId}/projects/{projectId}/history
     * Status history for a specific project
     */
    @GetMapping("/clients/{clientId}/projects/{projectId}/history")
    public ResponseEntity<ApiResponse<List<ProjectStatusDTO>>> getProjectHistoryForClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @PathVariable Long projectId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientProjectHistory(userId, clientId, projectId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — all clients' projects (partner-wide view)
    // ============================================================

    /**
     * GET /api/partner/me/clients/projects/all
     * All projects across all clients with payment summaries
     */
    @GetMapping("/me/clients/projects/all")
    public ResponseEntity<ApiResponse<List<ProjectPaymentSummaryDTO>>> getAllMyClientsProjects(
            @RequestAttribute("userId") Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getAllMyClientsProjects(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — client's PAYMENTS (flat list)
    // ============================================================

    @GetMapping("/clients/{clientId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> getClientPayments(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getMyClientPayments(userId, clientId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================================
    // PARTNER — create project for own client
    // ============================================================

    @PostMapping("/clients/{clientId}/projects")
    public ResponseEntity<ApiResponse<PartnerProjectSummaryDTO>> createProjectForClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @RequestBody PartnerCreateProjectRequest request) {
        try {
            PartnerProjectSummaryDTO dto =
                    partnerService.createProjectForClient(userId, clientId, request);
            return ResponseEntity.ok(ApiResponse.success("Project created for client", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}