package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ProjectAssignRequest;
import com.softmax.shardaos.dto.input.ProjectCreateRequest;
import com.softmax.shardaos.dto.input.ProjectUpdateRequest;
import com.softmax.shardaos.dto.output.ProjectResponseDTO;
import com.softmax.shardaos.dto.output.ProjectStatusDTO;
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
public class ProjectManagementService {

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private ProjectStatusRepository projectStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private ConsultantReviewRepository consultantReviewRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ==================== CLIENT APIs ====================

    @Transactional
    public ProjectResponseDTO createProject(Long clientUserId, ProjectCreateRequest request) {
        User client = userRepository.findById(clientUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserType> userTypes = userTypeRepository.findByUserUserId(clientUserId);
        boolean isClient = userTypes.stream().anyMatch(ut -> "CLIENT".equals(ut.getPtype()));

        if (!isClient) {
            throw new RuntimeException("Only clients can create projects");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        ProjectDetails project = new ProjectDetails();
        project.setProjectName(request.getProjectName());
        project.setProjectDescription(request.getProjectDescription());
        project.setProjectType(request.getProjectType());
        project.setDepartment(department);
        project.setUploader(client);
        project.setStatus("PENDING");
        project.setCost(request.getCost());
        project.setDeadline(null);

        project = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus("PENDING");
        statusRecord.setDateTime(LocalDateTime.now());
        projectStatusRepository.save(statusRecord);

        sendNotificationToDirector(project);
        sendProjectCreatedConfirmationToClient(project);
        return convertToDTO(project);
    }

    @Transactional
    public ProjectResponseDTO updateProject(Long clientUserId, Long projectId, ProjectUpdateRequest request) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUploader().getUserId().equals(clientUserId)) {
            throw new RuntimeException("You can only update your own projects");
        }

        if (request.getProjectName() != null) project.setProjectName(request.getProjectName());
        if (request.getProjectDescription() != null) project.setProjectDescription(request.getProjectDescription());
        if (request.getProjectType() != null) project.setProjectType(request.getProjectType());
        if (request.getCost() != null) project.setCost(request.getCost());

        project = projectRepository.save(project);
        return convertToDTO(project);
    }

    @Transactional
    public void deleteProject(Long clientUserId, Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUploader().getUserId().equals(clientUserId)) {
            throw new RuntimeException("You can only delete your own projects");
        }

        List<ProjectStatus> statusRecords = projectStatusRepository.findByProjectProjectId(projectId);
        projectStatusRepository.deleteAll(statusRecords);
        projectRepository.delete(project);
    }

    public List<ProjectResponseDTO> getMyProjects(Long clientUserId) {
        return projectRepository.findByUploaderUserId(clientUserId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ProjectResponseDTO getProjectById(Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return convertToDTO(project);
    }

    // ==================== DIRECTOR APIs ====================

    public boolean isDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        return isEmp && isDirector;
    }

    public Long getDirectorDepartmentId(Long directorUserId) {
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(directorUserId)
                .orElseThrow(() -> new RuntimeException("Employee details not found"));

        if (employee.getDepartmentRef() == null) {
            throw new RuntimeException("Director not assigned to any department");
        }
        return employee.getDepartmentRef().getDepartmentId();
    }

    public List<ProjectResponseDTO> getDepartmentProjects(Long directorUserId) {
        if (!isDirector(directorUserId)) {
            throw new RuntimeException("Access denied: Only directors can view department projects");
        }
        Long departmentId = getDirectorDepartmentId(directorUserId);
        return projectRepository.findByDepartmentDepartmentId(departmentId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> getPendingProjectsForDirector(Long directorUserId) {
        if (!isDirector(directorUserId)) {
            throw new RuntimeException("Access denied: Only directors can view pending projects");
        }
        Long departmentId = getDirectorDepartmentId(directorUserId);
        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponseDTO assignProjectToManager(Long directorUserId, Long projectId, ProjectAssignRequest request) {
        if (!isDirector(directorUserId)) {
            throw new RuntimeException("Access denied: Only directors can assign projects");
        }

        Long directorDeptId = getDirectorDepartmentId(directorUserId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(directorDeptId)) {
            throw new RuntimeException("This project does not belong to your department");
        }

        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        if (!isManagerInDepartment(manager.getUserId(), directorDeptId)) {
            throw new RuntimeException("Manager must belong to the same department");
        }

        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());

        project.setAssignedManager(manager);
        project.setStatus("ASSIGNED");
        project.setDeadline(request.getDeadline());
        project = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus("ASSIGNED");
        statusRecord.setDateTime(LocalDateTime.now());
        statusRecord.setHandledBy(userRepository.findById(directorUserId).get());
        statusRecord.setAssignedTo(manager);
        statusRecord.setDirectorNote(request.getDirectorNote());
        statusRecord.setDeadline(request.getDeadline());
        projectStatusRepository.save(statusRecord);

        sendAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
        sendProjectAssignedToClientEmail(project, manager);

        return convertToDTO(project);
    }

    private boolean isManagerInDepartment(Long userId, Long departmentId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));

        if (!isEmp || !isManager) return false;

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return employee != null && employee.getDepartmentRef() != null &&
                employee.getDepartmentRef().getDepartmentId().equals(departmentId);
    }

    // ==================== MANAGER APIs ====================

    public boolean isManager(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
        return isEmp && isManager;
    }

    public List<ProjectResponseDTO> getMyAssignedProjects(Long managerUserId) {
        if (!isManager(managerUserId)) {
            throw new RuntimeException("Access denied: Only managers can view assigned projects");
        }
        return projectRepository.findByAssignedManagerUserId(managerUserId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponseDTO updateProjectStatus(Long managerUserId, Long projectId, String status, String note) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getAssignedManager() == null ||
                !project.getAssignedManager().getUserId().equals(managerUserId)) {
            throw new RuntimeException("You are not assigned to this project");
        }

        String oldStatus = project.getStatus();
        project.setStatus(status);
        project = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus(status);
        statusRecord.setDateTime(LocalDateTime.now());
        statusRecord.setAssignedTo(userRepository.findById(managerUserId).get());
        statusRecord.setManagerNote(note);
        projectStatusRepository.save(statusRecord);

        sendStatusUpdateEmail(project, oldStatus, status, note);
        sendStatusUpdateToClientEmail(project, status, note);

        return convertToDTO(project);
    }

    // ==================== STATUS HISTORY API ====================

    public List<ProjectStatusDTO> getProjectStatusHistory(Long projectId, Long currentUserId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean hasAccess = false;

        if (project.getUploader().getUserId().equals(currentUserId)) {
            hasAccess = true;
        }

        if (isDirector(currentUserId)) {
            Long directorDeptId = getDirectorDepartmentId(currentUserId);
            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
                hasAccess = true;
            }
        }

        if (isManager(currentUserId) && project.getAssignedManager() != null) {
            if (project.getAssignedManager().getUserId().equals(currentUserId)) {
                hasAccess = true;
            }
        }

        if (!hasAccess) {
            throw new RuntimeException("You don't have access to view this project's history");
        }

        return projectStatusRepository.findByProjectProjectId(projectId)
                .stream()
                .map(this::convertToStatusDTO)
                .collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    private ProjectResponseDTO convertToDTO(ProjectDetails project) {
        return ProjectResponseDTO.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .projectDescription(project.getProjectDescription())
                .projectType(project.getProjectType())
                .status(project.getStatus())
                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
                .clientId(project.getUploader().getUserId())
                .clientName(project.getUploader().getName())
                .clientEmail(project.getUploader().getEmail())
                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
                .deadline(project.getDeadline())
                .cost(project.getCost())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ProjectStatusDTO convertToStatusDTO(ProjectStatus status) {
        return ProjectStatusDTO.builder()
                .statusId(status.getStatusId())
                .projectId(status.getProject().getProjectId())
                .projectName(status.getProject().getProjectName())
                .status(status.getStatus())
                .dateTime(status.getDateTime())
                .handledById(status.getHandledBy() != null ? status.getHandledBy().getUserId() : null)
                .handledByName(status.getHandledBy() != null ? status.getHandledBy().getName() : null)
                .assignedToId(status.getAssignedTo() != null ? status.getAssignedTo().getUserId() : null)
                .assignedToName(status.getAssignedTo() != null ? status.getAssignedTo().getName() : null)
                .directorNote(status.getDirectorNote())
                .managerNote(status.getManagerNote())
                .deadline(status.getDeadline())
                .build();
    }

    // ==================== EMAIL METHODS ====================

    /**
     * NEW: Client ko project create confirmation
     * Sirf clients.softmax.in links
     */
    private void sendProjectCreatedConfirmationToClient(ProjectDetails project) {
        try {
            String subject = "✅ Project Submitted Successfully - " + project.getProjectName() + " - Softmax Technologies and IT Services";

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #667eea; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>✅ Project Submitted</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your project has been submitted successfully and is now under review by our team.</p>
                            <div class="info-box">
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>📝 <strong>Description:</strong> %s</p>
                                <p>📱 <strong>Type:</strong> %s</p>
                                <p>🏢 <strong>Department:</strong> %s</p>
                                <p>💰 <strong>Estimated Cost:</strong> %s</p>
                                <p>📅 <strong>Submitted:</strong> %s</p>
                                <p>📊 <strong>Status:</strong> PENDING (awaiting director review)</p>
                            </div>
                            <p style="color:#4a5568;">You will receive another email once your project is reviewed and assigned to a manager.</p>
                            <div style="text-align:center;">
                                <a href="https://clients.softmax.in/dashboard" class="btn">🚀 Track Your Project</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    project.getUploader().getName(),
                    project.getProjectName(),
                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
                    project.getProjectType() != null ? project.getProjectType() : "Not specified",
                    project.getDepartment().getDepartmentName(),
                    project.getCost() != null ? project.getCost() : "Not specified",
                    project.getCreatedAt().format(formatter)
            );
            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error (client create confirmation): " + e.getMessage());
        }
    }

    private void sendNotificationToDirector(ProjectDetails project) {
        try {
            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
            User director = employees.stream()
                    .filter(e -> e.getDepartmentRef() != null &&
                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
                    .map(EmployeeDetails::getUser)
                    .filter(user -> {
                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
                    })
                    .findFirst()
                    .orElse(null);

            if (director != null) {
                String subject = "📋 New Project Created - Action Required - Softmax Technologies and IT Services";

                String html = String.format("""
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
                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                            .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                            .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>📋 New Project Created</h1>
                                <p>Softmax Technologies and IT Services</p>
                            </div>
                            <div class="content">
                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                                <p style="color:#4a5568;font-size:15px;">A new project has been created and requires your approval.</p>
                                <div class="info-box">
                                    <p>📁 <strong>Project:</strong> %s</p>
                                    <p>👤 <strong>Client:</strong> %s</p>
                                    <p>🏢 <strong>Department:</strong> %s</p>
                                    <p>💰 <strong>Estimated Cost:</strong> %s</p>
                                    <p>📅 <strong>Created:</strong> %s</p>
                                </div>
                                <p style="color:#4a5568;">Please login to review and assign this project.</p>
                                <div style="text-align:center;">
                                    <a href="https://nexus.softmax.in" class="btn">🚀 Review Project</a>
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
                        director.getName(),
                        project.getProjectName(),
                        project.getUploader().getName(),
                        project.getDepartment().getDepartmentName(),
                        project.getCost() != null ? project.getCost() : "Not specified",
                        project.getCreatedAt().format(formatter)
                );
                emailService.sendEmail(director.getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
        try {
            String subject = "📋 New Project Assigned to You - Softmax Technologies and IT Services";
            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e8f4f8; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2193b0; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #2193b0; }
                        .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(33,147,176,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #2193b0; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>📋 New Project Assigned</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">A new project has been assigned to you.</p>
                            <div class="info-box">
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>📝 <strong>Description:</strong> %s</p>
                                <p>💰 <strong>Budget:</strong> %s</p>
                                <p>📅 <strong>Deadline:</strong> %s</p>
                                <div class="note-box">
                                    <p><strong>📝 Director Note:</strong></p>
                                    <p>%s</p>
                                </div>
                            </div>
                            <p style="color:#4a5568;">Please login to create teams and assign tasks.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View Project</a>
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
                    manager.getName(),
                    project.getProjectName(),
                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
                    project.getCost() != null ? project.getCost() : "Not specified",
                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set",
                    safeNote
            );
            emailService.sendEmail(manager.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    /**
     * Client ko project assignment notification
     * ONLY clients.softmax.in — no nexus
     */
    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
        try {
            String subject = "✅ Project Assigned - Update - Softmax Technologies and IT Services";

            String html = String.format("""
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
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>✅ Project Update</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> has been assigned to a manager.</p>
                            <div class="info-box">
                                <p>👨‍💼 <strong>Assigned Manager:</strong> %s</p>
                                <p>📅 <strong>Deadline:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">You can track the project progress from your dashboard.</p>
                            <div style="text-align:center;">
                                <a href="https://clients.softmax.in/dashboard" class="btn">🚀 View Project</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    project.getUploader().getName(),
                    project.getProjectName(),
                    manager.getName(),
                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set"
            );
            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendStatusUpdateEmail(ProjectDetails project, String oldStatus, String newStatus, String note) {
        try {
            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
            User director = employees.stream()
                    .filter(e -> e.getDepartmentRef() != null &&
                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
                    .map(EmployeeDetails::getUser)
                    .filter(user -> {
                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
                    })
                    .findFirst()
                    .orElse(null);

            if (director != null) {
                String subject = "🔄 Project Status Updated - Softmax Technologies and IT Services";
                String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");

                String html = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                            .header { background: linear-gradient(135deg, #FF9800 0%, #FF5722 100%); padding: 40px 30px; text-align: center; }
                            .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                            .content { padding: 40px 35px; }
                            .info-box { background: #fff3e0; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #FF9800; }
                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                            .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #FF9800; }
                            .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                            .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                            .btn { display: inline-block; background: linear-gradient(135deg,#FF9800 0%,#FF5722 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(255,152,0,0.35); }
                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                            .footer a { color: #FF9800; text-decoration: none; font-weight: 600; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>🔄 Project Status Update</h1>
                                <p>Softmax Technologies and IT Services</p>
                            </div>
                            <div class="content">
                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                                <p style="color:#4a5568;font-size:15px;">Project <strong>%s</strong> status has been updated.</p>
                                <div class="info-box">
                                    <p>📊 <strong>Status Changed:</strong> %s → <strong style="color:#11998e;">%s</strong></p>
                                    <div class="note-box">
                                        <p><strong>📝 Manager Note:</strong></p>
                                        <p>%s</p>
                                    </div>
                                </div>
                                <div style="text-align:center;">
                                    <a href="https://nexus.softmax.in" class="btn">🚀 View Project</a>
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
                        director.getName(),
                        project.getProjectName(),
                        oldStatus,
                        newStatus,
                        safeNote
                );
                emailService.sendEmail(director.getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    /**
     * Client ko project status update notification
     * ONLY clients.softmax.in — no nexus
     */
    private void sendStatusUpdateToClientEmail(ProjectDetails project, String newStatus, String note) {
        try {
            String subject = "🔄 Project Status Update - " + project.getProjectName() + " - Softmax Technologies and IT Services";
            String statusIcon = newStatus.equals("COMPLETED") ? "✅" : newStatus.equals("IN_PROGRESS") ? "🔄" : "📋";
            String statusColor = newStatus.equals("COMPLETED") ? "#11998e" : newStatus.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, %s 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .status-badge { display: inline-block; background: %s20; color: %s; padding: 8px 20px; border-radius: 30px; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; }
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
                            <h1>%s Project Status Update</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> status has been updated.</p>
                            <div class="info-box">
                                <p><strong>📊 New Status:</strong></p>
                                <span class="status-badge">%s %s</span>
                                <div class="note-box">
                                    <p><strong>📝 Manager Note:</strong></p>
                                    <p>%s</p>
                                </div>
                            </div>
                            <p style="color:#4a5568;">Please login to your dashboard to view more details.</p>
                            <div style="text-align:center;">
                                <a href="https://clients.softmax.in/dashboard" class="btn">🚀 View Project</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    statusColor, statusColor, statusColor, statusColor,
                    statusIcon,
                    project.getUploader().getName(),
                    project.getProjectName(),
                    statusIcon, newStatus,
                    safeNote
            );
            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }
}








//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ProjectAssignRequest;
//import com.softmax.shardaos.dto.input.ProjectCreateRequest;
//import com.softmax.shardaos.dto.input.ProjectUpdateRequest;
//import com.softmax.shardaos.dto.output.ProjectResponseDTO;
//import com.softmax.shardaos.dto.output.ProjectStatusDTO;
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
//public class ProjectManagementService {
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
//
//    @Autowired
//    private ProjectStatusRepository projectStatusRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private DepartmentRepository departmentRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private ConsultantReviewRepository consultantReviewRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    // ==================== CLIENT APIs ====================
//
//    @Transactional
//    public ProjectResponseDTO createProject(Long clientUserId, ProjectCreateRequest request) {
//        User client = userRepository.findById(clientUserId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(clientUserId);
//        boolean isClient = userTypes.stream().anyMatch(ut -> "CLIENT".equals(ut.getPtype()));
//
//        if (!isClient) {
//            throw new RuntimeException("Only clients can create projects");
//        }
//
//        Department department = departmentRepository.findById(request.getDepartmentId())
//                .orElseThrow(() -> new RuntimeException("Department not found"));
//
//        ProjectDetails project = new ProjectDetails();
//        project.setProjectName(request.getProjectName());
//        project.setProjectDescription(request.getProjectDescription());
//        project.setProjectType(request.getProjectType());
//        project.setDepartment(department);
//        project.setUploader(client);
//        project.setStatus("PENDING");
//        project.setCost(request.getCost());
//        project.setDeadline(null);
//
//        project = projectRepository.save(project);
//
//        ProjectStatus statusRecord = new ProjectStatus();
//        statusRecord.setProject(project);
//        statusRecord.setStatus("PENDING");
//        statusRecord.setDateTime(LocalDateTime.now());
//        projectStatusRepository.save(statusRecord);
//
//        sendNotificationToDirector(project);
//        return convertToDTO(project);
//    }
//
//    @Transactional
//    public ProjectResponseDTO updateProject(Long clientUserId, Long projectId, ProjectUpdateRequest request) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (!project.getUploader().getUserId().equals(clientUserId)) {
//            throw new RuntimeException("You can only update your own projects");
//        }
//
//        if (request.getProjectName() != null) project.setProjectName(request.getProjectName());
//        if (request.getProjectDescription() != null) project.setProjectDescription(request.getProjectDescription());
//        if (request.getProjectType() != null) project.setProjectType(request.getProjectType());
//        if (request.getCost() != null) project.setCost(request.getCost());
//
//        project = projectRepository.save(project);
//        return convertToDTO(project);
//    }
//
//    @Transactional
//    public void deleteProject(Long clientUserId, Long projectId) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (!project.getUploader().getUserId().equals(clientUserId)) {
//            throw new RuntimeException("You can only delete your own projects");
//        }
//
//        List<ProjectStatus> statusRecords = projectStatusRepository.findByProjectProjectId(projectId);
//        projectStatusRepository.deleteAll(statusRecords);
//        projectRepository.delete(project);
//    }
//
//    public List<ProjectResponseDTO> getMyProjects(Long clientUserId) {
//        return projectRepository.findByUploaderUserId(clientUserId)
//                .stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    public ProjectResponseDTO getProjectById(Long projectId) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//        return convertToDTO(project);
//    }
//
//    // ==================== DIRECTOR APIs ====================
//
//    public boolean isDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//        return isEmp && isDirector;
//    }
//
//    public Long getDirectorDepartmentId(Long directorUserId) {
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(directorUserId)
//                .orElseThrow(() -> new RuntimeException("Employee details not found"));
//
//        if (employee.getDepartmentRef() == null) {
//            throw new RuntimeException("Director not assigned to any department");
//        }
//        return employee.getDepartmentRef().getDepartmentId();
//    }
//
//    public List<ProjectResponseDTO> getDepartmentProjects(Long directorUserId) {
//        if (!isDirector(directorUserId)) {
//            throw new RuntimeException("Access denied: Only directors can view department projects");
//        }
//        Long departmentId = getDirectorDepartmentId(directorUserId);
//        return projectRepository.findByDepartmentDepartmentId(departmentId)
//                .stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    public List<ProjectResponseDTO> getPendingProjectsForDirector(Long directorUserId) {
//        if (!isDirector(directorUserId)) {
//            throw new RuntimeException("Access denied: Only directors can view pending projects");
//        }
//        Long departmentId = getDirectorDepartmentId(directorUserId);
//        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
//                .stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    @Transactional
//    public ProjectResponseDTO assignProjectToManager(Long directorUserId, Long projectId, ProjectAssignRequest request) {
//        if (!isDirector(directorUserId)) {
//            throw new RuntimeException("Access denied: Only directors can assign projects");
//        }
//
//        Long directorDeptId = getDirectorDepartmentId(directorUserId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(directorDeptId)) {
//            throw new RuntimeException("This project does not belong to your department");
//        }
//
//        User manager = userRepository.findById(request.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Manager not found"));
//
//        if (!isManagerInDepartment(manager.getUserId(), directorDeptId)) {
//            throw new RuntimeException("Manager must belong to the same department");
//        }
//
//        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
//        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());
//
//        project.setAssignedManager(manager);
//        project.setStatus("ASSIGNED");
//        project.setDeadline(request.getDeadline());
//        project = projectRepository.save(project);
//
//        ProjectStatus statusRecord = new ProjectStatus();
//        statusRecord.setProject(project);
//        statusRecord.setStatus("ASSIGNED");
//        statusRecord.setDateTime(LocalDateTime.now());
//        statusRecord.setHandledBy(userRepository.findById(directorUserId).get());
//        statusRecord.setAssignedTo(manager);
//        statusRecord.setDirectorNote(request.getDirectorNote());
//        statusRecord.setDeadline(request.getDeadline());
//        projectStatusRepository.save(statusRecord);
//
//        sendAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
//        sendProjectAssignedToClientEmail(project, manager);
//
//        return convertToDTO(project);
//    }
//
//    private boolean isManagerInDepartment(Long userId, Long departmentId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
//
//        if (!isEmp || !isManager) return false;
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        return employee != null && employee.getDepartmentRef() != null &&
//                employee.getDepartmentRef().getDepartmentId().equals(departmentId);
//    }
//
//    // ==================== MANAGER APIs ====================
//
//    public boolean isManager(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
//        return isEmp && isManager;
//    }
//
//    public List<ProjectResponseDTO> getMyAssignedProjects(Long managerUserId) {
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Access denied: Only managers can view assigned projects");
//        }
//        return projectRepository.findByAssignedManagerUserId(managerUserId)
//                .stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    @Transactional
//    public ProjectResponseDTO updateProjectStatus(Long managerUserId, Long projectId, String status, String note) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getAssignedManager() == null ||
//                !project.getAssignedManager().getUserId().equals(managerUserId)) {
//            throw new RuntimeException("You are not assigned to this project");
//        }
//
//        String oldStatus = project.getStatus();
//        project.setStatus(status);
//        project = projectRepository.save(project);
//
//        ProjectStatus statusRecord = new ProjectStatus();
//        statusRecord.setProject(project);
//        statusRecord.setStatus(status);
//        statusRecord.setDateTime(LocalDateTime.now());
//        statusRecord.setAssignedTo(userRepository.findById(managerUserId).get());
//        statusRecord.setManagerNote(note);
//        projectStatusRepository.save(statusRecord);
//
//        sendStatusUpdateEmail(project, oldStatus, status, note);
//        sendStatusUpdateToClientEmail(project, status, note);
//
//        return convertToDTO(project);
//    }
//
//    // ==================== STATUS HISTORY API ====================
//
//    public List<ProjectStatusDTO> getProjectStatusHistory(Long projectId, Long currentUserId) {
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        boolean hasAccess = false;
//
//        if (project.getUploader().getUserId().equals(currentUserId)) {
//            hasAccess = true;
//        }
//
//        if (isDirector(currentUserId)) {
//            Long directorDeptId = getDirectorDepartmentId(currentUserId);
//            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
//                hasAccess = true;
//            }
//        }
//
//        if (isManager(currentUserId) && project.getAssignedManager() != null) {
//            if (project.getAssignedManager().getUserId().equals(currentUserId)) {
//                hasAccess = true;
//            }
//        }
//
//        if (!hasAccess) {
//            throw new RuntimeException("You don't have access to view this project's history");
//        }
//
//        return projectStatusRepository.findByProjectProjectId(projectId)
//                .stream()
//                .map(this::convertToStatusDTO)
//                .collect(Collectors.toList());
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private ProjectResponseDTO convertToDTO(ProjectDetails project) {
//        return ProjectResponseDTO.builder()
//                .projectId(project.getProjectId())
//                .projectName(project.getProjectName())
//                .projectDescription(project.getProjectDescription())
//                .projectType(project.getProjectType())
//                .status(project.getStatus())
//                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
//                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
//                .clientId(project.getUploader().getUserId())
//                .clientName(project.getUploader().getName())
//                .clientEmail(project.getUploader().getEmail())
//                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
//                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
//                .deadline(project.getDeadline())
//                .cost(project.getCost())
//                .createdAt(project.getCreatedAt())
//                .updatedAt(project.getUpdatedAt())
//                .build();
//    }
//
//    private ProjectStatusDTO convertToStatusDTO(ProjectStatus status) {
//        return ProjectStatusDTO.builder()
//                .statusId(status.getStatusId())
//                .projectId(status.getProject().getProjectId())
//                .projectName(status.getProject().getProjectName())
//                .status(status.getStatus())
//                .dateTime(status.getDateTime())
//                .handledById(status.getHandledBy() != null ? status.getHandledBy().getUserId() : null)
//                .handledByName(status.getHandledBy() != null ? status.getHandledBy().getName() : null)
//                .assignedToId(status.getAssignedTo() != null ? status.getAssignedTo().getUserId() : null)
//                .assignedToName(status.getAssignedTo() != null ? status.getAssignedTo().getName() : null)
//                .directorNote(status.getDirectorNote())
//                .managerNote(status.getManagerNote())
//                .deadline(status.getDeadline())
//                .build();
//    }
//
//    // ==================== EMAIL METHODS ====================
//
//    private void sendNotificationToDirector(ProjectDetails project) {
//        try {
//            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//            User director = employees.stream()
//                    .filter(e -> e.getDepartmentRef() != null &&
//                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
//                    .map(EmployeeDetails::getUser)
//                    .filter(user -> {
//                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
//                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//                    })
//                    .findFirst()
//                    .orElse(null);
//
//            if (director != null) {
//                String subject = "📋 New Project Created - Action Required - Softmax Technologies and IT Services";
//
//                String html = String.format("""
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta charset="UTF-8">
//                        <style>
//                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                            .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
//                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                            .content { padding: 40px 35px; }
//                            .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
//                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
//                            .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
//                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                            .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>📋 New Project Created</h1>
//                                <p>Softmax Technologies and IT Services</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                                <p style="color:#4a5568;font-size:15px;">A new project has been created and requires your approval.</p>
//                                <div class="info-box">
//                                    <p>📁 <strong>Project:</strong> %s</p>
//                                    <p>👤 <strong>Client:</strong> %s</p>
//                                    <p>🏢 <strong>Department:</strong> %s</p>
//                                    <p>💰 <strong>Estimated Cost:</strong> %s</p>
//                                    <p>📅 <strong>Created:</strong> %s</p>
//                                </div>
//                                <p style="color:#4a5568;">Please login to review and assign this project.</p>
//                                <div style="text-align:center;">
//                                    <a href="https://nexus.softmax.in" class="btn">🚀 Review Project</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        director.getName(),
//                        project.getProjectName(),
//                        project.getUploader().getName(),
//                        project.getDepartment().getDepartmentName(),
//                        project.getCost() != null ? project.getCost() : "Not specified",
//                        project.getCreatedAt().format(formatter)
//                );
//                emailService.sendEmail(director.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
//        try {
//            String subject = "📋 New Project Assigned to You - Softmax Technologies and IT Services";
//            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
//
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head>
//                    <meta charset="UTF-8">
//                    <style>
//                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                        .header { background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%); padding: 40px 30px; text-align: center; }
//                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
//                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                        .content { padding: 40px 35px; }
//                        .info-box { background: #e8f4f8; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2193b0; }
//                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                        .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #2193b0; }
//                        .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                        .btn { display: inline-block; background: linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(33,147,176,0.35); }
//                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                        .footer a { color: #2193b0; text-decoration: none; font-weight: 600; }
//                    </style>
//                </head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h1>📋 New Project Assigned</h1>
//                            <p>Softmax Technologies and IT Services</p>
//                        </div>
//                        <div class="content">
//                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                            <p style="color:#4a5568;font-size:15px;">A new project has been assigned to you.</p>
//                            <div class="info-box">
//                                <p>📁 <strong>Project:</strong> %s</p>
//                                <p>📝 <strong>Description:</strong> %s</p>
//                                <p>💰 <strong>Budget:</strong> %s</p>
//                                <p>📅 <strong>Deadline:</strong> %s</p>
//                                <div class="note-box">
//                                    <p><strong>📝 Director Note:</strong></p>
//                                    <p>%s</p>
//                                </div>
//                            </div>
//                            <p style="color:#4a5568;">Please login to create teams and assign tasks.</p>
//                            <div style="text-align:center;">
//                                <a href="https://nexus.softmax.in" class="btn">🚀 View Project</a>
//                            </div>
//                        </div>
//                        <div class="footer">
//                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    manager.getName(),
//                    project.getProjectName(),
//                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
//                    project.getCost() != null ? project.getCost() : "Not specified",
//                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set",
//                    safeNote
//            );
//            emailService.sendEmail(manager.getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
//        try {
//            String subject = "✅ Project Assigned - Update - Softmax Technologies and IT Services";
//
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head>
//                    <meta charset="UTF-8">
//                    <style>
//                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                        .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
//                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
//                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                        .content { padding: 40px 35px; }
//                        .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
//                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
//                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
//                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                        .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
//                    </style>
//                </head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h1>✅ Project Update</h1>
//                            <p>Softmax Technologies and IT Services</p>
//                        </div>
//                        <div class="content">
//                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> has been assigned to a manager.</p>
//                            <div class="info-box">
//                                <p>👨‍💼 <strong>Assigned Manager:</strong> %s</p>
//                                <p>📅 <strong>Deadline:</strong> %s</p>
//                            </div>
//                            <p style="color:#4a5568;">You can track the project progress from your dashboard.</p>
//                            <div style="text-align:center;">
//                                <a href="https://clients.softmax.in/dashboard" class="btn">🚀 View Project</a>
//                            </div>
//                        </div>
//                        <div class="footer">
//                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
//                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    project.getUploader().getName(),
//                    project.getProjectName(),
//                    manager.getName(),
//                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set"
//            );
//            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendStatusUpdateEmail(ProjectDetails project, String oldStatus, String newStatus, String note) {
//        try {
//            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//            User director = employees.stream()
//                    .filter(e -> e.getDepartmentRef() != null &&
//                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
//                    .map(EmployeeDetails::getUser)
//                    .filter(user -> {
//                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
//                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//                    })
//                    .findFirst()
//                    .orElse(null);
//
//            if (director != null) {
//                String subject = "🔄 Project Status Updated - Softmax Technologies and IT Services";
//                String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
//
//                String html = String.format("""
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta charset="UTF-8">
//                        <style>
//                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                            .header { background: linear-gradient(135deg, #FF9800 0%, #FF5722 100%); padding: 40px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
//                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                            .content { padding: 40px 35px; }
//                            .info-box { background: #fff3e0; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #FF9800; }
//                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                            .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #FF9800; }
//                            .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                            .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                            .btn { display: inline-block; background: linear-gradient(135deg,#FF9800 0%,#FF5722 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(255,152,0,0.35); }
//                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                            .footer a { color: #FF9800; text-decoration: none; font-weight: 600; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>🔄 Project Status Update</h1>
//                                <p>Softmax Technologies and IT Services</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                                <p style="color:#4a5568;font-size:15px;">Project <strong>%s</strong> status has been updated.</p>
//                                <div class="info-box">
//                                    <p>📊 <strong>Status Changed:</strong> %s → <strong style="color:#11998e;">%s</strong></p>
//                                    <div class="note-box">
//                                        <p><strong>📝 Manager Note:</strong></p>
//                                        <p>%s</p>
//                                    </div>
//                                </div>
//                                <div style="text-align:center;">
//                                    <a href="https://nexus.softmax.in" class="btn">🚀 View Project</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        director.getName(),
//                        project.getProjectName(),
//                        oldStatus,
//                        newStatus,
//                        safeNote
//                );
//                emailService.sendEmail(director.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendStatusUpdateToClientEmail(ProjectDetails project, String newStatus, String note) {
//        try {
//            String subject = "🔄 Project Status Update - " + project.getProjectName() + " - Softmax Technologies and IT Services";
//            String statusIcon = newStatus.equals("COMPLETED") ? "✅" : newStatus.equals("IN_PROGRESS") ? "🔄" : "📋";
//            String statusColor = newStatus.equals("COMPLETED") ? "#11998e" : newStatus.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
//            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
//
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html>
//                <head>
//                    <meta charset="UTF-8">
//                    <style>
//                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                        .header { background: linear-gradient(135deg, %s 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center; }
//                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
//                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                        .content { padding: 40px 35px; }
//                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
//                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                        .status-badge { display: inline-block; background: %s20; color: %s; padding: 8px 20px; border-radius: 30px; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; }
//                        .note-box { background: #fff9e6; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border-left: 5px solid #f6c23e; }
//                        .note-box p { margin: 6px 0; color: #5c4a00; font-size: 14px; line-height: 1.6; }
//                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
//                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
//                    </style>
//                </head>
//                <body>
//                    <div class="container">
//                        <div class="header">
//                            <h1>%s Project Status Update</h1>
//                            <p>Softmax Technologies and IT Services</p>
//                        </div>
//                        <div class="content">
//                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> status has been updated.</p>
//                            <div class="info-box">
//                                <p><strong>📊 New Status:</strong></p>
//                                <span class="status-badge">%s %s</span>
//                                <div class="note-box">
//                                    <p><strong>📝 Manager Note:</strong></p>
//                                    <p>%s</p>
//                                </div>
//                            </div>
//                            <p style="color:#4a5568;">Please login to your dashboard to view more details.</p>
//                            <div style="text-align:center;">
//                                <a href="https://clients.softmax.in/dashboard" class="btn">🚀 View Project</a>
//                            </div>
//                        </div>
//                        <div class="footer">
//                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
//                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
//                        </div>
//                    </div>
//                </body>
//                </html>
//                """,
//                    statusColor, statusColor, statusColor, statusColor,
//                    statusIcon,
//                    project.getUploader().getName(),
//                    project.getProjectName(),
//                    statusIcon, newStatus,
//                    safeNote
//            );
//            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
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
//
//
//
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.ProjectAssignRequest;
////import com.softmax.shardaos.dto.input.ProjectCreateRequest;
////import com.softmax.shardaos.dto.input.ProjectUpdateRequest;
////import com.softmax.shardaos.dto.output.ProjectResponseDTO;
////import com.softmax.shardaos.dto.output.ProjectStatusDTO;
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
////public class ProjectManagementService {
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
////
////    @Autowired
////    private ProjectStatusRepository projectStatusRepository;
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private DepartmentRepository departmentRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmployeeDetailsRepository employeeDetailsRepository;
////
////    @Autowired
////    private ConsultantReviewRepository consultantReviewRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////
////    // ==================== CLIENT APIs ====================
////
////    @Transactional
////    public ProjectResponseDTO createProject(Long clientUserId, ProjectCreateRequest request) {
////        User client = userRepository.findById(clientUserId)
////                .orElseThrow(() -> new RuntimeException("User not found"));
////
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(clientUserId);
////        boolean isClient = userTypes.stream().anyMatch(ut -> "CLIENT".equals(ut.getPtype()));
////
////        if (!isClient) {
////            throw new RuntimeException("Only clients can create projects");
////        }
////
////        Department department = departmentRepository.findById(request.getDepartmentId())
////                .orElseThrow(() -> new RuntimeException("Department not found"));
////
////        ProjectDetails project = new ProjectDetails();
////        project.setProjectName(request.getProjectName());
////        project.setProjectDescription(request.getProjectDescription());
////        project.setProjectType(request.getProjectType());
////        project.setDepartment(department);
////        project.setUploader(client);
////        project.setStatus("PENDING");
////        project.setCost(request.getCost());
////        project.setDeadline(null);
////
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus("PENDING");
////        statusRecord.setDateTime(LocalDateTime.now());
////        projectStatusRepository.save(statusRecord);
////
////        sendNotificationToDirector(project);
////        return convertToDTO(project);
////    }
////
////    @Transactional
////    public ProjectResponseDTO updateProject(Long clientUserId, Long projectId, ProjectUpdateRequest request) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (!project.getUploader().getUserId().equals(clientUserId)) {
////            throw new RuntimeException("You can only update your own projects");
////        }
////
////        if (request.getProjectName() != null) project.setProjectName(request.getProjectName());
////        if (request.getProjectDescription() != null) project.setProjectDescription(request.getProjectDescription());
////        if (request.getProjectType() != null) project.setProjectType(request.getProjectType());
////        if (request.getCost() != null) project.setCost(request.getCost());
////
////        project = projectRepository.save(project);
////        return convertToDTO(project);
////    }
////
////    @Transactional
////    public void deleteProject(Long clientUserId, Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (!project.getUploader().getUserId().equals(clientUserId)) {
////            throw new RuntimeException("You can only delete your own projects");
////        }
////
////        List<ProjectStatus> statusRecords = projectStatusRepository.findByProjectProjectId(projectId);
////        projectStatusRepository.deleteAll(statusRecords);
////        projectRepository.delete(project);
////    }
////
////    public List<ProjectResponseDTO> getMyProjects(Long clientUserId) {
////        return projectRepository.findByUploaderUserId(clientUserId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    public ProjectResponseDTO getProjectById(Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////        return convertToDTO(project);
////    }
////
////    // ==================== DIRECTOR APIs ====================
////
////    public boolean isDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////        return isEmp && isDirector;
////    }
////
////    public Long getDirectorDepartmentId(Long directorUserId) {
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(directorUserId)
////                .orElseThrow(() -> new RuntimeException("Employee details not found"));
////
////        if (employee.getDepartmentRef() == null) {
////            throw new RuntimeException("Director not assigned to any department");
////        }
////        return employee.getDepartmentRef().getDepartmentId();
////    }
////
////    public List<ProjectResponseDTO> getDepartmentProjects(Long directorUserId) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can view department projects");
////        }
////        Long departmentId = getDirectorDepartmentId(directorUserId);
////        return projectRepository.findByDepartmentDepartmentId(departmentId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    public List<ProjectResponseDTO> getPendingProjectsForDirector(Long directorUserId) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can view pending projects");
////        }
////        Long departmentId = getDirectorDepartmentId(directorUserId);
////        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ProjectResponseDTO assignProjectToManager(Long directorUserId, Long projectId, ProjectAssignRequest request) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can assign projects");
////        }
////
////        Long directorDeptId = getDirectorDepartmentId(directorUserId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(directorDeptId)) {
////            throw new RuntimeException("This project does not belong to your department");
////        }
////
////        User manager = userRepository.findById(request.getManagerId())
////                .orElseThrow(() -> new RuntimeException("Manager not found"));
////
////        if (!isManagerInDepartment(manager.getUserId(), directorDeptId)) {
////            throw new RuntimeException("Manager must belong to the same department");
////        }
////
////        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
////        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());
////
////        project.setAssignedManager(manager);
////        project.setStatus("ASSIGNED");
////        project.setDeadline(request.getDeadline());
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus("ASSIGNED");
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setHandledBy(userRepository.findById(directorUserId).get());
////        statusRecord.setAssignedTo(manager);
////        statusRecord.setDirectorNote(request.getDirectorNote());
////        statusRecord.setDeadline(request.getDeadline());
////        projectStatusRepository.save(statusRecord);
////
////        sendAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
////        sendProjectAssignedToClientEmail(project, manager);
////
////        return convertToDTO(project);
////    }
////
////    private boolean isManagerInDepartment(Long userId, Long departmentId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
////
////        if (!isEmp || !isManager) return false;
////
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
////        return employee != null && employee.getDepartmentRef() != null &&
////                employee.getDepartmentRef().getDepartmentId().equals(departmentId);
////    }
////
////    // ==================== MANAGER APIs ====================
////
////    public boolean isManager(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
////        return isEmp && isManager;
////    }
////
////    public List<ProjectResponseDTO> getMyAssignedProjects(Long managerUserId) {
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Access denied: Only managers can view assigned projects");
////        }
////        return projectRepository.findByAssignedManagerUserId(managerUserId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ProjectResponseDTO updateProjectStatus(Long managerUserId, Long projectId, String status, String note) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getAssignedManager() == null ||
////                !project.getAssignedManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You are not assigned to this project");
////        }
////
////        String oldStatus = project.getStatus();
////        project.setStatus(status);
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus(status);
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setAssignedTo(userRepository.findById(managerUserId).get());
////        statusRecord.setManagerNote(note);
////        projectStatusRepository.save(statusRecord);
////
////        sendStatusUpdateEmail(project, oldStatus, status, note);
////        sendStatusUpdateToClientEmail(project, status, note);
////
////        return convertToDTO(project);
////    }
////
////    // ==================== STATUS HISTORY API ====================
////
////    public List<ProjectStatusDTO> getProjectStatusHistory(Long projectId, Long currentUserId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        boolean hasAccess = false;
////
////        if (project.getUploader().getUserId().equals(currentUserId)) {
////            hasAccess = true;
////        }
////
////        if (isDirector(currentUserId)) {
////            Long directorDeptId = getDirectorDepartmentId(currentUserId);
////            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
////                hasAccess = true;
////            }
////        }
////
////        if (isManager(currentUserId) && project.getAssignedManager() != null) {
////            if (project.getAssignedManager().getUserId().equals(currentUserId)) {
////                hasAccess = true;
////            }
////        }
////
////        if (!hasAccess) {
////            throw new RuntimeException("You don't have access to view this project's history");
////        }
////
////        return projectStatusRepository.findByProjectProjectId(projectId)
////                .stream()
////                .map(this::convertToStatusDTO)
////                .collect(Collectors.toList());
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    private ProjectResponseDTO convertToDTO(ProjectDetails project) {
////        return ProjectResponseDTO.builder()
////                .projectId(project.getProjectId())
////                .projectName(project.getProjectName())
////                .projectDescription(project.getProjectDescription())
////                .projectType(project.getProjectType())
////                .status(project.getStatus())
////                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
////                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
////                .clientId(project.getUploader().getUserId())
////                .clientName(project.getUploader().getName())
////                .clientEmail(project.getUploader().getEmail())
////                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
////                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
////                .deadline(project.getDeadline())
////                .cost(project.getCost())
////                .createdAt(project.getCreatedAt())
////                .updatedAt(project.getUpdatedAt())
////                .build();
////    }
////
////    private ProjectStatusDTO convertToStatusDTO(ProjectStatus status) {
////        return ProjectStatusDTO.builder()
////                .statusId(status.getStatusId())
////                .projectId(status.getProject().getProjectId())
////                .projectName(status.getProject().getProjectName())
////                .status(status.getStatus())
////                .dateTime(status.getDateTime())
////                .handledById(status.getHandledBy() != null ? status.getHandledBy().getUserId() : null)
////                .handledByName(status.getHandledBy() != null ? status.getHandledBy().getName() : null)
////                .assignedToId(status.getAssignedTo() != null ? status.getAssignedTo().getUserId() : null)
////                .assignedToName(status.getAssignedTo() != null ? status.getAssignedTo().getName() : null)
////                .directorNote(status.getDirectorNote())
////                .managerNote(status.getManagerNote())
////                .deadline(status.getDeadline())
////                .build();
////    }
////
////    // ==================== IMPROVED EMAIL METHODS ====================
////
////    private void sendNotificationToDirector(ProjectDetails project) {
////        try {
////            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
////            User director = employees.stream()
////                    .filter(e -> e.getDepartmentRef() != null &&
////                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
////                    .map(EmployeeDetails::getUser)
////                    .filter(user -> {
////                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
////                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////                    })
////                    .findFirst()
////                    .orElse(null);
////
////            if (director != null) {
////                String subject = "📋 New Project Created - Action Required - Softmax Technologies and IT Services";
////
////                String html = String.format("""
////                    <!DOCTYPE html>
////                    <html>
////                    <head>
////                        <meta charset="UTF-8">
////                        <style>
////                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
////                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
////                            .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
////                            .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
////                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
////                            .content { padding: 40px 35px; }
////                            .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
////                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
////                            .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
////                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
////                            .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
////                        </style>
////                    </head>
////                    <body>
////                        <div class="container">
////                            <div class="header">
////                                <h1>📋 New Project Created</h1>
////                                <p>Softmax Technologies and IT Services</p>
////                            </div>
////                            <div class="content">
////                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
////                                <p style="color:#4a5568;font-size:15px;">A new project has been created and requires your approval.</p>
////                                <div class="info-box">
////                                    <p>📁 <strong>Project:</strong> %s</p>
////                                    <p>👤 <strong>Client:</strong> %s</p>
////                                    <p>🏢 <strong>Department:</strong> %s</p>
////                                    <p>💰 <strong>Estimated Cost:</strong> %s</p>
////                                    <p>📅 <strong>Created:</strong> %s</p>
////                                </div>
////                                <p style="color:#4a5568;">Please login to review and assign this project.</p>
////                                <div style="text-align:center;">
////                                    <a href="https://nexus.softmax.in/director/projects" class="btn">🚀 Review Project</a>
////                                </div>
////                            </div>
////                            <div class="footer">
////                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
////                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
////                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
////                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
////                            </div>
////                        </div>
////                    </body>
////                    </html>
////                    """,
////                        director.getName(),
////                        project.getProjectName(),
////                        project.getUploader().getName(),
////                        project.getDepartment().getDepartmentName(),
////                        project.getCost() != null ? project.getCost() : "Not specified",
////                        project.getCreatedAt().format(formatter)
////                );
////                emailService.sendEmail(director.getEmail(), subject, html);
////            }
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
////        try {
////            String subject = "📋 New Project Assigned to You - Softmax Technologies and IT Services";
////            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
////
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head>
////                    <meta charset="UTF-8">
////                    <style>
////                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
////                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
////                        .header { background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%); padding: 40px 30px; text-align: center; }
////                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
////                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
////                        .content { padding: 40px 35px; }
////                        .info-box { background: #e8f4f8; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2193b0; }
////                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
////                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                        .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #2193b0; }
////                        .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
////                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                        .btn { display: inline-block; background: linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(33,147,176,0.35); }
////                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
////                        .footer a { color: #2193b0; text-decoration: none; font-weight: 600; }
////                    </style>
////                </head>
////                <body>
////                    <div class="container">
////                        <div class="header">
////                            <h1>📋 New Project Assigned</h1>
////                            <p>Softmax Technologies and IT Services</p>
////                        </div>
////                        <div class="content">
////                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
////                            <p style="color:#4a5568;font-size:15px;">A new project has been assigned to you.</p>
////                            <div class="info-box">
////                                <p>📁 <strong>Project:</strong> %s</p>
////                                <p>📝 <strong>Description:</strong> %s</p>
////                                <p>💰 <strong>Budget:</strong> %s</p>
////                                <p>📅 <strong>Deadline:</strong> %s</p>
////                                <div class="note-box">
////                                    <p><strong>📝 Director Note:</strong></p>
////                                    <p>%s</p>
////                                </div>
////                            </div>
////                            <p style="color:#4a5568;">Please login to create teams and assign tasks.</p>
////                            <div style="text-align:center;">
////                                <a href="https://nexus.softmax.in/manager/projects" class="btn">🚀 View Project</a>
////                            </div>
////                        </div>
////                        <div class="footer">
////                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
////                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
////                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
////                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """,
////                    manager.getName(),
////                    project.getProjectName(),
////                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
////                    project.getCost() != null ? project.getCost() : "Not specified",
////                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set",
////                    safeNote
////            );
////            emailService.sendEmail(manager.getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
////        try {
////            String subject = "✅ Project Assigned - Update - Softmax Technologies and IT Services";
////
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head>
////                    <meta charset="UTF-8">
////                    <style>
////                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
////                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
////                        .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
////                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
////                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
////                        .content { padding: 40px 35px; }
////                        .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
////                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
////                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
////                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
////                        .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
////                    </style>
////                </head>
////                <body>
////                    <div class="container">
////                        <div class="header">
////                            <h1>✅ Project Update</h1>
////                            <p>Softmax Technologies and IT Services</p>
////                        </div>
////                        <div class="content">
////                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
////                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> has been assigned to a manager.</p>
////                            <div class="info-box">
////                                <p>👨‍💼 <strong>Assigned Manager:</strong> %s</p>
////                                <p>📅 <strong>Deadline:</strong> %s</p>
////                            </div>
////                            <p style="color:#4a5568;">You can track the project progress from your dashboard.</p>
////                            <div style="text-align:center;">
////                                <a href="https://nexus.softmax.in/client/dashboard" class="btn">🚀 View Project</a>
////                            </div>
////                        </div>
////                        <div class="footer">
////                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
////                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
////                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
////                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """,
////                    project.getUploader().getName(),
////                    project.getProjectName(),
////                    manager.getName(),
////                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set"
////            );
////            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendStatusUpdateEmail(ProjectDetails project, String oldStatus, String newStatus, String note) {
////        try {
////            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
////            User director = employees.stream()
////                    .filter(e -> e.getDepartmentRef() != null &&
////                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
////                    .map(EmployeeDetails::getUser)
////                    .filter(user -> {
////                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
////                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////                    })
////                    .findFirst()
////                    .orElse(null);
////
////            if (director != null) {
////                String subject = "🔄 Project Status Updated - Softmax Technologies and IT Services";
////                String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
////
////                String html = String.format("""
////                    <!DOCTYPE html>
////                    <html>
////                    <head>
////                        <meta charset="UTF-8">
////                        <style>
////                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
////                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
////                            .header { background: linear-gradient(135deg, #FF9800 0%, #FF5722 100%); padding: 40px 30px; text-align: center; }
////                            .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
////                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
////                            .content { padding: 40px 35px; }
////                            .info-box { background: #fff3e0; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #FF9800; }
////                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
////                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                            .note-box { background: #fff; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border: 1px solid #FF9800; }
////                            .note-box p { margin: 6px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
////                            .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                            .btn { display: inline-block; background: linear-gradient(135deg,#FF9800 0%,#FF5722 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(255,152,0,0.35); }
////                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
////                            .footer a { color: #FF9800; text-decoration: none; font-weight: 600; }
////                        </style>
////                    </head>
////                    <body>
////                        <div class="container">
////                            <div class="header">
////                                <h1>🔄 Project Status Update</h1>
////                                <p>Softmax Technologies and IT Services</p>
////                            </div>
////                            <div class="content">
////                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
////                                <p style="color:#4a5568;font-size:15px;">Project <strong>%s</strong> status has been updated.</p>
////                                <div class="info-box">
////                                    <p>📊 <strong>Status Changed:</strong> %s → <strong style="color:#11998e;">%s</strong></p>
////                                    <div class="note-box">
////                                        <p><strong>📝 Manager Note:</strong></p>
////                                        <p>%s</p>
////                                    </div>
////                                </div>
////                                <div style="text-align:center;">
////                                    <a href="https://nexus.softmax.in/director/projects" class="btn">🚀 View Project</a>
////                                </div>
////                            </div>
////                            <div class="footer">
////                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
////                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
////                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
////                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
////                            </div>
////                        </div>
////                    </body>
////                    </html>
////                    """,
////                        director.getName(),
////                        project.getProjectName(),
////                        oldStatus,
////                        newStatus,
////                        safeNote
////                );
////                emailService.sendEmail(director.getEmail(), subject, html);
////            }
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendStatusUpdateToClientEmail(ProjectDetails project, String newStatus, String note) {
////        try {
////            String subject = "🔄 Project Status Update - " + project.getProjectName() + " - Softmax Technologies and IT Services";
////            String statusIcon = newStatus.equals("COMPLETED") ? "✅" : newStatus.equals("IN_PROGRESS") ? "🔄" : "📋";
////            String statusColor = newStatus.equals("COMPLETED") ? "#11998e" : newStatus.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
////            String safeNote = EmailService.makeLinksClickable(note != null ? note : "No additional notes");
////
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head>
////                    <meta charset="UTF-8">
////                    <style>
////                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
////                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
////                        .header { background: linear-gradient(135deg, %s 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center; }
////                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
////                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
////                        .content { padding: 40px 35px; }
////                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
////                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
////                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                        .status-badge { display: inline-block; background: %s20; color: %s; padding: 8px 20px; border-radius: 30px; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; }
////                        .note-box { background: #fff9e6; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border-left: 5px solid #f6c23e; }
////                        .note-box p { margin: 6px 0; color: #5c4a00; font-size: 14px; line-height: 1.6; }
////                        .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
////                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
////                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
////                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
////                    </style>
////                </head>
////                <body>
////                    <div class="container">
////                        <div class="header">
////                            <h1>%s Project Status Update</h1>
////                            <p>Softmax Technologies and IT Services</p>
////                        </div>
////                        <div class="content">
////                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
////                            <p style="color:#4a5568;font-size:15px;">Your project <strong>%s</strong> status has been updated.</p>
////                            <div class="info-box">
////                                <p><strong>📊 New Status:</strong></p>
////                                <span class="status-badge">%s %s</span>
////                                <div class="note-box">
////                                    <p><strong>📝 Manager Note:</strong></p>
////                                    <p>%s</p>
////                                </div>
////                            </div>
////                            <p style="color:#4a5568;">Please login to your dashboard to view more details.</p>
////                            <div style="text-align:center;">
////                                <a href="https://nexus.softmax.in/client/dashboard" class="btn">🚀 View Project</a>
////                            </div>
////                        </div>
////                        <div class="footer">
////                            <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
////                            <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
////                            <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
////                            <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """,
////                    statusColor, statusColor, statusColor, statusColor,
////                    statusIcon,
////                    project.getUploader().getName(),
////                    project.getProjectName(),
////                    statusIcon, newStatus,
////                    safeNote
////            );
////            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////}
////
////
////
//
//
//
//
//
//
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.ProjectAssignRequest;
////import com.softmax.shardaos.dto.input.ProjectCreateRequest;
////import com.softmax.shardaos.dto.input.ProjectUpdateRequest;
////import com.softmax.shardaos.dto.output.ProjectResponseDTO;
////import com.softmax.shardaos.dto.output.ProjectStatusDTO;
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
////public class ProjectManagementService {
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
////
////    @Autowired
////    private ProjectStatusRepository projectStatusRepository;
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private DepartmentRepository departmentRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmployeeDetailsRepository employeeDetailsRepository;
////
////    @Autowired
////    private ConsultantReviewRepository consultantReviewRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////
////    // ==================== CLIENT APIs ====================
////
////    @Transactional
////    public ProjectResponseDTO createProject(Long clientUserId, ProjectCreateRequest request) {
////        User client = userRepository.findById(clientUserId)
////                .orElseThrow(() -> new RuntimeException("User not found"));
////
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(clientUserId);
////        boolean isClient = userTypes.stream().anyMatch(ut -> "CLIENT".equals(ut.getPtype()));
////
////        if (!isClient) {
////            throw new RuntimeException("Only clients can create projects");
////        }
////
////        Department department = departmentRepository.findById(request.getDepartmentId())
////                .orElseThrow(() -> new RuntimeException("Department not found"));
////
////        ProjectDetails project = new ProjectDetails();
////        project.setProjectName(request.getProjectName());
////        project.setProjectDescription(request.getProjectDescription());
////        project.setProjectType(request.getProjectType());
////        project.setDepartment(department);
////        project.setUploader(client);
////        project.setStatus("PENDING");
////        project.setCost(request.getCost());
////        project.setDeadline(null);
////
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus("PENDING");
////        statusRecord.setDateTime(LocalDateTime.now());
////        projectStatusRepository.save(statusRecord);
////
////        sendNotificationToDirector(project);
////        return convertToDTO(project);
////    }
////
////    @Transactional
////    public ProjectResponseDTO updateProject(Long clientUserId, Long projectId, ProjectUpdateRequest request) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (!project.getUploader().getUserId().equals(clientUserId)) {
////            throw new RuntimeException("You can only update your own projects");
////        }
////
////        if (request.getProjectName() != null) project.setProjectName(request.getProjectName());
////        if (request.getProjectDescription() != null) project.setProjectDescription(request.getProjectDescription());
////        if (request.getProjectType() != null) project.setProjectType(request.getProjectType());
////        if (request.getCost() != null) project.setCost(request.getCost());
////
////        project = projectRepository.save(project);
////        return convertToDTO(project);
////    }
////
////    @Transactional
////    public void deleteProject(Long clientUserId, Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (!project.getUploader().getUserId().equals(clientUserId)) {
////            throw new RuntimeException("You can only delete your own projects");
////        }
////
////        List<ProjectStatus> statusRecords = projectStatusRepository.findByProjectProjectId(projectId);
////        projectStatusRepository.deleteAll(statusRecords);
////        projectRepository.delete(project);
////    }
////
////    public List<ProjectResponseDTO> getMyProjects(Long clientUserId) {
////        return projectRepository.findByUploaderUserId(clientUserId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    public ProjectResponseDTO getProjectById(Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////        return convertToDTO(project);
////    }
////
////    // ==================== DIRECTOR APIs ====================
////
////    public boolean isDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////        return isEmp && isDirector;
////    }
////
////    public Long getDirectorDepartmentId(Long directorUserId) {
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(directorUserId)
////                .orElseThrow(() -> new RuntimeException("Employee details not found"));
////
////        if (employee.getDepartmentRef() == null) {
////            throw new RuntimeException("Director not assigned to any department");
////        }
////        return employee.getDepartmentRef().getDepartmentId();
////    }
////
////    public List<ProjectResponseDTO> getDepartmentProjects(Long directorUserId) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can view department projects");
////        }
////        Long departmentId = getDirectorDepartmentId(directorUserId);
////        return projectRepository.findByDepartmentDepartmentId(departmentId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    public List<ProjectResponseDTO> getPendingProjectsForDirector(Long directorUserId) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can view pending projects");
////        }
////        Long departmentId = getDirectorDepartmentId(directorUserId);
////        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ProjectResponseDTO assignProjectToManager(Long directorUserId, Long projectId, ProjectAssignRequest request) {
////        if (!isDirector(directorUserId)) {
////            throw new RuntimeException("Access denied: Only directors can assign projects");
////        }
////
////        Long directorDeptId = getDirectorDepartmentId(directorUserId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(directorDeptId)) {
////            throw new RuntimeException("This project does not belong to your department");
////        }
////
////        User manager = userRepository.findById(request.getManagerId())
////                .orElseThrow(() -> new RuntimeException("Manager not found"));
////
////        if (!isManagerInDepartment(manager.getUserId(), directorDeptId)) {
////            throw new RuntimeException("Manager must belong to the same department");
////        }
////
////        // Get manager performance data for email
////        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
////        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());
////
////        project.setAssignedManager(manager);
////        project.setStatus("ASSIGNED");
////        project.setDeadline(request.getDeadline());
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus("ASSIGNED");
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setHandledBy(userRepository.findById(directorUserId).get());
////        statusRecord.setAssignedTo(manager);
////        statusRecord.setDirectorNote(request.getDirectorNote());
////        statusRecord.setDeadline(request.getDeadline());
////        projectStatusRepository.save(statusRecord);
////
////        sendAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
////        sendProjectAssignedToClientEmail(project, manager);
////
////        return convertToDTO(project);
////    }
////
////    private boolean isManagerInDepartment(Long userId, Long departmentId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
////
////        if (!isEmp || !isManager) return false;
////
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
////        return employee != null && employee.getDepartmentRef() != null &&
////                employee.getDepartmentRef().getDepartmentId().equals(departmentId);
////    }
////
////    // ==================== MANAGER APIs ====================
////
////    public boolean isManager(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isManager = userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
////        return isEmp && isManager;
////    }
////
////    public List<ProjectResponseDTO> getMyAssignedProjects(Long managerUserId) {
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Access denied: Only managers can view assigned projects");
////        }
////        return projectRepository.findByAssignedManagerUserId(managerUserId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ProjectResponseDTO updateProjectStatus(Long managerUserId, Long projectId, String status, String note) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getAssignedManager() == null ||
////                !project.getAssignedManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You are not assigned to this project");
////        }
////
////        String oldStatus = project.getStatus();
////        project.setStatus(status);
////        project = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus(status);
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setAssignedTo(userRepository.findById(managerUserId).get());
////        statusRecord.setManagerNote(note);
////        projectStatusRepository.save(statusRecord);
////
////        sendStatusUpdateEmail(project, oldStatus, status, note);
////        sendStatusUpdateToClientEmail(project, status, note);
////
////        return convertToDTO(project);
////    }
////
////    // ==================== STATUS HISTORY API ====================
////
////    public List<ProjectStatusDTO> getProjectStatusHistory(Long projectId, Long currentUserId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        boolean hasAccess = false;
////
////        // Client access
////        if (project.getUploader().getUserId().equals(currentUserId)) {
////            hasAccess = true;
////        }
////
////        // Director access (same department)
////        if (isDirector(currentUserId)) {
////            Long directorDeptId = getDirectorDepartmentId(currentUserId);
////            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
////                hasAccess = true;
////            }
////        }
////
////        // Manager access (assigned to this project)
////        if (isManager(currentUserId) && project.getAssignedManager() != null) {
////            if (project.getAssignedManager().getUserId().equals(currentUserId)) {
////                hasAccess = true;
////            }
////        }
////
////        if (!hasAccess) {
////            throw new RuntimeException("You don't have access to view this project's history");
////        }
////
////        return projectStatusRepository.findByProjectProjectId(projectId)
////                .stream()
////                .map(this::convertToStatusDTO)
////                .collect(Collectors.toList());
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    private ProjectResponseDTO convertToDTO(ProjectDetails project) {
////        return ProjectResponseDTO.builder()
////                .projectId(project.getProjectId())
////                .projectName(project.getProjectName())
////                .projectDescription(project.getProjectDescription())
////                .projectType(project.getProjectType())
////                .status(project.getStatus())
////                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
////                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
////                .clientId(project.getUploader().getUserId())
////                .clientName(project.getUploader().getName())
////                .clientEmail(project.getUploader().getEmail())
////                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
////                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
////                .deadline(project.getDeadline())
////                .cost(project.getCost())
////                .createdAt(project.getCreatedAt())
////                .updatedAt(project.getUpdatedAt())
////                .build();
////    }
////
////    private ProjectStatusDTO convertToStatusDTO(ProjectStatus status) {
////        return ProjectStatusDTO.builder()
////                .statusId(status.getStatusId())
////                .projectId(status.getProject().getProjectId())
////                .projectName(status.getProject().getProjectName())
////                .status(status.getStatus())
////                .dateTime(status.getDateTime())
////                .handledById(status.getHandledBy() != null ? status.getHandledBy().getUserId() : null)
////                .handledByName(status.getHandledBy() != null ? status.getHandledBy().getName() : null)
////                .assignedToId(status.getAssignedTo() != null ? status.getAssignedTo().getUserId() : null)
////                .assignedToName(status.getAssignedTo() != null ? status.getAssignedTo().getName() : null)
////                .directorNote(status.getDirectorNote())
////                .managerNote(status.getManagerNote())
////                .deadline(status.getDeadline())
////                .build();
////    }
////
////    // ==================== IMPROVED EMAIL METHODS ====================
////
////    private void sendNotificationToDirector(ProjectDetails project) {
////        try {
////            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
////            User director = employees.stream()
////                    .filter(e -> e.getDepartmentRef() != null &&
////                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
////                    .map(EmployeeDetails::getUser)
////                    .filter(user -> {
////                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
////                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////                    })
////                    .findFirst()
////                    .orElse(null);
////
////            if (director != null) {
////                String subject = "📋 New Project Created - Action Required";
////                String html = String.format("""
////                    <html>
////                    <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                        <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                            <h2 style='color: #4CAF50;'>📋 New Project Created</h2>
////                            <p>Dear <strong>%s</strong>,</p>
////                            <p>A new project has been created and requires your approval.</p>
////                            <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                                <p><strong>📁 Project:</strong> %s</p>
////                                <p><strong>👤 Client:</strong> %s</p>
////                                <p><strong>🏢 Department:</strong> %s</p>
////                                <p><strong>💰 Estimated Cost:</strong> %s</p>
////                                <p><strong>📅 Created:</strong> %s</p>
////                            </div>
////                            <p>Please login to review and assign this project.</p>
////                            <hr style='margin: 20px 0;'>
////                            <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                        </div>
////                    </body>
////                    </html>
////                    """,
////                        director.getName(),
////                        project.getProjectName(),
////                        project.getUploader().getName(),
////                        project.getDepartment().getDepartmentName(),
////                        project.getCost() != null ? project.getCost() : "Not specified",
////                        project.getCreatedAt().format(formatter)
////                );
////                emailService.sendEmail(director.getEmail(), subject, html);
////            }
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
////        try {
////            String subject = "📋 New Project Assigned to You";
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #2196F3;'>📋 New Project Assigned</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>A new project has been assigned to you.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📁 Project:</strong> %s</p>
////                            <p><strong>📝 Description:</strong> %s</p>
////                            <p><strong>💰 Budget:</strong> %s</p>
////                            <p><strong>📅 Deadline:</strong> %s</p>
////                            <p><strong>📝 Director Note:</strong> %s</p>
////                        </div>
////                        <p>Please login to create teams and assign tasks.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    manager.getName(),
////                    project.getProjectName(),
////                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
////                    project.getCost() != null ? project.getCost() : "Not specified",
////                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set",
////                    note != null ? note : "No additional notes"
////            );
////            emailService.sendEmail(manager.getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
////        try {
////            String subject = "✅ Project Assigned - Update";
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #4CAF50;'>✅ Project Update</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Your project <strong>%s</strong> has been assigned to a manager.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>👨‍💼 Assigned Manager:</strong> %s</p>
////                            <p><strong>📅 Deadline:</strong> %s</p>
////                        </div>
////                        <p>You can track the project progress from your dashboard.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    project.getUploader().getName(),
////                    project.getProjectName(),
////                    manager.getName(),
////                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set"
////            );
////            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendStatusUpdateEmail(ProjectDetails project, String oldStatus, String newStatus, String note) {
////        try {
////            List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
////            User director = employees.stream()
////                    .filter(e -> e.getDepartmentRef() != null &&
////                            e.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId()))
////                    .map(EmployeeDetails::getUser)
////                    .filter(user -> {
////                        List<UserType> types = userTypeRepository.findByUserUserId(user.getUserId());
////                        return types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////                    })
////                    .findFirst()
////                    .orElse(null);
////
////            if (director != null) {
////                String subject = "🔄 Project Status Updated";
////                String html = String.format("""
////                    <html>
////                    <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                        <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                            <h2 style='color: #FF9800;'>🔄 Project Status Update</h2>
////                            <p>Dear <strong>%s</strong>,</p>
////                            <p>Project <strong>%s</strong> status has been updated.</p>
////                            <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                                <p><strong>Status Changed:</strong> %s → %s</p>
////                                <p><strong>Manager Note:</strong> %s</p>
////                            </div>
////                            <hr style='margin: 20px 0;'>
////                            <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                        </div>
////                    </body>
////                    </html>
////                    """,
////                        director.getName(),
////                        project.getProjectName(),
////                        oldStatus,
////                        newStatus,
////                        note != null ? note : "No additional notes"
////                );
////                emailService.sendEmail(director.getEmail(), subject, html);
////            }
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendStatusUpdateToClientEmail(ProjectDetails project, String newStatus, String note) {
////        try {
////            String subject = "🔄 Project Status Update - " + project.getProjectName();
////            String statusIcon = newStatus.equals("COMPLETED") ? "✅" : newStatus.equals("IN_PROGRESS") ? "🔄" : "📋";
////            String statusColor = newStatus.equals("COMPLETED") ? "#4CAF50" : newStatus.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
////
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: %s;'>%s Project Status Update</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Your project <strong>%s</strong> status has been updated.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>New Status:</strong> %s %s</p>
////                            <p><strong>Manager Note:</strong> %s</p>
////                        </div>
////                        <p>Please login to your dashboard to view more details.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    statusColor,
////                    statusIcon,
////                    project.getUploader().getName(),
////                    project.getProjectName(),
////                    statusIcon,
////                    newStatus,
////                    note != null ? note : "No additional notes"
////            );
////            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////}