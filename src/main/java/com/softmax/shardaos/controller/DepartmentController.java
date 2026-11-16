package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.DepartmentRequestDTO;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.DepartmentResponseDTO;
import com.softmax.shardaos.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    // ==================== PUBLIC APIs (All authenticated users) ====================

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponseDTO>>> getAllDepartments() {
        try {
            List<DepartmentResponseDTO> departments = departmentService.getAllDepartments();
            return ResponseEntity.ok(ApiResponse.success(departments));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<DepartmentResponseDTO>> getDepartmentById(@PathVariable Long departmentId) {
        try {
            DepartmentResponseDTO department = departmentService.getDepartmentById(departmentId);
            return ResponseEntity.ok(ApiResponse.success(department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== HEAD DIRECTOR ONLY APIs (CRUD) ====================

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponseDTO>> createDepartment(
            @RequestAttribute("userId") Long userId,
            @RequestBody DepartmentRequestDTO request) {
        try {
            DepartmentResponseDTO department = departmentService.createDepartment(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Department created successfully", department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<DepartmentResponseDTO>> updateDepartment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long departmentId,
            @RequestBody DepartmentRequestDTO request) {
        try {
            DepartmentResponseDTO department = departmentService.updateDepartment(userId, departmentId, request);
            return ResponseEntity.ok(ApiResponse.success("Department updated successfully", department));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long departmentId) {
        try {
            departmentService.deleteDepartment(userId, departmentId);
            return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}