package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.*;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.models.ClientDetails;
import com.softmax.shardaos.models.EmployeeDetails;
import com.softmax.shardaos.service.BDEApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bde-director")
public class BDEDirectorController {

    @Autowired
    private BDEApprovalService bdeApprovalService;

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Boolean>> checkIsBDEDirector(@RequestAttribute("userId") Long userId) {
        boolean isDirector = bdeApprovalService.isBDEDirector(userId);
        return ResponseEntity.ok(ApiResponse.success(isDirector));
    }

    @GetMapping("/client/pending")
    public ResponseEntity<ApiResponse<List<ClientDetails>>> getPendingClients(@RequestAttribute("userId") Long userId) {
        try {
            List<ClientDetails> clients = bdeApprovalService.getPendingClients(userId);
            return ResponseEntity.ok(ApiResponse.success(clients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/client/all")
    public ResponseEntity<ApiResponse<List<ClientDetails>>> getAllClients(@RequestAttribute("userId") Long userId) {
        try {
            List<ClientDetails> clients = bdeApprovalService.getAllClients(userId);
            return ResponseEntity.ok(ApiResponse.success(clients));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/client/{clientId}")
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

    @PutMapping("/client/approve/{clientId}")
    public ResponseEntity<ApiResponse<ClientDetails>> approveClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @RequestBody ApproveClientRequest request) {
        try {
            ClientDetails client = bdeApprovalService.approveClient(userId, clientId, request);
            String message = request.getIsApproved() ? "Client approved successfully" : "Client rejected successfully";
            return ResponseEntity.ok(ApiResponse.success(message, client));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/client/update/{clientId}")
    public ResponseEntity<ApiResponse<ClientDetails>> updateClient(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long clientId,
            @RequestBody UpdateClientRequest request) {
        try {
            ClientDetails client = bdeApprovalService.updateClient(userId, clientId, request);
            return ResponseEntity.ok(ApiResponse.success("Client updated successfully", client));
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

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardKPIs(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> kpis = bdeApprovalService.getDashboardKPIs(userId);
            return ResponseEntity.ok(ApiResponse.success(kpis));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}