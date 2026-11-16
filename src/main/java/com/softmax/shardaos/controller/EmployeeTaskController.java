package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.UpdateTaskStatusRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.EmployeeTaskResponseDTO;
import com.softmax.shardaos.service.EmployeeTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee")
public class EmployeeTaskController {

    @Autowired
    private EmployeeTaskService employeeTaskService;

    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<EmployeeTaskResponseDTO>>> getMyTasks(
            @RequestAttribute("userId") Long userId) {
        try {
            List<EmployeeTaskResponseDTO> tasks = employeeTaskService.getMyTasks(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<EmployeeTaskResponseDTO>> getTaskById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId) {
        try {
            EmployeeTaskResponseDTO task = employeeTaskService.getTaskById(userId, taskId);
            return ResponseEntity.ok(ApiResponse.success(task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<EmployeeTaskResponseDTO>> updateTaskStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskStatusRequest request) {
        try {
            EmployeeTaskResponseDTO task = employeeTaskService.updateTaskStatus(userId, taskId, request);
            return ResponseEntity.ok(ApiResponse.success("Task status updated", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<EmployeeTaskResponseDTO>> completeTask(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestBody(required = false) UpdateTaskStatusRequest request) {
        try {
            UpdateTaskStatusRequest completeRequest = new UpdateTaskStatusRequest();
            completeRequest.setStatus("COMPLETED");
            if (request != null && request.getNote() != null) {
                completeRequest.setNote(request.getNote());
            }
            EmployeeTaskResponseDTO task = employeeTaskService.updateTaskStatus(userId, taskId, completeRequest);
            return ResponseEntity.ok(ApiResponse.success("Task completed", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}/note")
    public ResponseEntity<ApiResponse<EmployeeTaskResponseDTO>> addNote(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestBody String note) {
        try {
            EmployeeTaskResponseDTO task = employeeTaskService.addNote(userId, taskId, note);
            return ResponseEntity.ok(ApiResponse.success("Note added", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}