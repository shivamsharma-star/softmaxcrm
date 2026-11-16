package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.TaskCreateRequest;
import com.softmax.shardaos.dto.input.TaskUpdateRequest;
import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.TaskResponseDTO;
import com.softmax.shardaos.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    // ==================== MANAGER APIs ====================

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> createTask(
            @RequestAttribute("userId") Long userId,
            @RequestBody TaskCreateRequest request) {
        try {
            TaskResponseDTO task = taskService.createTask(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Task created successfully & email sent to consultant", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/update/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> updateTask(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestBody TaskUpdateRequest request) {
        try {
            TaskResponseDTO task = taskService.updateTask(userId, taskId, request);
            return ResponseEntity.ok(ApiResponse.success("Task updated successfully", task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId) {
        try {
            taskService.deleteTask(userId, taskId);
            return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-created-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponseDTO>>> getMyCreatedTasks(
            @RequestAttribute("userId") Long userId) {
        try {
            List<TaskResponseDTO> tasks = taskService.getTasksCreatedByUser(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== COMMON APIs ====================

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<TaskResponseDTO>>> getProjectTasks(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<TaskResponseDTO> tasks = taskService.getProjectTasks(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> getTaskById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId) {
        try {
            TaskResponseDTO task = taskService.getTaskById(userId, taskId);
            return ResponseEntity.ok(ApiResponse.success(task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== CONSULTANT APIs ====================

    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponseDTO>>> getMyTasks(
            @RequestAttribute("userId") Long userId) {
        try {
            List<TaskResponseDTO> tasks = taskService.getTasksAssignedToUser(userId);
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/update-status/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> updateTaskStatus(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long taskId,
            @RequestParam String status,
            @RequestParam(required = false) String managerNote) {
        try {
            TaskResponseDTO task = taskService.updateTaskStatus(userId, taskId, status, managerNote);
            return ResponseEntity.ok(ApiResponse.success("Task status updated to " + status, task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}