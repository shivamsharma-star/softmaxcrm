package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.UpdateTaskStatusRequest;
import com.softmax.shardaos.dto.output.EmployeeTaskResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeTaskService {

    @Autowired
    private EmpTaskRepository empTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private boolean isEmployee(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP").isPresent();
    }

    private void validateEmployee(Long userId) {
        if (!isEmployee(userId)) {
            throw new RuntimeException("Access denied: Only employees can access this resource");
        }
    }

    public List<EmployeeTaskResponseDTO> getMyTasks(Long userId) {
        validateEmployee(userId);
        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(userId);
        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public EmployeeTaskResponseDTO getTaskById(Long userId, Long taskId) {
        validateEmployee(userId);

        EmpTask task = empTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getAssignedTo().getUserId().equals(userId)) {
            throw new RuntimeException("You don't have access to this task");
        }

        return convertToDTO(task);
    }

    @Transactional
    public EmployeeTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
        validateEmployee(userId);

        EmpTask task = empTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getAssignedTo().getUserId().equals(userId)) {
            throw new RuntimeException("You can only update your own tasks");
        }

        String oldStatus = task.getStatus();
        task.setStatus(request.getStatus());

        if (request.getNote() != null && !request.getNote().isEmpty()) {
            String newDesc = task.getDescription() + "\n📝 Note: " + request.getNote();
            task.setDescription(newDesc);
        }

        if ("COMPLETED".equals(request.getStatus())) {
            task.setUpdatedAt(LocalDateTime.now());
        }

        EmpTask updatedTask = empTaskRepository.save(task);

        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus());

        return convertToDTO(updatedTask);
    }

    @Transactional
    public EmployeeTaskResponseDTO addNote(Long userId, Long taskId, String note) {
        validateEmployee(userId);

        EmpTask task = empTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getAssignedTo().getUserId().equals(userId)) {
            throw new RuntimeException("You can only add notes to your own tasks");
        }

        String newDesc = task.getDescription() + "\n\n📝 Employee Note: " + note + " (" + LocalDateTime.now().format(formatter) + ")";
        task.setDescription(newDesc);

        EmpTask updatedTask = empTaskRepository.save(task);

        sendTaskNoteEmail(task, note);

        return convertToDTO(updatedTask);
    }

    private EmployeeTaskResponseDTO convertToDTO(EmpTask task) {
        User creator = task.getCreator();

        return EmployeeTaskResponseDTO.builder()
                .taskId(task.getEmpTaskId())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(getPriority(task.getDescription()))
                .createdByUserId(creator.getUserId())
                .createdByName(creator.getName())
                .createdByPhoto(creator.getPhotoUrl())
                .deadline(task.getDeadline())
                .note(null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt("COMPLETED".equals(task.getStatus()) ? task.getUpdatedAt() : null)
                .build();
    }

    private String getPriority(String description) {
        if (description == null) return "MEDIUM";
        String lowerDesc = description.toLowerCase();
        if (lowerDesc.contains("urgent") || lowerDesc.contains("asap") || lowerDesc.contains("critical")) {
            return "HIGH";
        }
        if (lowerDesc.contains("low") || lowerDesc.contains("when free")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus) {
        try {
            User creator = task.getCreator();
            User employee = task.getAssignedTo();

            String desc = task.getDescription();
            String shortDesc = desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;

            // ✅ Status ke hisaab se icon & color
            String statusIcon;
            String statusColor;
            switch (newStatus != null ? newStatus.toUpperCase() : "") {
                case "COMPLETED": statusIcon = "✅"; statusColor = "#11998e"; break;
                case "IN_PROGRESS": statusIcon = "🔄"; statusColor = "#2196F3"; break;
                case "PENDING": statusIcon = "⏳"; statusColor = "#FF9800"; break;
                default: statusIcon = "🔄"; statusColor = "#667eea"; break;
            }

            String subject = statusIcon + " Task Status Update: " + newStatus + " - Softmax Technologies and IT Services";

            // ✅ Note mein links clickable
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");

            String html = String.format("""
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
                        .status-badge { display: inline-block; background: %s20; color: %s; padding: 8px 18px; border-radius: 30px; font-weight: 700; font-size: 14px; }
                        .arrow { color: #888; margin: 0 8px; font-size: 16px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>%s Task Status Updated</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Employee <strong>%s</strong> has updated task status.</p>
                            <div class="info-box">
                                <p><strong>📝 Task:</strong></p>
                                <p>%s</p>
                                <p style="margin-top:14px;"><strong>Status Changed:</strong></p>
                                <p>
                                    <span class="status-badge" style="background:#e9ecef;color:#6c757d;">%s</span>
                                    <span class="arrow">→</span>
                                    <span class="status-badge">%s %s</span>
                                </p>
                            </div>
                            <p style="color:#4a5568;">Please login to your dashboard for more details.</p>
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
                    statusColor, statusColor, statusColor, statusColor,
                    statusIcon,
                    creator.getName(),
                    employee.getName(),
                    safeDesc,
                    oldStatus,
                    statusIcon, newStatus
            );
            emailService.sendEmail(creator.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendTaskNoteEmail(EmpTask task, String note) {
        try {
            User creator = task.getCreator();
            User employee = task.getAssignedTo();

            String desc = task.getDescription();
            String shortDesc = desc.length() > 40 ? desc.substring(0, 40) + "..." : desc;

            String subject = "📝 New Note Added to Task - Softmax Technologies and IT Services";

            // ✅ Task description aur note dono mein links clickable
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");
            String safeNote = EmailService.makeLinksClickable(note != null ? note : "");

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #f6c23e 0%, #d97706 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #fff9e6; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #f6c23e; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .note-box { background: #fff; padding: 18px 22px; border-radius: 10px; margin-top: 15px; border: 1px solid #f6c23e; }
                        .note-box p { margin: 6px 0; color: #5c4a00; font-size: 14px; line-height: 1.6; }
                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#f6c23e 0%,#d97706 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(246,194,62,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>📝 New Note Added</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Employee <strong>%s</strong> added a note to task.</p>
                            <div class="info-box">
                                <p><strong>📝 Task:</strong></p>
                                <p>%s</p>
                                <div class="note-box">
                                    <p><strong>💬 Note:</strong></p>
                                    <p>%s</p>
                                </div>
                            </div>
                            <p style="color:#4a5568;">Please login to review and respond.</p>
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
                    creator.getName(),
                    employee.getName(),
                    safeDesc,
                    safeNote
            );
            emailService.sendEmail(creator.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }
}











//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.UpdateTaskStatusRequest;
//import com.softmax.shardaos.dto.output.EmployeeTaskResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class EmployeeTaskService {
//
//    @Autowired
//    private EmpTaskRepository empTaskRepository;
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
//    private boolean isEmployee(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP").isPresent();
//    }
//
//    private void validateEmployee(Long userId) {
//        if (!isEmployee(userId)) {
//            throw new RuntimeException("Access denied: Only employees can access this resource");
//        }
//    }
//
//    public List<EmployeeTaskResponseDTO> getMyTasks(Long userId) {
//        validateEmployee(userId);
//        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(userId);
//        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
//    }
//
//    public EmployeeTaskResponseDTO getTaskById(Long userId, Long taskId) {
//        validateEmployee(userId);
//
//        EmpTask task = empTaskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getAssignedTo().getUserId().equals(userId)) {
//            throw new RuntimeException("You don't have access to this task");
//        }
//
//        return convertToDTO(task);
//    }
//
//    @Transactional
//    public EmployeeTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
//        validateEmployee(userId);
//
//        EmpTask task = empTaskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getAssignedTo().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only update your own tasks");
//        }
//
//        String oldStatus = task.getStatus();
//        task.setStatus(request.getStatus());
//
//        if (request.getNote() != null && !request.getNote().isEmpty()) {
//            String newDesc = task.getDescription() + "\n📝 Note: " + request.getNote();
//            task.setDescription(newDesc);
//        }
//
//        if ("COMPLETED".equals(request.getStatus())) {
//            task.setUpdatedAt(LocalDateTime.now());
//        }
//
//        EmpTask updatedTask = empTaskRepository.save(task);
//
//        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus());
//
//        return convertToDTO(updatedTask);
//    }
//
//    @Transactional
//    public EmployeeTaskResponseDTO addNote(Long userId, Long taskId, String note) {
//        validateEmployee(userId);
//
//        EmpTask task = empTaskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!task.getAssignedTo().getUserId().equals(userId)) {
//            throw new RuntimeException("You can only add notes to your own tasks");
//        }
//
//        String newDesc = task.getDescription() + "\n\n📝 Employee Note: " + note + " (" + LocalDateTime.now().format(formatter) + ")";
//        task.setDescription(newDesc);
//
//        EmpTask updatedTask = empTaskRepository.save(task);
//
//        sendTaskNoteEmail(task, note);
//
//        return convertToDTO(updatedTask);
//    }
//
//    private EmployeeTaskResponseDTO convertToDTO(EmpTask task) {
//        User creator = task.getCreator();
//
//        return EmployeeTaskResponseDTO.builder()
//                .taskId(task.getEmpTaskId())
//                .description(task.getDescription())
//                .status(task.getStatus())
//                .priority(getPriority(task.getDescription()))
//                .createdByUserId(creator.getUserId())
//                .createdByName(creator.getName())
//                .createdByPhoto(creator.getPhotoUrl())
//                .deadline(task.getDeadline())
//                .note(null)
//                .createdAt(task.getCreatedAt())
//                .updatedAt(task.getUpdatedAt())
//                .completedAt("COMPLETED".equals(task.getStatus()) ? task.getUpdatedAt() : null)
//                .build();
//    }
//
//    private String getPriority(String description) {
//        if (description == null) return "MEDIUM";
//        String lowerDesc = description.toLowerCase();
//        if (lowerDesc.contains("urgent") || lowerDesc.contains("asap") || lowerDesc.contains("critical")) {
//            return "HIGH";
//        }
//        if (lowerDesc.contains("low") || lowerDesc.contains("when free")) {
//            return "LOW";
//        }
//        return "MEDIUM";
//    }
//
//    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus) {
//        try {
//            User creator = task.getCreator();
//            User employee = task.getAssignedTo();
//
//            String desc = task.getDescription();
//            String shortDesc = desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;
//
//            String subject = "🔄 Task Status Update - " + shortDesc;
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f0f7ff; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #3b82f6;'>🔄 Task Status Updated</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Employee <strong>%s</strong> has updated task status.</p>
//                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📝 Task:</strong> %s</p>
//                            <p><strong>Status Changed:</strong> %s → <strong style='color: #10b981;'>%s</strong></p>
//                        </div>
//                        <p>Please login to your dashboard for more details.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Employee Task Management</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    creator.getName(),
//                    employee.getName(),
//                    task.getDescription(),
//                    oldStatus,
//                    newStatus
//            );
//            emailService.sendEmail(creator.getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendTaskNoteEmail(EmpTask task, String note) {
//        try {
//            User creator = task.getCreator();
//            User employee = task.getAssignedTo();
//
//            String desc = task.getDescription();
//            String shortDesc = desc.length() > 40 ? desc.substring(0, 40) + "..." : desc;
//
//            String subject = "📝 New Note Added to Task - " + shortDesc;
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #fef3c7; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #d97706;'>📝 New Note Added</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Employee <strong>%s</strong> added a note to task.</p>
//                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📝 Task:</strong> %s</p>
//                            <p><strong>💬 Note:</strong> %s</p>
//                        </div>
//                        <p>Please login to review and respond.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Employee Task Management</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    creator.getName(),
//                    employee.getName(),
//                    task.getDescription(),
//                    note
//            );
//            emailService.sendEmail(creator.getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//}