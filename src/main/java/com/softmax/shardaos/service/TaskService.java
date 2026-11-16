package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.TaskCreateRequest;
import com.softmax.shardaos.dto.input.TaskUpdateRequest;
import com.softmax.shardaos.dto.output.TaskResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    @Autowired
    private TaskDetailsRepository taskRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ==================== MANAGER METHODS ====================

    @Transactional
    public TaskResponseDTO createTask(Long userId, TaskCreateRequest request) {
        if (!isManager(userId)) {
            throw new RuntimeException("Only Manager can create tasks");
        }

        User manager = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        ProjectDetails project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getAssignedManager() == null || !project.getAssignedManager().getUserId().equals(userId)) {
            throw new RuntimeException("You can only create tasks for projects assigned to you");
        }

        User assignedUser = userRepository.findById(request.getAssignedToUserId())
                .orElseThrow(() -> new RuntimeException("Assigned user not found"));

        if (!isConsultant(request.getAssignedToUserId())) {
            throw new RuntimeException("Task can only be assigned to a Consultant");
        }

        TaskDetails task = new TaskDetails();
        task.setProject(project);
        task.setUser(assignedUser);
        task.setTaskDetails(request.getTaskDetails());
        task.setDescription(request.getDescription());
        task.setCreatedBy(manager);
        task.setTaskStatus("PENDING");
        task.setDeadline(request.getDeadline());
        task.setCreatedAt(LocalDateTime.now());

        TaskDetails savedTask = taskRepository.save(task);

        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
        sendTaskAssignedEmailAsync(assignedUser, manager, project, savedTask);

        // 🔥 PHAHLE RESPONSE BHEJO
        return convertToDTO(savedTask);
    }

    @Transactional
    public TaskResponseDTO updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
        if (!isManager(userId)) {
            throw new RuntimeException("Only Manager can update tasks");
        }

        TaskDetails task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getCreatedBy().getUserId().equals(userId)) {
            throw new RuntimeException("You can only update tasks created by you");
        }

        if (request.getTaskDetails() != null) {
            task.setTaskDetails(request.getTaskDetails());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getManagerNote() != null) {
            task.setManagerNote(request.getManagerNote());
        }
        if (request.getTaskStatus() != null) {
            task.setTaskStatus(request.getTaskStatus());
        }

        TaskDetails updatedTask = taskRepository.save(task);

        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
        sendTaskUpdatedEmailAsync(task.getUser(), task.getCreatedBy(), task.getProject(), updatedTask);

        // 🔥 PHAHLE RESPONSE BHEJO
        return convertToDTO(updatedTask);
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        if (!isManager(userId)) {
            throw new RuntimeException("Only Manager can delete tasks");
        }

        TaskDetails task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getCreatedBy().getUserId().equals(userId)) {
            throw new RuntimeException("You can only delete tasks created by you");
        }

        // 🔥 MAIL BACKGROUND ME BHEJEGE (DELETION SE PEHLE DATA SAVE KARO)
        User consultant = task.getUser();
        User manager = task.getCreatedBy();
        ProjectDetails project = task.getProject();
        String taskDetails = task.getTaskDetails();

        taskRepository.delete(task);

        // 🔥 MAIL BACKGROUND ME BHEJO
        sendTaskDeletedEmailAsync(consultant, manager, project, taskDetails);
    }

    public List<TaskResponseDTO> getTasksCreatedByUser(Long userId) {
        if (!isManager(userId)) {
            throw new RuntimeException("Only Manager can view created tasks");
        }
        List<TaskDetails> tasks = taskRepository.findByCreatedByUserId(userId);
        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // ==================== COMMON METHODS ====================

    public List<TaskResponseDTO> getProjectTasks(Long userId, Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean hasAccess = false;

        if (project.getUploader().getUserId().equals(userId)) {
            hasAccess = true;
        }
        else if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
            hasAccess = true;
        }
        else if (isConsultant(userId)) {
            List<TaskDetails> userTasks = taskRepository.findByUserUserId(userId);
            hasAccess = userTasks.stream().anyMatch(t -> t.getProject().getProjectId().equals(projectId));
        }

        if (!hasAccess) {
            throw new RuntimeException("You don't have access to this project's tasks");
        }

        List<TaskDetails> tasks = taskRepository.findByProjectProjectId(projectId);
        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public TaskResponseDTO getTaskById(Long userId, Long taskId) {
        TaskDetails task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        boolean hasAccess = false;

        if (task.getCreatedBy().getUserId().equals(userId)) {
            hasAccess = true;
        }
        else if (task.getUser().getUserId().equals(userId)) {
            hasAccess = true;
        }
        else if (task.getProject().getUploader().getUserId().equals(userId)) {
            hasAccess = true;
        }
        else if (task.getProject().getAssignedManager() != null &&
                task.getProject().getAssignedManager().getUserId().equals(userId)) {
            hasAccess = true;
        }

        if (!hasAccess) {
            throw new RuntimeException("You don't have access to this task");
        }

        return convertToDTO(task);
    }

    // ==================== CONSULTANT METHODS ====================

    public List<TaskResponseDTO> getTasksAssignedToUser(Long userId) {
        if (!isConsultant(userId)) {
            throw new RuntimeException("Only Consultant can view assigned tasks");
        }
        List<TaskDetails> tasks = taskRepository.findByUserUserId(userId);
        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public TaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status, String managerNote) {
        if (!isConsultant(userId)) {
            throw new RuntimeException("Only Consultant can update task status");
        }

        TaskDetails task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("You can only update status of tasks assigned to you");
        }

        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
        }

        String oldStatus = task.getTaskStatus();
        task.setTaskStatus(status);
        if (managerNote != null && !managerNote.isEmpty()) {
            task.setManagerNote(managerNote);
        }

        TaskDetails updatedTask = taskRepository.save(task);

        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
        sendTaskStatusUpdatedEmailAsync(task.getCreatedBy(), task.getUser(), task.getProject(), updatedTask, oldStatus);

        // 🔥 PHAHLE RESPONSE BHEJO
        return convertToDTO(updatedTask);
    }

    // ==================== ASYNC EMAIL METHODS (BACKGROUND ME BHEJEGA) ====================

    @Async
    public void sendTaskAssignedEmailAsync(User consultant, User manager, ProjectDetails project, TaskDetails task) {
        try {
            System.out.println("📧 [Task] Background mail starting for consultant: " + consultant.getEmail());

            String subject = "🔔 New Task Assigned: " + task.getTaskDetails() + " - Softmax Technologies and IT Services";
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "No description");

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .status-badge { display: inline-block; background: #fff3cd; color: #856404; padding: 6px 16px; border-radius: 20px; font-size: 13px; font-weight: 600; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>📋 New Task Assigned</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Hello <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">A new task has been assigned to you by <strong>%s</strong>.</p>
                            <div class="info-box">
                                <p>📌 <strong>Task:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>📝 <strong>Description:</strong></p>
                                <p>%s</p>
                                <p>⏰ <strong>Deadline:</strong> %s</p>
                                <p>📊 <strong>Status:</strong> <span class="status-badge">⏳ PENDING</span></p>
                            </div>
                            <p style="color:#4a5568;">Please login to your dashboard to view and update task status.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View Task</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    consultant.getName(),
                    manager.getName(),
                    task.getTaskDetails(),
                    project.getProjectName(),
                    safeDesc,
                    task.getDeadline() != null ? task.getDeadline().format(formatter) : "Not set"
            );
            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
            System.out.println("✅ [Task] Background mail sent to consultant: " + consultant.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Task] Background mail failed for consultant: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendTaskUpdatedEmailAsync(User consultant, User manager, ProjectDetails project, TaskDetails task) {
        try {
            System.out.println("📧 [Task] Background update mail starting for consultant: " + consultant.getEmail());

            String subject = "✏️ Task Updated: " + task.getTaskDetails() + " - Softmax Technologies and IT Services";
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "No description");

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #2196F3 0%, #1a237e 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e3f2fd; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2196F3; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#2196F3 0%,#1a237e 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(33,150,243,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #2196F3; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>✏️ Task Updated</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Hello <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your task has been updated by <strong>%s</strong>.</p>
                            <div class="info-box">
                                <p>📌 <strong>Task:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>📝 <strong>Description:</strong></p>
                                <p>%s</p>
                                <p>📊 <strong>Status:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">Please login to view the updated details.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View Task</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    consultant.getName(),
                    manager.getName(),
                    task.getTaskDetails(),
                    project.getProjectName(),
                    safeDesc,
                    task.getTaskStatus()
            );
            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
            System.out.println("✅ [Task] Background update mail sent to consultant: " + consultant.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Task] Background update mail failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendTaskDeletedEmailAsync(User consultant, User manager, ProjectDetails project, String taskDetails) {
        try {
            System.out.println("📧 [Task] Background delete mail starting for consultant: " + consultant.getEmail());

            String subject = "❌ Task Deleted: " + taskDetails + " - Softmax Technologies and IT Services";

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #ee5a24 0%, #ff4757 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #ffebee; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #ff4757; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #ee5a24; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>❌ Task Deleted</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Hello <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">The following task has been deleted by <strong>%s</strong>:</p>
                            <div class="info-box">
                                <p>📌 <strong>Task:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">No further action is required from your end.</p>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    consultant.getName(),
                    manager.getName(),
                    taskDetails,
                    project.getProjectName()
            );
            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
            System.out.println("✅ [Task] Background delete mail sent to consultant: " + consultant.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Task] Background delete mail failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendTaskStatusUpdatedEmailAsync(User manager, User consultant, ProjectDetails project, TaskDetails task, String oldStatus) {
        try {
            System.out.println("📧 [Task] Background status update mail starting for manager: " + manager.getEmail());

            String subject = "🔄 Task Status Updated: " + task.getTaskDetails() + " - Softmax Technologies and IT Services";
            String statusColor = task.getTaskStatus().equals("COMPLETED") ? "#11998e" :
                    (task.getTaskStatus().equals("IN_PROGRESS") ? "#2196F3" : "#FF9800");
            String safeNote = EmailService.makeLinksClickable(task.getManagerNote() != null ? task.getManagerNote() : "No additional note");
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "No description");

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, %s 0%%, #1a237e 100%%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #f0f7ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .old-status { color: #ee5a24; text-decoration: line-through; padding: 4px 10px; background: #ffebee; border-radius: 6px; }
                        .new-status { color: #11998e; font-weight: bold; padding: 4px 10px; background: #e8f5e9; border-radius: 6px; }
                        .note-box { background: #fff9e6; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border-left: 5px solid #f6c23e; }
                        .note-box p { margin: 6px 0; color: #5c4a00; font-size: 14px; line-height: 1.6; }
                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🔄 Task Status Updated</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Hello <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;"><strong>%s</strong> has updated task status.</p>
                            <div class="info-box">
                                <p>📌 <strong>Task:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>📝 <strong>Description:</strong></p>
                                <p>%s</p>
                                <p>📊 <strong>Status Changed:</strong> <span class="old-status">%s</span> → <span class="new-status">%s</span></p>
                                <div class="note-box">
                                    <p><strong>💬 Note:</strong></p>
                                    <p>%s</p>
                                </div>
                            </div>
                            <p style="color:#4a5568;">Please login to review the updated status.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View Task</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    statusColor, statusColor,
                    manager.getName(),
                    consultant.getName(),
                    task.getTaskDetails(),
                    project.getProjectName(),
                    safeDesc,
                    oldStatus,
                    task.getTaskStatus(),
                    safeNote
            );
            emailService.sendEmail(manager.getEmail(), subject, htmlContent);
            System.out.println("✅ [Task] Background status update mail sent to manager: " + manager.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Task] Background status update mail failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== HELPER METHODS ====================

    private boolean isManager(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "MANAGER".equals(ut.getStype()))
                .isPresent();
    }

    private boolean isConsultant(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
    }

    private TaskResponseDTO convertToDTO(TaskDetails task) {
        return TaskResponseDTO.builder()
                .taskId(task.getTaskId())
                .projectId(task.getProject().getProjectId())
                .projectName(task.getProject().getProjectName())
                .assignedToUserId(task.getUser().getUserId())
                .assignedToUserName(task.getUser().getName())
                .assignedToUserPhoto(task.getUser().getPhotoUrl())
                .taskDetails(task.getTaskDetails())
                .description(task.getDescription())
                .taskStatus(task.getTaskStatus())
                .createdById(task.getCreatedBy().getUserId())
                .createdByName(task.getCreatedBy().getName())
                .deadline(task.getDeadline())
                .managerNote(task.getManagerNote())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}













//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.TaskCreateRequest;
//import com.softmax.shardaos.dto.input.TaskUpdateRequest;
//import com.softmax.shardaos.dto.output.TaskResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class TaskService {
//
//    @Autowired
//    private TaskDetailsRepository taskRepository;
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    // ==================== MANAGER METHODS ====================
//
//    @Transactional
//    public TaskResponseDTO createTask(Long userId, TaskCreateRequest request) {
//        // Check if user is Manager
//        if (!isManager(userId)) {
//            throw new RuntimeException("Only Manager can create tasks");
//        }
//
//        User manager = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("Manager not found"));
//
//        ProjectDetails project = projectRepository.findById(request.getProjectId())
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        // Verify manager is assigned to this project
//        if (project.getAssignedManager() == null || !project.getAssignedManager().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only create tasks for projects assigned to you");
//        }
//
//        User assignedUser = userRepository.findById(request.getAssignedToUserId())
//                .orElseThrow(() -> new RuntimeException("Assigned user not found"));
//
//        // Check if assigned user is Consultant
//        if (!isConsultant(request.getAssignedToUserId())) {
//            throw new RuntimeException("Task can only be assigned to a Consultant");
//        }
//
//        TaskDetails task = new TaskDetails();
//        task.setProject(project);
//        task.setUser(assignedUser);
//        task.setTaskDetails(request.getTaskDetails());
//        task.setDescription(request.getDescription());
//        task.setCreatedBy(manager);
//        task.setTaskStatus("PENDING");
//        task.setDeadline(request.getDeadline());
//        task.setCreatedAt(LocalDateTime.now());
//
//        TaskDetails savedTask = taskRepository.save(task);
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//        sendTaskAssignedEmailAsync(assignedUser, manager, project, savedTask);
//
//        // 🔥 PHAHLE RESPONSE BHEJO
//        return convertToDTO(savedTask);
//    }
//
//    @Transactional
//    public TaskResponseDTO updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
//        if (!isManager(userId)) {
//            throw new RuntimeException("Only Manager can update tasks");
//        }
//
//        TaskDetails task = taskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getCreatedBy().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only update tasks created by you");
//        }
//
//        if (request.getTaskDetails() != null) {
//            task.setTaskDetails(request.getTaskDetails());
//        }
//        if (request.getDescription() != null) {
//            task.setDescription(request.getDescription());
//        }
//        if (request.getManagerNote() != null) {
//            task.setManagerNote(request.getManagerNote());
//        }
//        if (request.getTaskStatus() != null) {
//            task.setTaskStatus(request.getTaskStatus());
//        }
//
//        TaskDetails updatedTask = taskRepository.save(task);
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//        sendTaskUpdatedEmailAsync(task.getUser(), task.getCreatedBy(), task.getProject(), updatedTask);
//
//        // 🔥 PHAHLE RESPONSE BHEJO
//        return convertToDTO(updatedTask);
//    }
//
//    @Transactional
//    public void deleteTask(Long userId, Long taskId) {
//        if (!isManager(userId)) {
//            throw new RuntimeException("Only Manager can delete tasks");
//        }
//
//        TaskDetails task = taskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getCreatedBy().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only delete tasks created by you");
//        }
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE (DELETION SE PEHLE DATA SAVE KARO)
//        User consultant = task.getUser();
//        User manager = task.getCreatedBy();
//        ProjectDetails project = task.getProject();
//        String taskDetails = task.getTaskDetails();
//
//        taskRepository.delete(task);
//
//        // 🔥 MAIL BACKGROUND ME BHEJO
//        sendTaskDeletedEmailAsync(consultant, manager, project, taskDetails);
//    }
//
//    public List<TaskResponseDTO> getTasksCreatedByUser(Long userId) {
//        if (!isManager(userId)) {
//            throw new RuntimeException("Only Manager can view created tasks");
//        }
//        List<TaskDetails> tasks = taskRepository.findByCreatedByUserId(userId);
//        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//    }
//
//    // ==================== COMMON METHODS ====================
//
//    public List<TaskResponseDTO> getProjectTasks(Long userId, Long projectId) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        boolean hasAccess = false;
//
//        if (project.getUploader().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//        else if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//        else if (isConsultant(userId)) {
//            List<TaskDetails> userTasks = taskRepository.findByUserUserId(userId);
//            hasAccess = userTasks.stream().anyMatch(t -> t.getProject().getProjectId().equals(projectId));
//        }
//
//        if (!hasAccess) {
//            throw new RuntimeException("You don't have access to this project's tasks");
//        }
//
//        List<TaskDetails> tasks = taskRepository.findByProjectProjectId(projectId);
//        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//    }
//
//    public TaskResponseDTO getTaskById(Long userId, Long taskId) {
//        TaskDetails task = taskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        boolean hasAccess = false;
//
//        if (task.getCreatedBy().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//        else if (task.getUser().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//        else if (task.getProject().getUploader().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//        else if (task.getProject().getAssignedManager() != null &&
//                task.getProject().getAssignedManager().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//
//        if (!hasAccess) {
//            throw new RuntimeException("You don't have access to this task");
//        }
//
//        return convertToDTO(task);
//    }
//
//    // ==================== CONSULTANT METHODS ====================
//
//    public List<TaskResponseDTO> getTasksAssignedToUser(Long userId) {
//        if (!isConsultant(userId)) {
//            throw new RuntimeException("Only Consultant can view assigned tasks");
//        }
//        List<TaskDetails> tasks = taskRepository.findByUserUserId(userId);
//        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//    }
//
//    @Transactional
//    public TaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status, String managerNote) {
//        if (!isConsultant(userId)) {
//            throw new RuntimeException("Only Consultant can update task status");
//        }
//
//        TaskDetails task = taskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getUser().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only update status of tasks assigned to you");
//        }
//
//        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
//            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
//        }
//
//        String oldStatus = task.getTaskStatus();
//        task.setTaskStatus(status);
//        if (managerNote != null && !managerNote.isEmpty()) {
//            task.setManagerNote(managerNote);
//        }
//
//        TaskDetails updatedTask = taskRepository.save(task);
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//        sendTaskStatusUpdatedEmailAsync(task.getCreatedBy(), task.getUser(), task.getProject(), updatedTask, oldStatus);
//
//        // 🔥 PHAHLE RESPONSE BHEJO
//        return convertToDTO(updatedTask);
//    }
//
//    // ==================== ASYNC EMAIL METHODS (BACKGROUND ME BHEJEGA) ====================
//
//    @Async
//    public void sendTaskAssignedEmailAsync(User consultant, User manager, ProjectDetails project, TaskDetails task) {
//        try {
//            System.out.println("📧 [Task] Background mail starting for consultant: " + consultant.getEmail());
//
//            String subject = "🔔 New Task Assigned: " + task.getTaskDetails();
//            String htmlContent = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head><style>
//                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
//                    .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//                    .header { background: #4CAF50; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//                    .content { padding: 20px; }
//                    .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #4CAF50; margin: 15px 0; }
//                    .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
//                    .status-pending { color: #ff9800; font-weight: bold; }
//                </style></head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h2>📋 New Task Assigned</h2>
//                        </div>
//                        <div class="content">
//                            <p>Hello <strong>%s</strong>,</p>
//                            <p>A new task has been assigned to you by <strong>%s</strong>.</p>
//                            <div class="task-detail">
//                                <h3>📌 Task: %s</h3>
//                                <p><strong>Project:</strong> %s</p>
//                                <p><strong>Description:</strong> %s</p>
//                                <p><strong>Deadline:</strong> %s</p>
//                                <p><strong>Status:</strong> <span class="status-pending">PENDING</span></p>
//                            </div>
//                            <p>Please login to your dashboard to view and update task status.</p>
//                        </div>
//                        <div class="footer">
//                            <p>This is an automated message. Please do not reply to this email.</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    consultant.getName(),
//                    manager.getName(),
//                    task.getTaskDetails(),
//                    project.getProjectName(),
//                    task.getDescription() != null ? task.getDescription() : "No description",
//                    task.getDeadline() != null ? task.getDeadline().format(formatter) : "Not set"
//            );
//            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//            System.out.println("✅ [Task] Background mail sent to consultant: " + consultant.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Task] Background mail failed for consultant: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Async
//    public void sendTaskUpdatedEmailAsync(User consultant, User manager, ProjectDetails project, TaskDetails task) {
//        try {
//            System.out.println("📧 [Task] Background update mail starting for consultant: " + consultant.getEmail());
//
//            String subject = "✏️ Task Updated: " + task.getTaskDetails();
//            String htmlContent = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head><style>
//                    body { font-family: Arial, sans-serif; line-height: 1.6; }
//                    .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//                    .header { background: #2196F3; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//                    .content { padding: 20px; }
//                    .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #2196F3; margin: 15px 0; }
//                </style></head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h2>✏️ Task Updated</h2>
//                        </div>
//                        <div class="content">
//                            <p>Hello <strong>%s</strong>,</p>
//                            <p>Your task has been updated by <strong>%s</strong>.</p>
//                            <div class="task-detail">
//                                <h3>📌 Task: %s</h3>
//                                <p><strong>Project:</strong> %s</p>
//                                <p><strong>Description:</strong> %s</p>
//                                <p><strong>Status:</strong> %s</p>
//                            </div>
//                            <p>Please login to view the updated details.</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    consultant.getName(),
//                    manager.getName(),
//                    task.getTaskDetails(),
//                    project.getProjectName(),
//                    task.getDescription() != null ? task.getDescription() : "No description",
//                    task.getTaskStatus()
//            );
//            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//            System.out.println("✅ [Task] Background update mail sent to consultant: " + consultant.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Task] Background update mail failed: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Async
//    public void sendTaskDeletedEmailAsync(User consultant, User manager, ProjectDetails project, String taskDetails) {
//        try {
//            System.out.println("📧 [Task] Background delete mail starting for consultant: " + consultant.getEmail());
//
//            String subject = "❌ Task Deleted: " + taskDetails;
//            String htmlContent = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head><style>
//                    body { font-family: Arial, sans-serif; }
//                    .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//                    .header { background: #f44336; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//                    .content { padding: 20px; }
//                    .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #f44336; margin: 15px 0; }
//                </style></head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h2>❌ Task Deleted</h2>
//                        </div>
//                        <div class="content">
//                            <p>Hello <strong>%s</strong>,</p>
//                            <p>The following task has been deleted by <strong>%s</strong>:</p>
//                            <div class="task-detail">
//                                <h3>📌 Task: %s</h3>
//                                <p><strong>Project:</strong> %s</p>
//                            </div>
//                            <p>No further action is required from your end.</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    consultant.getName(),
//                    manager.getName(),
//                    taskDetails,
//                    project.getProjectName()
//            );
//            emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//            System.out.println("✅ [Task] Background delete mail sent to consultant: " + consultant.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Task] Background delete mail failed: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Async
//    public void sendTaskStatusUpdatedEmailAsync(User manager, User consultant, ProjectDetails project, TaskDetails task, String oldStatus) {
//        try {
//            System.out.println("📧 [Task] Background status update mail starting for manager: " + manager.getEmail());
//
//            String subject = "🔄 Task Status Updated: " + task.getTaskDetails();
//            String statusColor = task.getTaskStatus().equals("COMPLETED") ? "#4CAF50" :
//                    (task.getTaskStatus().equals("IN_PROGRESS") ? "#2196F3" : "#ff9800");
//
//            String htmlContent = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head><style>
//                    body { font-family: Arial, sans-serif; }
//                    .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//                    .header { background: %s; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//                    .content { padding: 20px; }
//                    .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid %s; margin: 15px 0; }
//                    .old-status { color: #f44336; text-decoration: line-through; }
//                    .new-status { color: #4CAF50; font-weight: bold; }
//                </style></head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h2>🔄 Task Status Updated</h2>
//                        </div>
//                        <div class="content">
//                            <p>Hello <strong>%s</strong>,</p>
//                            <p><strong>%s</strong> has updated task status.</p>
//                            <div class="task-detail">
//                                <h3>📌 Task: %s</h3>
//                                <p><strong>Project:</strong> %s</p>
//                                <p><strong>Status Changed:</strong> <span class="old-status">%s</span> → <span class="new-status">%s</span></p>
//                                <p><strong>Note:</strong> %s</p>
//                            </div>
//                            <p>Please login to review the updated status.</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    statusColor, statusColor,
//                    manager.getName(),
//                    consultant.getName(),
//                    task.getTaskDetails(),
//                    project.getProjectName(),
//                    oldStatus,
//                    task.getTaskStatus(),
//                    task.getManagerNote() != null ? task.getManagerNote() : "No additional note"
//            );
//            emailService.sendEmail(manager.getEmail(), subject, htmlContent);
//            System.out.println("✅ [Task] Background status update mail sent to manager: " + manager.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Task] Background status update mail failed: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private boolean isManager(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
//                .filter(ut -> "MANAGER".equals(ut.getStype()))
//                .isPresent();
//    }
//
//    private boolean isConsultant(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
//    }
//
//    private TaskResponseDTO convertToDTO(TaskDetails task) {
//        return TaskResponseDTO.builder()
//                .taskId(task.getTaskId())
//                .projectId(task.getProject().getProjectId())
//                .projectName(task.getProject().getProjectName())
//                .assignedToUserId(task.getUser().getUserId())
//                .assignedToUserName(task.getUser().getName())
//                .assignedToUserPhoto(task.getUser().getPhotoUrl())
//                .taskDetails(task.getTaskDetails())
//                .description(task.getDescription())
//                .taskStatus(task.getTaskStatus())
//                .createdById(task.getCreatedBy().getUserId())
//                .createdByName(task.getCreatedBy().getName())
//                .deadline(task.getDeadline())
//                .managerNote(task.getManagerNote())
//                .createdAt(task.getCreatedAt())
//                .updatedAt(task.getUpdatedAt())
//                .build();
//    }
//}
//
//
//
//
//
//
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.TaskCreateRequest;
////import com.softmax.shardaos.dto.input.TaskUpdateRequest;
////import com.softmax.shardaos.dto.output.TaskResponseDTO;
////import com.softmax.shardaos.models.*;
////import com.softmax.shardaos.repository.*;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import org.springframework.transaction.annotation.Transactional;
////
////import java.time.LocalDateTime;
////import java.time.format.DateTimeFormatter;
////import java.util.List;
////import java.util.stream.Collectors;
////
////@Service
////public class TaskService {
////
////    @Autowired
////    private TaskDetailsRepository taskRepository;
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////
////    // ==================== MANAGER METHODS ====================
////
////    @Transactional
////    public TaskResponseDTO createTask(Long userId, TaskCreateRequest request) {
////        // Check if user is Manager
////        if (!isManager(userId)) {
////            throw new RuntimeException("Only Manager can create tasks");
////        }
////
////        User manager = userRepository.findById(userId)
////                .orElseThrow(() -> new RuntimeException("Manager not found"));
////
////        ProjectDetails project = projectRepository.findById(request.getProjectId())
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        // Verify manager is assigned to this project
////        if (project.getAssignedManager() == null || !project.getAssignedManager().getUserId().equals(userId)) {
////            throw new RuntimeException("You can only create tasks for projects assigned to you");
////        }
////
////        User assignedUser = userRepository.findById(request.getAssignedToUserId())
////                .orElseThrow(() -> new RuntimeException("Assigned user not found"));
////
////        // Check if assigned user is Consultant
////        if (!isConsultant(request.getAssignedToUserId())) {
////            throw new RuntimeException("Task can only be assigned to a Consultant");
////        }
////
////        TaskDetails task = new TaskDetails();
////        task.setProject(project);
////        task.setUser(assignedUser);
////        task.setTaskDetails(request.getTaskDetails());
////        task.setDescription(request.getDescription());
////        task.setCreatedBy(manager);
////        task.setTaskStatus("PENDING");
////        task.setDeadline(request.getDeadline());
////        task.setCreatedAt(LocalDateTime.now());
////
////        TaskDetails savedTask = taskRepository.save(task);
////
////        sendTaskAssignedEmail(assignedUser, manager, project, savedTask);
////
////        return convertToDTO(savedTask);
////    }
////
////    @Transactional
////    public TaskResponseDTO updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
////        if (!isManager(userId)) {
////            throw new RuntimeException("Only Manager can update tasks");
////        }
////
////        TaskDetails task = taskRepository.findById(taskId)
////                .orElseThrow(() -> new RuntimeException("Task not found"));
////
////        if (!task.getCreatedBy().getUserId().equals(userId)) {
////            throw new RuntimeException("You can only update tasks created by you");
////        }
////
////        if (request.getTaskDetails() != null) {
////            task.setTaskDetails(request.getTaskDetails());
////        }
////        if (request.getDescription() != null) {
////            task.setDescription(request.getDescription());
////        }
////        if (request.getManagerNote() != null) {
////            task.setManagerNote(request.getManagerNote());
////        }
////        if (request.getTaskStatus() != null) {
////            task.setTaskStatus(request.getTaskStatus());
////        }
////
////        TaskDetails updatedTask = taskRepository.save(task);
////
////        sendTaskUpdatedEmail(task.getUser(), task.getCreatedBy(), task.getProject(), updatedTask);
////
////        return convertToDTO(updatedTask);
////    }
////
////    @Transactional
////    public void deleteTask(Long userId, Long taskId) {
////        if (!isManager(userId)) {
////            throw new RuntimeException("Only Manager can delete tasks");
////        }
////
////        TaskDetails task = taskRepository.findById(taskId)
////                .orElseThrow(() -> new RuntimeException("Task not found"));
////
////        if (!task.getCreatedBy().getUserId().equals(userId)) {
////            throw new RuntimeException("You can only delete tasks created by you");
////        }
////
////        sendTaskDeletedEmail(task.getUser(), task.getCreatedBy(), task.getProject(), task);
////
////        taskRepository.delete(task);
////    }
////
////    public List<TaskResponseDTO> getTasksCreatedByUser(Long userId) {
////        if (!isManager(userId)) {
////            throw new RuntimeException("Only Manager can view created tasks");
////        }
////        List<TaskDetails> tasks = taskRepository.findByCreatedByUserId(userId);
////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
////    }
////
////    // ==================== COMMON METHODS ====================
////
////    public List<TaskResponseDTO> getProjectTasks(Long userId, Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        boolean hasAccess = false;
////
////        if (project.getUploader().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////        else if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////        else if (isConsultant(userId)) {
////            List<TaskDetails> userTasks = taskRepository.findByUserUserId(userId);
////            hasAccess = userTasks.stream().anyMatch(t -> t.getProject().getProjectId().equals(projectId));
////        }
////
////        if (!hasAccess) {
////            throw new RuntimeException("You don't have access to this project's tasks");
////        }
////
////        List<TaskDetails> tasks = taskRepository.findByProjectProjectId(projectId);
////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
////    }
////
////    public TaskResponseDTO getTaskById(Long userId, Long taskId) {
////        TaskDetails task = taskRepository.findById(taskId)
////                .orElseThrow(() -> new RuntimeException("Task not found"));
////
////        boolean hasAccess = false;
////
////        if (task.getCreatedBy().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////        else if (task.getUser().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////        else if (task.getProject().getUploader().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////        else if (task.getProject().getAssignedManager() != null &&
////                task.getProject().getAssignedManager().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////
////        if (!hasAccess) {
////            throw new RuntimeException("You don't have access to this task");
////        }
////
////        return convertToDTO(task);
////    }
////
////    // ==================== CONSULTANT METHODS ====================
////
////    public List<TaskResponseDTO> getTasksAssignedToUser(Long userId) {
////        if (!isConsultant(userId)) {
////            throw new RuntimeException("Only Consultant can view assigned tasks");
////        }
////        List<TaskDetails> tasks = taskRepository.findByUserUserId(userId);
////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
////    }
////
////    @Transactional
////    public TaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status, String managerNote) {
////        if (!isConsultant(userId)) {
////            throw new RuntimeException("Only Consultant can update task status");
////        }
////
////        TaskDetails task = taskRepository.findById(taskId)
////                .orElseThrow(() -> new RuntimeException("Task not found"));
////
////        if (!task.getUser().getUserId().equals(userId)) {
////            throw new RuntimeException("You can only update status of tasks assigned to you");
////        }
////
////        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
////            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
////        }
////
////        String oldStatus = task.getTaskStatus();
////        task.setTaskStatus(status);
////        if (managerNote != null && !managerNote.isEmpty()) {
////            task.setManagerNote(managerNote);
////        }
////
////        TaskDetails updatedTask = taskRepository.save(task);
////
////        sendTaskStatusUpdatedEmail(task.getCreatedBy(), task.getUser(), task.getProject(), updatedTask, oldStatus);
////
////        return convertToDTO(updatedTask);
////    }
////
////    // ==================== EMAIL METHODS ====================
////
////    private void sendTaskAssignedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
////        String subject = "🔔 New Task Assigned: " + task.getTaskDetails();
////        String htmlContent = String.format("""
////            <html><body>
////            <h2>📋 New Task Assigned</h2>
////            <p>Hello <strong>%s</strong>,</p>
////            <p>A new task has been assigned to you by <strong>%s</strong>.</p>
////            <p><strong>Task:</strong> %s</p>
////            <p><strong>Project:</strong> %s</p>
////            <p><strong>Deadline:</strong> %s</p>
////            </body></html>
////            """,
////                consultant.getName(), manager.getName(), task.getTaskDetails(),
////                project.getProjectName(), task.getDeadline() != null ? task.getDeadline().format(formatter) : "Not set");
////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
////    }
////
////    private void sendTaskUpdatedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
////        String subject = "✏️ Task Updated: " + task.getTaskDetails();
////        String htmlContent = String.format("""
////            <html><body>
////            <h2>✏️ Task Updated</h2>
////            <p>Hello <strong>%s</strong>,</p>
////            <p>Your task has been updated by <strong>%s</strong>.</p>
////            </body></html>
////            """, consultant.getName(), manager.getName());
////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
////    }
////
////    private void sendTaskDeletedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
////        String subject = "❌ Task Deleted: " + task.getTaskDetails();
////        String htmlContent = String.format("""
////            <html><body>
////            <h2>❌ Task Deleted</h2>
////            <p>Hello <strong>%s</strong>,</p>
////            <p>Task <strong>%s</strong> has been deleted.</p>
////            </body></html>
////            """, consultant.getName(), task.getTaskDetails());
////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
////    }
////
////    private void sendTaskStatusUpdatedEmail(User manager, User consultant, ProjectDetails project, TaskDetails task, String oldStatus) {
////        String subject = "🔄 Task Status Updated: " + task.getTaskDetails();
////        String htmlContent = String.format("""
////            <html><body>
////            <h2>🔄 Task Status Updated</h2>
////            <p>Hello <strong>%s</strong>,</p>
////            <p><strong>%s</strong> has updated task status from %s to %s.</p>
////            </body></html>
////            """, manager.getName(), consultant.getName(), oldStatus, task.getTaskStatus());
////        emailService.sendEmail(manager.getEmail(), subject, htmlContent);
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    // ✅ FIXED: Correct isManager() method
////    private boolean isManager(Long userId) {
////        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
////                .filter(ut -> "MANAGER".equals(ut.getStype()))
////                .isPresent();
////    }
////
////    private boolean isConsultant(Long userId) {
////        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
////    }
////
////    private TaskResponseDTO convertToDTO(TaskDetails task) {
////        return TaskResponseDTO.builder()
////                .taskId(task.getTaskId())
////                .projectId(task.getProject().getProjectId())
////                .projectName(task.getProject().getProjectName())
////                .assignedToUserId(task.getUser().getUserId())
////                .assignedToUserName(task.getUser().getName())
////                .assignedToUserPhoto(task.getUser().getPhotoUrl())
////                .taskDetails(task.getTaskDetails())
////                .description(task.getDescription())
////                .taskStatus(task.getTaskStatus())
////                .createdById(task.getCreatedBy().getUserId())
////                .createdByName(task.getCreatedBy().getName())
////                .deadline(task.getDeadline())
////                .managerNote(task.getManagerNote())
////                .createdAt(task.getCreatedAt())
////                .updatedAt(task.getUpdatedAt())
////                .build();
////    }
////}
//////package com.softmax.shardaos.service;
//////
//////import com.softmax.shardaos.dto.input.TaskCreateRequest;
//////import com.softmax.shardaos.dto.input.TaskUpdateRequest;
//////import com.softmax.shardaos.dto.output.TaskResponseDTO;
//////import com.softmax.shardaos.models.*;
//////import com.softmax.shardaos.repository.*;
//////import org.springframework.beans.factory.annotation.Autowired;
//////import org.springframework.stereotype.Service;
//////import org.springframework.transaction.annotation.Transactional;
//////
//////import java.time.LocalDateTime;
//////import java.time.format.DateTimeFormatter;
//////import java.util.List;
//////import java.util.stream.Collectors;
//////
//////@Service
//////public class TaskService {
//////
//////    @Autowired
//////    private TaskDetailsRepository taskRepository;
//////
//////    @Autowired
//////    private ProjectDetailsRepository projectRepository;
//////
//////    @Autowired
//////    private UserRepository userRepository;
//////
//////    @Autowired
//////    private UserTypeRepository userTypeRepository;
//////
//////    @Autowired
//////    private EmailService emailService;
//////
//////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//////
//////    // ==================== MANAGER METHODS ====================
//////
//////    @Transactional
//////    public TaskResponseDTO createTask(Long userId, TaskCreateRequest request) {
//////        // Check if user is Manager
//////        if (!isManager(userId)) {
//////            throw new RuntimeException("Only Manager can create tasks");
//////        }
//////
//////        User manager = userRepository.findById(userId)
//////                .orElseThrow(() -> new RuntimeException("Manager not found"));
//////
//////        ProjectDetails project = projectRepository.findById(request.getProjectId())
//////                .orElseThrow(() -> new RuntimeException("Project not found"));
//////
//////        // Verify manager is assigned to this project
//////        if (project.getAssignedManager() == null || !project.getAssignedManager().getUserId().equals(userId)) {
//////            throw new RuntimeException("You can only create tasks for projects assigned to you");
//////        }
//////
//////        User assignedUser = userRepository.findById(request.getAssignedToUserId())
//////                .orElseThrow(() -> new RuntimeException("Assigned user not found"));
//////
//////        // Check if assigned user is Consultant
//////        if (!isConsultant(request.getAssignedToUserId())) {
//////            throw new RuntimeException("Task can only be assigned to a Consultant");
//////        }
//////
//////        TaskDetails task = new TaskDetails();
//////        task.setProject(project);
//////        task.setUser(assignedUser);
//////        task.setTaskDetails(request.getTaskDetails());
//////        task.setDescription(request.getDescription());
//////        task.setCreatedBy(manager);
//////        task.setTaskStatus("PENDING");
//////        task.setDeadline(request.getDeadline());
//////        task.setCreatedAt(LocalDateTime.now());
//////
//////        TaskDetails savedTask = taskRepository.save(task);
//////
//////        // ✅ Send Email to Consultant
//////        sendTaskAssignedEmail(assignedUser, manager, project, savedTask);
//////
//////        return convertToDTO(savedTask);
//////    }
//////
//////    @Transactional
//////    public TaskResponseDTO updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
//////        if (!isManager(userId)) {
//////            throw new RuntimeException("Only Manager can update tasks");
//////        }
//////
//////        TaskDetails task = taskRepository.findById(taskId)
//////                .orElseThrow(() -> new RuntimeException("Task not found"));
//////
//////        if (!task.getCreatedBy().getUserId().equals(userId)) {
//////            throw new RuntimeException("You can only update tasks created by you");
//////        }
//////
//////        if (request.getTaskDetails() != null) {
//////            task.setTaskDetails(request.getTaskDetails());
//////        }
//////        if (request.getDescription() != null) {
//////            task.setDescription(request.getDescription());
//////        }
//////        if (request.getManagerNote() != null) {
//////            task.setManagerNote(request.getManagerNote());
//////        }
//////        if (request.getTaskStatus() != null) {
//////            task.setTaskStatus(request.getTaskStatus());
//////        }
//////
//////        TaskDetails updatedTask = taskRepository.save(task);
//////
//////        // ✅ Send Email on Task Update
//////        sendTaskUpdatedEmail(task.getUser(), task.getCreatedBy(), task.getProject(), updatedTask);
//////
//////        return convertToDTO(updatedTask);
//////    }
//////
//////    @Transactional
//////    public void deleteTask(Long userId, Long taskId) {
//////        if (!isManager(userId)) {
//////            throw new RuntimeException("Only Manager can delete tasks");
//////        }
//////
//////        TaskDetails task = taskRepository.findById(taskId)
//////                .orElseThrow(() -> new RuntimeException("Task not found"));
//////
//////        if (!task.getCreatedBy().getUserId().equals(userId)) {
//////            throw new RuntimeException("You can only delete tasks created by you");
//////        }
//////
//////        // ✅ Send Email before deletion
//////        sendTaskDeletedEmail(task.getUser(), task.getCreatedBy(), task.getProject(), task);
//////
//////        taskRepository.delete(task);
//////    }
//////
//////    public List<TaskResponseDTO> getTasksCreatedByUser(Long userId) {
//////        if (!isManager(userId)) {
//////            throw new RuntimeException("Only Manager can view created tasks");
//////        }
//////        List<TaskDetails> tasks = taskRepository.findByCreatedByUserId(userId);
//////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//////    }
//////
//////    // ==================== COMMON METHODS ====================
//////
//////    public List<TaskResponseDTO> getProjectTasks(Long userId, Long projectId) {
//////        ProjectDetails project = projectRepository.findById(projectId)
//////                .orElseThrow(() -> new RuntimeException("Project not found"));
//////
//////        boolean hasAccess = false;
//////
//////        if (project.getUploader().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////        else if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////        else if (isConsultant(userId)) {
//////            List<TaskDetails> userTasks = taskRepository.findByUserUserId(userId);
//////            hasAccess = userTasks.stream().anyMatch(t -> t.getProject().getProjectId().equals(projectId));
//////        }
//////
//////        if (!hasAccess) {
//////            throw new RuntimeException("You don't have access to this project's tasks");
//////        }
//////
//////        List<TaskDetails> tasks = taskRepository.findByProjectProjectId(projectId);
//////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//////    }
//////
//////    public TaskResponseDTO getTaskById(Long userId, Long taskId) {
//////        TaskDetails task = taskRepository.findById(taskId)
//////                .orElseThrow(() -> new RuntimeException("Task not found"));
//////
//////        boolean hasAccess = false;
//////
//////        if (task.getCreatedBy().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////        else if (task.getUser().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////        else if (task.getProject().getUploader().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////        else if (task.getProject().getAssignedManager() != null &&
//////                task.getProject().getAssignedManager().getUserId().equals(userId)) {
//////            hasAccess = true;
//////        }
//////
//////        if (!hasAccess) {
//////            throw new RuntimeException("You don't have access to this task");
//////        }
//////
//////        return convertToDTO(task);
//////    }
//////
//////    // ==================== CONSULTANT METHODS ====================
//////
//////    public List<TaskResponseDTO> getTasksAssignedToUser(Long userId) {
//////        if (!isConsultant(userId)) {
//////            throw new RuntimeException("Only Consultant can view assigned tasks");
//////        }
//////        List<TaskDetails> tasks = taskRepository.findByUserUserId(userId);
//////        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//////    }
//////
//////    @Transactional
//////    public TaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status, String managerNote) {
//////        if (!isConsultant(userId)) {
//////            throw new RuntimeException("Only Consultant can update task status");
//////        }
//////
//////        TaskDetails task = taskRepository.findById(taskId)
//////                .orElseThrow(() -> new RuntimeException("Task not found"));
//////
//////        if (!task.getUser().getUserId().equals(userId)) {
//////            throw new RuntimeException("You can only update status of tasks assigned to you");
//////        }
//////
//////        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
//////            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
//////        }
//////
//////        String oldStatus = task.getTaskStatus();
//////        task.setTaskStatus(status);
//////        if (managerNote != null && !managerNote.isEmpty()) {
//////            task.setManagerNote(managerNote);
//////        }
//////
//////        TaskDetails updatedTask = taskRepository.save(task);
//////
//////        // ✅ Send Email on Status Change
//////        sendTaskStatusUpdatedEmail(task.getCreatedBy(), task.getUser(), task.getProject(), updatedTask, oldStatus);
//////
//////        return convertToDTO(updatedTask);
//////    }
//////
//////    // ==================== EMAIL METHODS ====================
//////
//////    private void sendTaskAssignedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
//////        String subject = "🔔 New Task Assigned: " + task.getTaskDetails();
//////
//////        String htmlContent = String.format("""
//////            <!DOCTYPE html>
//////            <html>
//////            <head><style>
//////                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
//////                .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//////                .header { background: #4CAF50; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//////                .content { padding: 20px; }
//////                .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #4CAF50; margin: 15px 0; }
//////                .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
//////                .status-pending { color: #ff9800; font-weight: bold; }
//////                .btn { display: inline-block; padding: 10px 20px; background: #4CAF50; color: white; text-decoration: none; border-radius: 5px; }
//////            </style></head>
//////            <body>
//////                <div class="container">
//////                    <div class="header">
//////                        <h2>📋 New Task Assigned</h2>
//////                    </div>
//////                    <div class="content">
//////                        <p>Hello <strong>%s</strong>,</p>
//////                        <p>A new task has been assigned to you by <strong>%s</strong>.</p>
//////
//////                        <div class="task-detail">
//////                            <h3>📌 Task: %s</h3>
//////                            <p><strong>Project:</strong> %s</p>
//////                            <p><strong>Description:</strong> %s</p>
//////                            <p><strong>Deadline:</strong> %s</p>
//////                            <p><strong>Status:</strong> <span class="status-pending">PENDING</span></p>
//////                        </div>
//////
//////                        <p>Please login to your dashboard to view and update task status.</p>
//////                        <p style="text-align: center;">
//////                            <a href="#" class="btn">View Task</a>
//////                        </p>
//////                    </div>
//////                    <div class="footer">
//////                        <p>This is an automated message. Please do not reply to this email.</p>
//////                    </div>
//////                </div>
//////            </body>
//////            </html>
//////            """,
//////                consultant.getName(),
//////                manager.getName(),
//////                task.getTaskDetails(),
//////                project.getProjectName(),
//////                task.getDescription() != null ? task.getDescription() : "No description",
//////                task.getDeadline() != null ? task.getDeadline().format(formatter) : "Not set"
//////        );
//////
//////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//////    }
//////
//////    private void sendTaskUpdatedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
//////        String subject = "✏️ Task Updated: " + task.getTaskDetails();
//////
//////        String htmlContent = String.format("""
//////            <!DOCTYPE html>
//////            <html>
//////            <head><style>
//////                body { font-family: Arial, sans-serif; line-height: 1.6; }
//////                .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//////                .header { background: #2196F3; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//////                .content { padding: 20px; }
//////                .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #2196F3; margin: 15px 0; }
//////                .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
//////            </style></head>
//////            <body>
//////                <div class="container">
//////                    <div class="header">
//////                        <h2>✏️ Task Updated</h2>
//////                    </div>
//////                    <div class="content">
//////                        <p>Hello <strong>%s</strong>,</p>
//////                        <p>Your task has been updated by <strong>%s</strong>.</p>
//////
//////                        <div class="task-detail">
//////                            <h3>📌 Task: %s</h3>
//////                            <p><strong>Project:</strong> %s</p>
//////                            <p><strong>Description:</strong> %s</p>
//////                            <p><strong>Status:</strong> %s</p>
//////                        </div>
//////
//////                        <p>Please login to view the updated details.</p>
//////                    </div>
//////                    <div class="footer">
//////                        <p>This is an automated message.</p>
//////                    </div>
//////                </div>
//////            </body>
//////            </html>
//////            """,
//////                consultant.getName(),
//////                manager.getName(),
//////                task.getTaskDetails(),
//////                project.getProjectName(),
//////                task.getDescription() != null ? task.getDescription() : "No description",
//////                task.getTaskStatus()
//////        );
//////
//////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//////    }
//////
//////    private void sendTaskDeletedEmail(User consultant, User manager, ProjectDetails project, TaskDetails task) {
//////        String subject = "❌ Task Deleted: " + task.getTaskDetails();
//////
//////        String htmlContent = String.format("""
//////            <!DOCTYPE html>
//////            <html>
//////            <head><style>
//////                body { font-family: Arial, sans-serif; }
//////                .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//////                .header { background: #f44336; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//////                .content { padding: 20px; }
//////                .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid #f44336; margin: 15px 0; }
//////            </style></head>
//////            <body>
//////                <div class="container">
//////                    <div class="header">
//////                        <h2>❌ Task Deleted</h2>
//////                    </div>
//////                    <div class="content">
//////                        <p>Hello <strong>%s</strong>,</p>
//////                        <p>The following task has been deleted by <strong>%s</strong>:</p>
//////
//////                        <div class="task-detail">
//////                            <h3>📌 Task: %s</h3>
//////                            <p><strong>Project:</strong> %s</p>
//////                        </div>
//////
//////                        <p>No further action is required from your end.</p>
//////                    </div>
//////                </div>
//////            </body>
//////            </html>
//////            """,
//////                consultant.getName(),
//////                manager.getName(),
//////                task.getTaskDetails(),
//////                project.getProjectName()
//////        );
//////
//////        emailService.sendEmail(consultant.getEmail(), subject, htmlContent);
//////    }
//////
//////    private void sendTaskStatusUpdatedEmail(User manager, User consultant, ProjectDetails project, TaskDetails task, String oldStatus) {
//////        String subject = "🔄 Task Status Updated: " + task.getTaskDetails();
//////
//////        String statusColor = task.getTaskStatus().equals("COMPLETED") ? "#4CAF50" :
//////                (task.getTaskStatus().equals("IN_PROGRESS") ? "#2196F3" : "#ff9800");
//////
//////        String htmlContent = String.format("""
//////            <!DOCTYPE html>
//////            <html>
//////            <head><style>
//////                body { font-family: Arial, sans-serif; }
//////                .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
//////                .header { background: %s; color: white; padding: 10px; text-align: center; border-radius: 5px; }
//////                .content { padding: 20px; }
//////                .task-detail { background: #f9f9f9; padding: 15px; border-left: 4px solid %s; margin: 15px 0; }
//////                .old-status { color: #f44336; text-decoration: line-through; }
//////                .new-status { color: #4CAF50; font-weight: bold; }
//////            </style></head>
//////            <body>
//////                <div class="container">
//////                    <div class="header">
//////                        <h2>🔄 Task Status Updated</h2>
//////                    </div>
//////                    <div class="content">
//////                        <p>Hello <strong>%s</strong>,</p>
//////                        <p><strong>%s</strong> has updated task status.</p>
//////
//////                        <div class="task-detail">
//////                            <h3>📌 Task: %s</h3>
//////                            <p><strong>Project:</strong> %s</p>
//////                            <p><strong>Status Changed:</strong> <span class="old-status">%s</span> → <span class="new-status">%s</span></p>
//////                            <p><strong>Note:</strong> %s</p>
//////                        </div>
//////
//////                        <p>Please login to review the updated status.</p>
//////                    </div>
//////                </div>
//////            </body>
//////            </html>
//////            """,
//////                statusColor, statusColor,
//////                manager.getName(),
//////                consultant.getName(),
//////                task.getTaskDetails(),
//////                project.getProjectName(),
//////                oldStatus,
//////                task.getTaskStatus(),
//////                task.getManagerNote() != null ? task.getManagerNote() : "No additional note"
//////        );
//////
//////        emailService.sendEmail(manager.getEmail(), subject, htmlContent);
//////    }
//////
//////    // ==================== HELPER METHODS ====================
//////
//////    private boolean isManager(Long userId) {
//////        return userTypeRepository.findByUserUserIdAndPtype(userId, "MANAGER").isPresent();
//////    }
//////
//////    private boolean isConsultant(Long userId) {
//////        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
//////    }
//////
//////    private TaskResponseDTO convertToDTO(TaskDetails task) {
//////        return TaskResponseDTO.builder()
//////                .taskId(task.getTaskId())
//////                .projectId(task.getProject().getProjectId())
//////                .projectName(task.getProject().getProjectName())
//////                .assignedToUserId(task.getUser().getUserId())
//////                .assignedToUserName(task.getUser().getName())
//////                .assignedToUserPhoto(task.getUser().getPhotoUrl())
//////                .taskDetails(task.getTaskDetails())
//////                .description(task.getDescription())
//////                .taskStatus(task.getTaskStatus())
//////                .createdById(task.getCreatedBy().getUserId())
//////                .createdByName(task.getCreatedBy().getName())
//////                .deadline(task.getDeadline())
//////                .managerNote(task.getManagerNote())
//////                .createdAt(task.getCreatedAt())
//////                .updatedAt(task.getUpdatedAt())
//////                .build();
//////    }
//////}