package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.*;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CoreDirectorService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private ConsultantStatusRepository consultantStatusRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private ProjectStatusRepository projectStatusRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeamDetailsRepository teamRepository;

    @Autowired
    private TeamMembersRepository teamMembersRepository;

    @Autowired
    private EmpTaskRepository empTaskRepository;

    @Autowired
    private ConsultantReviewRepository consultantReviewRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ==================== VALIDATION ====================

    public boolean isCoreDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));

        if (!isEmp || !isDirector) return false;

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (employee == null) return false;

        return employee.getDepartmentRef() != null;
    }

    private void validateCoreDirector(Long userId) {
        if (!isCoreDirector(userId)) {
            throw new RuntimeException("Access denied: Only Core Director can access this resource");
        }
    }

    private Long getDirectorDepartmentId(Long userId) {
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (employee.getDepartmentRef() == null) {
            throw new RuntimeException("Director not assigned to any department");
        }
        return employee.getDepartmentRef().getDepartmentId();
    }

    // ==================== CONSULTANT MANAGEMENT ====================

    public List<ConsultantListResponseDTO> getMyDepartmentConsultants(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);

        return consultants.stream().map(c -> {
            String latestStatus = consultantStatusRepository.findLatestStatus(c.getConsultantId())
                    .map(ConsultantStatus::getStatus).orElse("PENDING");

            String isApproved = userTypeRepository.findByUserUserIdAndPtype(c.getUser().getUserId(), "CONSULTANT")
                    .map(UserType::getIsapproved).orElse("PENDING");

            return ConsultantListResponseDTO.builder()
                    .consultantId(c.getConsultantId())
                    .userId(c.getUser().getUserId())
                    .name(c.getUser().getName())
                    .email(c.getUser().getEmail())
                    .photoUrl(c.getUser().getPhotoUrl())
                    .skill(c.getSkill())
                    .experience(c.getExperience())
                    .currentStatus(latestStatus)
                    .departmentId(departmentId)
                    .departmentName(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
                    .isApproved(isApproved)
                    .build();
        }).collect(Collectors.toList());
    }

    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Consultant not found in your department");
        }

        String latestStatus = consultantStatusRepository.findLatestStatus(consultantId)
                .map(ConsultantStatus::getStatus).orElse("PENDING");

        String isApproved = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
                .map(UserType::getIsapproved).orElse("PENDING");

        return ConsultantListResponseDTO.builder()
                .consultantId(consultant.getConsultantId())
                .userId(consultant.getUser().getUserId())
                .name(consultant.getUser().getName())
                .email(consultant.getUser().getEmail())
                .photoUrl(consultant.getUser().getPhotoUrl())
                .skill(consultant.getSkill())
                .experience(consultant.getExperience())
                .currentStatus(latestStatus)
                .departmentId(departmentId)
                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
                .isApproved(isApproved)
                .build();
    }

    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Consultant not found in your department");
        }

        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);

        return statusList.stream().map(status -> ConsultantStatusHistoryDTO.builder()
                .statusId(status.getStatusId())
                .status(status.getStatus())
                .note(status.getNote())
                .dateTime(status.getDateTime())
                .build()).collect(Collectors.toList());
    }

    @Transactional
    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Consultant not found in your department");
        }

        ConsultantStatus status = new ConsultantStatus();
        status.setConsultant(consultant);
        status.setStatus(request.getStatus());
        status.setNote(request.getNote());
        status.setDateTime(LocalDateTime.now());

        ConsultantStatus savedStatus = consultantStatusRepository.save(status);

        if ("APPROVED".equals(request.getStatus())) {
            userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
                    .ifPresent(ut -> {
                        ut.setIsapproved("APPROVED");
                        userTypeRepository.save(ut);
                    });
        }

        sendConsultantStatusEmail(consultant, request);

        return ConsultantStatusResponseDTO.builder()
                .statusId(savedStatus.getStatusId())
                .consultantId(consultantId)
                .consultantName(consultant.getUser().getName())
                .status(savedStatus.getStatus())
                .note(savedStatus.getNote())
                .dateTime(savedStatus.getDateTime())
                .build();
    }

    // Service me ye method add karo

    @Transactional
    public String convertConsultantToEmployeeByCoreDirector(Long userId, Long consultantId,
                                                            ConvertToEmployeeByCoreDirectorRequest request) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);  // ✅ Auto department

        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Consultant not found in your department");
        }

        User user = consultant.getUser();

        // Check if already employee
        if (employeeDetailsRepository.findByUserUserId(user.getUserId()).isPresent()) {
            throw new RuntimeException("User is already an employee");
        }

        // ✅ Department auto - Core Director ki department use karo
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // ✅ Create Employee (stype = MANAGER default)
        EmployeeDetails employee = new EmployeeDetails();
        employee.setUser(user);
        employee.setIsDirectorVerified(true);
        employee.setPositionName(request.getPositionName());
        employee.setSalary(request.getSalary());
        employee.setSkillSet(request.getSkillSet());
        employee.setDepartmentRef(department);
        employee.setDepartment(department.getDepartmentName());

        employeeDetailsRepository.save(employee);

        // ✅ Add EMP role with stype MANAGER
        UserType empRole = new UserType();
        empRole.setUser(user);
        empRole.setPtype("EMP");
        empRole.setStype("MANAGER");  // ✅ Fixed - Only MANAGER
        empRole.setIsapproved("APPROVED");
        empRole.setCreatedAt(LocalDateTime.now());
        userTypeRepository.save(empRole);

        // ✅ Update consultant status
        ConsultantStatus status = new ConsultantStatus();
        status.setConsultant(consultant);
        status.setStatus("CONVERTED_TO_EMPLOYEE");
        status.setNote("Converted to Employee (Manager) - " + request.getPositionName());
        status.setDateTime(LocalDateTime.now());
        consultantStatusRepository.save(status);

        // ✅ Send Email
        sendConversionByCoreDirectorEmail(consultant, employee, department);

        return "Consultant converted to Employee (Manager) successfully. Employee ID: " + employee.getEmployeeId();
    }

    // Email method
    private void sendConversionByCoreDirectorEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
        try {
            String subject = "🎉 Congratulations! Converted to Permanent Employee - Softmax Technologies and IT Services";
            String html = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center; }
                    .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                    .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                    .content { padding: 40px 35px; }
                    .info-box { background: linear-gradient(135deg,#f0f4ff 0%,#e8eeff 100%); padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #667eea; }
                    .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                    .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                    .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                    .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Welcome as Permanent Employee!</h1>
                        <p>Softmax Technologies and IT Services</p>
                    </div>
                    <div class="content">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">Congratulations! You have been converted from Consultant to <strong style="color:#667eea;">Permanent Employee</strong>.</p>
                        <div class="info-box">
                            <p><strong>📋 Employment Details:</strong></p>
                            <p>🆔 <strong>Employee ID:</strong> %d</p>
                            <p>💼 <strong>Position:</strong> %s</p>
                            <p>👔 <strong>Role Type:</strong> MANAGER</p>
                            <p>🏢 <strong>Department:</strong> %s</p>
                            <p>💰 <strong>Salary:</strong> ₹%.2f</p>
                            <p>🛠️ <strong>Skills:</strong> %s</p>
                        </div>
                        <p style="color:#4a5568;">Please login to your dashboard for more details.</p>
                        <div style="text-align: center;">
                            <a href="https://nexus.softmax.in" class="btn">🚀 Go to Dashboard</a>
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
                    consultant.getUser().getName(),
                    employee.getEmployeeId(),
                    employee.getPositionName(),
                    department.getDepartmentName(),
                    employee.getSalary(),
                    employee.getSkillSet()
            );
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    // ==================== EMPLOYEE MANAGEMENT ====================

    public List<EmployeeDTO> getMyDepartmentEmployees(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);

        return employees.stream()
                .filter(e -> {
                    List<UserType> userTypes = userTypeRepository.findByUserUserId(e.getUser().getUserId());
                    return userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
                })
                .map(e -> {
                    User u = e.getUser();
                    return EmployeeDTO.builder()
                            .employeeId(e.getEmployeeId())
                            .userId(u.getUserId())
                            .name(u.getName())
                            .email(u.getEmail())
                            .photoUrl(u.getPhotoUrl())
                            .phoneNumber(u.getPhoneNumber())
                            .address(u.getAddress())
                            .gender(u.getGender())
                            .isDirectorVerified(e.getIsDirectorVerified())
                            .positionName(e.getPositionName())
                            .salary(e.getSalary())
                            .department(e.getDepartment())
                            .skillSet(e.getSkillSet())
                            .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
                            .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        EmployeeDetails employee = employeeDetailsRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Employee not found in your department");
        }

        User u = employee.getUser();
        return EmployeeDTO.builder()
                .employeeId(employee.getEmployeeId())
                .userId(u.getUserId())
                .name(u.getName())
                .email(u.getEmail())
                .photoUrl(u.getPhotoUrl())
                .phoneNumber(u.getPhoneNumber())
                .address(u.getAddress())
                .gender(u.getGender())
                .isDirectorVerified(employee.getIsDirectorVerified())
                .positionName(employee.getPositionName())
                .salary(employee.getSalary())
                .department(employee.getDepartment())
                .skillSet(employee.getSkillSet())
                .departmentId(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentId() : null)
                .departmentName(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : null)
                .build();
    }

    // ==================== PROJECT MANAGEMENT ====================

    public List<ProjectResponseDTO> getMyDepartmentProjects(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        return projectRepository.findByDepartmentDepartmentId(departmentId)
                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> getPendingProjects(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponseDTO approveProject(Long userId, Long projectId, String status, String note) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Project not found in your department");
        }

        project.setStatus(status);
        ProjectDetails updated = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus(status);
        statusRecord.setDateTime(LocalDateTime.now());
        statusRecord.setHandledBy(userRepository.findById(userId).get());
        statusRecord.setDirectorNote(note);
        projectStatusRepository.save(statusRecord);

        sendProjectStatusUpdateEmail(project, status, note);
        return convertToProjectDTO(updated);
    }

    @Transactional
    public ProjectResponseDTO assignProjectToManager(Long userId, Long projectId, ProjectAssignRequest request) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Project not found in your department");
        }

        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());

        project.setAssignedManager(manager);
        project.setStatus("ASSIGNED");
        project.setDeadline(request.getDeadline());
        if (request.getCost() != null) {
            project.setCost(request.getCost());
        }
        ProjectDetails updated = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus("ASSIGNED");
        statusRecord.setDateTime(LocalDateTime.now());
        statusRecord.setHandledBy(userRepository.findById(userId).get());
        statusRecord.setAssignedTo(manager);
        statusRecord.setDirectorNote(request.getDirectorNote());
        statusRecord.setDeadline(request.getDeadline());
        projectStatusRepository.save(statusRecord);

        sendProjectAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
        sendProjectAssignedToClientEmail(project, manager);

        return convertToProjectDTO(updated);
    }

    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Project not found in your department");
        }

        return convertToProjectDTO(project);
    }

    public List<ProjectStatusDTO> getProjectHistory(Long userId, Long projectId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Project not found in your department");
        }

        return projectStatusRepository.findByProjectProjectId(projectId)
                .stream().map(this::convertToStatusDTO).collect(Collectors.toList());
    }

    // ==================== TEAM MANAGEMENT ====================

    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Project not found in your department");
        }

        List<TeamDetails> teams = teamRepository.findByProjectProjectId(projectId);

        return teams.stream().map(team -> {
            List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
            List<TeamMemberDTO> memberDTOs = members.stream()
                    .map(m -> TeamMemberDTO.builder()
                            .memberId(m.getMember().getUserId())
                            .memberName(m.getMember().getName())
                            .memberEmail(m.getMember().getEmail())
                            .photoUrl(m.getMember().getPhotoUrl())
                            .role(m.getRole())
                            .description(m.getDescription())
                            .build())
                    .collect(Collectors.toList());

            return TeamResponseDTO.builder()
                    .teamId(team.getTeamId())
                    .teamName(team.getTeamName())
                    .projectId(team.getProject().getProjectId())
                    .projectName(team.getProject().getProjectName())
                    .managerId(team.getManager().getUserId())
                    .managerName(team.getManager().getName())
                    .members(memberDTOs)
                    .createdAt(team.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== TASK MANAGEMENT ====================

    @Transactional
    public AssignedTaskResponseDTO assignTaskToEmployee(Long userId, AssignTaskToEmployeeRequest request) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        User assignedTo = userRepository.findById(request.getAssignedToUserId())
                .orElseThrow(() -> new RuntimeException("Assigned user not found"));

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(assignedTo.getUserId())
                .orElseThrow(() -> new RuntimeException("Assigned user is not an employee"));

        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
            throw new RuntimeException("Employee not found in your department");
        }

        EmpTask task = new EmpTask();
        task.setCreator(creator);
        task.setAssignedTo(assignedTo);
        task.setStatus("PENDING");
        task.setDescription(request.getDescription());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        EmpTask savedTask = empTaskRepository.save(task);

        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline());

        return AssignedTaskResponseDTO.builder()
                .taskId(savedTask.getEmpTaskId())
                .taskDetails("Task Assigned")
                .description(savedTask.getDescription())
                .status(savedTask.getStatus())
                .assignedToUserId(assignedTo.getUserId())
                .assignedToUserName(assignedTo.getName())
                .createdByUserId(creator.getUserId())
                .createdByName(creator.getName())
                .deadline(request.getDeadline())
                .createdAt(savedTask.getCreatedAt())
                .updatedAt(savedTask.getUpdatedAt())
                .build();
    }

    public List<AssignedTaskResponseDTO> getTasksByDepartment(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
        List<Long> employeeUserIds = employees.stream()
                .map(e -> e.getUser().getUserId())
                .collect(Collectors.toList());

        List<EmpTask> tasks = empTaskRepository.findAll().stream()
                .filter(t -> employeeUserIds.contains(t.getAssignedTo().getUserId()))
                .collect(Collectors.toList());

        return tasks.stream().map(task -> AssignedTaskResponseDTO.builder()
                .taskId(task.getEmpTaskId())
                .taskDetails("Task Assigned")
                .description(task.getDescription())
                .status(task.getStatus())
                .assignedToUserId(task.getAssignedTo().getUserId())
                .assignedToUserName(task.getAssignedTo().getName())
                .createdByUserId(task.getCreator().getUserId())
                .createdByName(task.getCreator().getName())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    public AssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status) {
        validateCoreDirector(userId);

        EmpTask task = empTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
        }

        task.setStatus(status);
        EmpTask updatedTask = empTaskRepository.save(task);

        sendTaskStatusUpdateEmail(task);

        return AssignedTaskResponseDTO.builder()
                .taskId(updatedTask.getEmpTaskId())
                .taskDetails("Task Assigned")
                .description(updatedTask.getDescription())
                .status(updatedTask.getStatus())
                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
                .assignedToUserName(updatedTask.getAssignedTo().getName())
                .createdByUserId(updatedTask.getCreator().getUserId())
                .createdByName(updatedTask.getCreator().getName())
                .createdAt(updatedTask.getCreatedAt())
                .updatedAt(updatedTask.getUpdatedAt())
                .build();
    }

    // ==================== DASHBOARD ====================

    public Map<String, Object> getDashboardStats(Long userId) {
        validateCoreDirector(userId);
        Long departmentId = getDirectorDepartmentId(userId);

        List<ProjectDetails> projects = projectRepository.findByDepartmentDepartmentId(departmentId);
        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
        List<TeamDetails> teams = teamRepository.findAll().stream()
                .filter(t -> t.getProject().getDepartment().getDepartmentId().equals(departmentId))
                .collect(Collectors.toList());
        List<EmpTask> tasks = empTaskRepository.findAll().stream()
                .filter(t -> employees.stream().anyMatch(e -> e.getUser().getUserId().equals(t.getAssignedTo().getUserId())))
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProjects", projects.size());
        stats.put("pendingProjects", projects.stream().filter(p -> "PENDING".equals(p.getStatus())).count());
        stats.put("inProgressProjects", projects.stream().filter(p -> "IN_PROGRESS".equals(p.getStatus())).count());
        stats.put("completedProjects", projects.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count());
        stats.put("totalConsultants", consultants.size());
        stats.put("totalEmployees", employees.size());
        stats.put("totalTeams", teams.size());
        stats.put("totalTasks", tasks.size());
        stats.put("pendingTasks", tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());

        return stats;
    }

    // ==================== HELPER METHODS ====================

    private ProjectResponseDTO convertToProjectDTO(ProjectDetails project) {
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

    private void sendConsultantStatusEmail(ConsultantDetails consultant, UpdateConsultantStatusRequest request) {
        try {
            // ✅ HAR STATUS KE LIYE ALAG ICON/AESTHETIC
            String status = request.getStatus() != null ? request.getStatus() : "";
            String statusIcon;
            String statusColor;
            switch (status.toUpperCase()) {
                case "APPROVED": statusIcon = "✅"; statusColor = "#11998e"; break;
                case "REJECTED": statusIcon = "❌"; statusColor = "#ee5a24"; break;
                case "INTERVIEW_SCHEDULED": statusIcon = "📅"; statusColor = "#667eea"; break;
                case "SHORTLISTED": statusIcon = "🌟"; statusColor = "#f6c23e"; break;
                case "ON_HOLD": statusIcon = "⏸️"; statusColor = "#f093fb"; break;
                case "APPLIED": statusIcon = "📩"; statusColor = "#2193b0"; break;
                default: statusIcon = "📋"; statusColor = "#667eea"; break;
            }

            String subject = statusIcon + " Consultant Status Update: " + status + " - Softmax Technologies and IT Services";

            // ✅ Note ko clickable banao (agar link ho)
            String safeNote = EmailService.makeLinksClickable(
                    request.getNote() != null ? request.getNote() : "No additional notes");

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
                        .status-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
                        .status-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .status-badge { display: inline-block; background: %s20; color: %s; padding: 8px 20px; border-radius: 30px; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; }
                        .note-box { background: #fff9e6; padding: 18px 22px; border-radius: 12px; margin: 20px 0; border-left: 5px solid #f6c23e; }
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
                            <h1>%s Consultant Status Update</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your consultant application status has been updated.</p>
                            <div class="status-box">
                                <p><strong>📊 New Status:</strong></p>
                                <span class="status-badge">%s %s</span>
                                <p style="margin-top:16px;"><strong>📅 Updated On:</strong> %s</p>
                            </div>
                            <div class="note-box">
                                <p><strong>📝 Note from HR:</strong></p>
                                <p>%s</p>
                            </div>
                            <p style="color:#4a5568;">Please login to your dashboard for more details.</p>
                            <div style="text-align: center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 Go to Dashboard</a>
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
                    consultant.getUser().getName(),
                    statusIcon, status,
                    LocalDateTime.now().format(formatter),
                    safeNote
            );
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
        try {
            String subject = "🎉 Congratulations! You are now an Employee - Softmax Technologies and IT Services";
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #667eea; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🎉 Welcome to the Team!</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Congratulations! You have been converted from Consultant to Employee.</p>
                            <div class="info-box">
                                <p><strong>📋 Employment Details:</strong></p>
                                <p>💼 <strong>Position:</strong> %s</p>
                                <p>🏢 <strong>Department:</strong> %s</p>
                                <p>💰 <strong>Salary:</strong> ₹%.2f</p>
                            </div>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 Go to Dashboard</a>
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
                    consultant.getUser().getName(),
                    employee.getPositionName(),
                    department.getDepartmentName(),
                    employee.getSalary()
            );
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendProjectStatusUpdateEmail(ProjectDetails project, String status, String note) {
        try {
            String statusIcon = status.equals("COMPLETED") ? "✅" : status.equals("IN_PROGRESS") ? "🔄" : "📋";
            String statusColor = status.equals("COMPLETED") ? "#4CAF50" : status.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";

            String subject = statusIcon + " Project Status Update - " + project.getProjectName();
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
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>%s Project Status Updated</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Project <strong>%s</strong> status has been updated.</p>
                            <div class="info-box">
                                <p><strong>New Status:</strong> %s %s</p>
                                <p><strong>Director Note:</strong></p>
                                <p>%s</p>
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
                    statusColor, statusColor,
                    statusIcon,
                    project.getUploader().getName(),
                    project.getProjectName(),
                    statusIcon,
                    status,
                    safeNote
            );
            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendProjectAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
        try {
            String subject = "📋 New Project Assigned to You - " + project.getProjectName();
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
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e8f4f8; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2193b0; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                        .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#2193b0 0%,#6dd5ed 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
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
                                <p>📝 <strong>Director Note:</strong></p>
                                <p>%s</p>
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

    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
        try {
            String subject = "✅ Project Update - " + project.getProjectName() + " has been assigned";
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
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

    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline) {
        try {
            String subject = "📋 New Task Assigned to You - " + task.getDescription();
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #FF9800 0%, #FF5722 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #fff3e0; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #FF9800; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#FF9800 0%,#FF5722 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #FF9800; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>📋 New Task Assigned</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">You have been assigned a new task by <strong>%s</strong>.</p>
                            <div class="info-box">
                                <p>📝 <strong>Task:</strong> %s</p>
                                <p>⏰ <strong>Deadline:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">Please login to your dashboard to update status.</p>
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
                    assignedTo.getName(),
                    creator.getName(),
                    task.getDescription(),
                    deadline != null ? deadline.format(formatter) : "Not set"
            );
            emailService.sendEmail(assignedTo.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }

    private void sendTaskStatusUpdateEmail(EmpTask task) {
        try {
            User assignedTo = task.getAssignedTo();
            User creator = task.getCreator();

            String subject = "🔄 Task Status Updated - " + task.getDescription();
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #2196F3 0%, #1a237e 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e3f2fd; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2196F3; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#2196F3 0%,#1a237e 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #2196F3; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🔄 Task Status Updated</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Task status has been updated by <strong>%s</strong>.</p>
                            <div class="info-box">
                                <p>📝 <strong>Task:</strong> %s</p>
                                <p>📊 <strong>New Status:</strong> %s</p>
                            </div>
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
                    assignedTo.getName(),
                    task.getDescription(),
                    task.getStatus()
            );
            emailService.sendEmail(creator.getEmail(), subject, html);
        } catch (Exception e) {
            System.out.println("Email error: " + e.getMessage());
        }
    }
}




















//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.*;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class CoreDirectorService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private ConsultantDetailsRepository consultantDetailsRepository;
//
//    @Autowired
//    private ConsultantStatusRepository consultantStatusRepository;
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
//
//    @Autowired
//    private ProjectStatusRepository projectStatusRepository;
//
//    @Autowired
//    private DepartmentRepository departmentRepository;
//
//    @Autowired
//    private TeamDetailsRepository teamRepository;
//
//    @Autowired
//    private TeamMembersRepository teamMembersRepository;
//
//    @Autowired
//    private EmpTaskRepository empTaskRepository;
//
//    @Autowired
//    private ConsultantReviewRepository consultantReviewRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    // ==================== VALIDATION ====================
//
//    public boolean isCoreDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//
//        if (!isEmp || !isDirector) return false;
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//        if (employee == null) return false;
//
//        return employee.getDepartmentRef() != null;
//    }
//
//    private void validateCoreDirector(Long userId) {
//        if (!isCoreDirector(userId)) {
//            throw new RuntimeException("Access denied: Only Core Director can access this resource");
//        }
//    }
//
//    private Long getDirectorDepartmentId(Long userId) {
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//        if (employee.getDepartmentRef() == null) {
//            throw new RuntimeException("Director not assigned to any department");
//        }
//        return employee.getDepartmentRef().getDepartmentId();
//    }
//
//    // ==================== CONSULTANT MANAGEMENT ====================
//
//    public List<ConsultantListResponseDTO> getMyDepartmentConsultants(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
//
//        return consultants.stream().map(c -> {
//            String latestStatus = consultantStatusRepository.findLatestStatus(c.getConsultantId())
//                    .map(ConsultantStatus::getStatus).orElse("PENDING");
//
//            String isApproved = userTypeRepository.findByUserUserIdAndPtype(c.getUser().getUserId(), "CONSULTANT")
//                    .map(UserType::getIsapproved).orElse("PENDING");
//
//            return ConsultantListResponseDTO.builder()
//                    .consultantId(c.getConsultantId())
//                    .userId(c.getUser().getUserId())
//                    .name(c.getUser().getName())
//                    .email(c.getUser().getEmail())
//                    .photoUrl(c.getUser().getPhotoUrl())
//                    .skill(c.getSkill())
//                    .experience(c.getExperience())
//                    .currentStatus(latestStatus)
//                    .departmentId(departmentId)
//                    .departmentName(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
//                    .isApproved(isApproved)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Consultant not found in your department");
//        }
//
//        String latestStatus = consultantStatusRepository.findLatestStatus(consultantId)
//                .map(ConsultantStatus::getStatus).orElse("PENDING");
//
//        String isApproved = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
//                .map(UserType::getIsapproved).orElse("PENDING");
//
//        return ConsultantListResponseDTO.builder()
//                .consultantId(consultant.getConsultantId())
//                .userId(consultant.getUser().getUserId())
//                .name(consultant.getUser().getName())
//                .email(consultant.getUser().getEmail())
//                .photoUrl(consultant.getUser().getPhotoUrl())
//                .skill(consultant.getSkill())
//                .experience(consultant.getExperience())
//                .currentStatus(latestStatus)
//                .departmentId(departmentId)
//                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
//                .isApproved(isApproved)
//                .build();
//    }
//
//    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Consultant not found in your department");
//        }
//
//        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
//
//        return statusList.stream().map(status -> ConsultantStatusHistoryDTO.builder()
//                .statusId(status.getStatusId())
//                .status(status.getStatus())
//                .note(status.getNote())
//                .dateTime(status.getDateTime())
//                .build()).collect(Collectors.toList());
//    }
//
//    @Transactional
//    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Consultant not found in your department");
//        }
//
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(consultant);
//        status.setStatus(request.getStatus());
//        status.setNote(request.getNote());
//        status.setDateTime(LocalDateTime.now());
//
//        ConsultantStatus savedStatus = consultantStatusRepository.save(status);
//
//        if ("APPROVED".equals(request.getStatus())) {
//            userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
//                    .ifPresent(ut -> {
//                        ut.setIsapproved("APPROVED");
//                        userTypeRepository.save(ut);
//                    });
//        }
//
//        sendConsultantStatusEmail(consultant, request);
//
//        return ConsultantStatusResponseDTO.builder()
//                .statusId(savedStatus.getStatusId())
//                .consultantId(consultantId)
//                .consultantName(consultant.getUser().getName())
//                .status(savedStatus.getStatus())
//                .note(savedStatus.getNote())
//                .dateTime(savedStatus.getDateTime())
//                .build();
//    }
//// Service me ye method add karo
//
//    @Transactional
//    public String convertConsultantToEmployeeByCoreDirector(Long userId, Long consultantId,
//                                                            ConvertToEmployeeByCoreDirectorRequest request) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);  // ✅ Auto department
//
//        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Consultant not found in your department");
//        }
//
//        User user = consultant.getUser();
//
//        // Check if already employee
//        if (employeeDetailsRepository.findByUserUserId(user.getUserId()).isPresent()) {
//            throw new RuntimeException("User is already an employee");
//        }
//
//        // ✅ Department auto - Core Director ki department use karo
//        Department department = departmentRepository.findById(departmentId)
//                .orElseThrow(() -> new RuntimeException("Department not found"));
//
//        // ✅ Create Employee (stype = MANAGER default)
//        EmployeeDetails employee = new EmployeeDetails();
//        employee.setUser(user);
//        employee.setIsDirectorVerified(true);
//        employee.setPositionName(request.getPositionName());
//        employee.setSalary(request.getSalary());
//        employee.setSkillSet(request.getSkillSet());
//        employee.setDepartmentRef(department);
//        employee.setDepartment(department.getDepartmentName());
//
//        employeeDetailsRepository.save(employee);
//
//        // ✅ Add EMP role with stype MANAGER
//        UserType empRole = new UserType();
//        empRole.setUser(user);
//        empRole.setPtype("EMP");
//        empRole.setStype("MANAGER");  // ✅ Fixed - Only MANAGER
//        empRole.setIsapproved("APPROVED");
//        empRole.setCreatedAt(LocalDateTime.now());
//        userTypeRepository.save(empRole);
//
//        // ✅ Update consultant status
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(consultant);
//        status.setStatus("CONVERTED_TO_EMPLOYEE");
//        status.setNote("Converted to Employee (Manager) - " + request.getPositionName());
//        status.setDateTime(LocalDateTime.now());
//        consultantStatusRepository.save(status);
//
//        // ✅ Send Email
//        sendConversionByCoreDirectorEmail(consultant, employee, department);
//
//        return "Consultant converted to Employee (Manager) successfully. Employee ID: " + employee.getEmployeeId();
//    }
//
//    // Email method
//    private void sendConversionByCoreDirectorEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
//        try {
//            String subject = "🎉 Congratulations! Converted to Permanent Employee - " + consultant.getUser().getName();
//            String html = String.format("""
//            <html>
//            <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; padding: 20px; color: white;'>
//                    <h2 style='text-align: center;'>🎉 Welcome as Permanent Employee!</h2>
//                    <p>Dear <strong>%s</strong>,</p>
//                    <p>Congratulations! You have been converted from Consultant to <strong>Permanent Employee</strong>.</p>
//                    <div style='background: rgba(255,255,255,0.2); padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                        <p><strong>📋 Employment Details:</strong></p>
//                        <p>• Employee ID: <strong>%d</strong></p>
//                        <p>• Position: <strong>%s</strong></p>
//                        <p>• Role Type: <strong>MANAGER</strong></p>
//                        <p>• Department: <strong>%s</strong></p>
//                        <p>• Salary: <strong>₹%.2f</strong></p>
//                        <p>• Skills: <strong>%s</strong></p>
//                    </div>
//                    <p>Please login to your dashboard for more details.</p>
//                    <hr style='margin: 20px 0;'>
//                    <p style='font-size: 12px;'>ShardaOS - Project Management System</p>
//                </div>
//            </body>
//            </html>
//            """,
//                    consultant.getUser().getName(),
//                    employee.getEmployeeId(),
//                    employee.getPositionName(),
//                    department.getDepartmentName(),
//                    employee.getSalary(),
//                    employee.getSkillSet()
//            );
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
////    @Transactional
////    public String convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Consultant not found in your department");
////        }
////
////        User user = consultant.getUser();
////
////        if (employeeDetailsRepository.findByUserUserId(user.getUserId()).isPresent()) {
////            throw new RuntimeException("User is already an employee");
////        }
////
////        // ✅ FIXED: Consultant ki department use karo
////        Department department = consultant.getDepartment();
////
////        EmployeeDetails employee = new EmployeeDetails();
////        employee.setUser(user);
////        employee.setIsDirectorVerified(true);
////        employee.setPositionName(request.getPositionName());
////        employee.setSalary(request.getSalary());
////        employee.setSkillSet(request.getSkillSet());
////        employee.setDepartmentRef(department);
////        employee.setDepartment(department.getDepartmentName());
////
////        employeeDetailsRepository.save(employee);
////
////        UserType empRole = new UserType();
////        empRole.setUser(user);
////        empRole.setPtype("EMP");
////        empRole.setStype("MANAGER");
////        empRole.setIsapproved("APPROVED");
////        empRole.setCreatedAt(LocalDateTime.now());
////        userTypeRepository.save(empRole);
////
////        UpdateConsultantStatusRequest statusRequest = new UpdateConsultantStatusRequest();
////        statusRequest.setStatus("CONVERTED_TO_EMPLOYEE");
////        statusRequest.setNote("Converted to employee as " + request.getPositionName());
////        updateConsultantStatus(userId, consultantId, statusRequest);
////
////        sendConversionEmail(consultant, employee, department);
////
////        return "Consultant converted to employee successfully. Position: " + request.getPositionName();
////    }
//
//    // ==================== EMPLOYEE MANAGEMENT ====================
//
//    public List<EmployeeDTO> getMyDepartmentEmployees(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
//
//        return employees.stream()
//                .filter(e -> {
//                    List<UserType> userTypes = userTypeRepository.findByUserUserId(e.getUser().getUserId());
//                    return userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
//                })
//                .map(e -> {
//                    User u = e.getUser();
//                    return EmployeeDTO.builder()
//                            .employeeId(e.getEmployeeId())
//                            .userId(u.getUserId())
//                            .name(u.getName())
//                            .email(u.getEmail())
//                            .photoUrl(u.getPhotoUrl())
//                            .phoneNumber(u.getPhoneNumber())
//                            .address(u.getAddress())
//                            .gender(u.getGender())
//                            .isDirectorVerified(e.getIsDirectorVerified())
//                            .positionName(e.getPositionName())
//                            .salary(e.getSalary())
//                            .department(e.getDepartment())
//                            .skillSet(e.getSkillSet())
//                            .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
//                            .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                            .build();
//                })
//                .collect(Collectors.toList());
//    }
//
//    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        EmployeeDetails employee = employeeDetailsRepository.findById(employeeId)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Employee not found in your department");
//        }
//
//        User u = employee.getUser();
//        return EmployeeDTO.builder()
//                .employeeId(employee.getEmployeeId())
//                .userId(u.getUserId())
//                .name(u.getName())
//                .email(u.getEmail())
//                .photoUrl(u.getPhotoUrl())
//                .phoneNumber(u.getPhoneNumber())
//                .address(u.getAddress())
//                .gender(u.getGender())
//                .isDirectorVerified(employee.getIsDirectorVerified())
//                .positionName(employee.getPositionName())
//                .salary(employee.getSalary())
//                .department(employee.getDepartment())
//                .skillSet(employee.getSkillSet())
//                .departmentId(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentId() : null)
//                .departmentName(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : null)
//                .build();
//    }
//
//    // ==================== PROJECT MANAGEMENT ====================
//
//    public List<ProjectResponseDTO> getMyDepartmentProjects(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        return projectRepository.findByDepartmentDepartmentId(departmentId)
//                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
//    }
//
//    public List<ProjectResponseDTO> getPendingProjects(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
//                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
//    }
//
//    @Transactional
//    public ProjectResponseDTO approveProject(Long userId, Long projectId, String status, String note) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Project not found in your department");
//        }
//
//        project.setStatus(status);
//        ProjectDetails updated = projectRepository.save(project);
//
//        ProjectStatus statusRecord = new ProjectStatus();
//        statusRecord.setProject(project);
//        statusRecord.setStatus(status);
//        statusRecord.setDateTime(LocalDateTime.now());
//        statusRecord.setHandledBy(userRepository.findById(userId).get());
//        statusRecord.setDirectorNote(note);
//        projectStatusRepository.save(statusRecord);
//
//        sendProjectStatusUpdateEmail(project, status, note);
//        return convertToProjectDTO(updated);
//    }
//
//    @Transactional
//    public ProjectResponseDTO assignProjectToManager(Long userId, Long projectId, ProjectAssignRequest request) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Project not found in your department");
//        }
//
//        User manager = userRepository.findById(request.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Manager not found"));
//
//        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
//        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());
//
//        project.setAssignedManager(manager);
//        project.setStatus("ASSIGNED");
//        project.setDeadline(request.getDeadline());
//        if (request.getCost() != null) {
//            project.setCost(request.getCost());
//        }
//        ProjectDetails updated = projectRepository.save(project);
//
//        ProjectStatus statusRecord = new ProjectStatus();
//        statusRecord.setProject(project);
//        statusRecord.setStatus("ASSIGNED");
//        statusRecord.setDateTime(LocalDateTime.now());
//        statusRecord.setHandledBy(userRepository.findById(userId).get());
//        statusRecord.setAssignedTo(manager);
//        statusRecord.setDirectorNote(request.getDirectorNote());
//        statusRecord.setDeadline(request.getDeadline());
//        projectStatusRepository.save(statusRecord);
//
//        sendProjectAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
//        sendProjectAssignedToClientEmail(project, manager);
//
//        return convertToProjectDTO(updated);
//    }
//
//    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Project not found in your department");
//        }
//
//        return convertToProjectDTO(project);
//    }
//
//    public List<ProjectStatusDTO> getProjectHistory(Long userId, Long projectId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Project not found in your department");
//        }
//
//        return projectStatusRepository.findByProjectProjectId(projectId)
//                .stream().map(this::convertToStatusDTO).collect(Collectors.toList());
//    }
//
//    // ==================== TEAM MANAGEMENT ====================
//
//    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Project not found in your department");
//        }
//
//        List<TeamDetails> teams = teamRepository.findByProjectProjectId(projectId);
//
//        return teams.stream().map(team -> {
//            List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
//            List<TeamMemberDTO> memberDTOs = members.stream()
//                    .map(m -> TeamMemberDTO.builder()
//                            .memberId(m.getMember().getUserId())
//                            .memberName(m.getMember().getName())
//                            .memberEmail(m.getMember().getEmail())
//                            .photoUrl(m.getMember().getPhotoUrl())
//                            .role(m.getRole())
//                            .description(m.getDescription())
//                            .build())
//                    .collect(Collectors.toList());
//
//            return TeamResponseDTO.builder()
//                    .teamId(team.getTeamId())
//                    .teamName(team.getTeamName())
//                    .projectId(team.getProject().getProjectId())
//                    .projectName(team.getProject().getProjectName())
//                    .managerId(team.getManager().getUserId())
//                    .managerName(team.getManager().getName())
//                    .members(memberDTOs)
//                    .createdAt(team.getCreatedAt())
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    // ==================== TASK MANAGEMENT ====================
//
//    @Transactional
//    public AssignedTaskResponseDTO assignTaskToEmployee(Long userId, AssignTaskToEmployeeRequest request) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        User creator = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("Creator not found"));
//
//        User assignedTo = userRepository.findById(request.getAssignedToUserId())
//                .orElseThrow(() -> new RuntimeException("Assigned user not found"));
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(assignedTo.getUserId())
//                .orElseThrow(() -> new RuntimeException("Assigned user is not an employee"));
//
//        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
//            throw new RuntimeException("Employee not found in your department");
//        }
//
//        EmpTask task = new EmpTask();
//        task.setCreator(creator);
//        task.setAssignedTo(assignedTo);
//        task.setStatus("PENDING");
//        task.setDescription(request.getDescription());
//        task.setCreatedAt(LocalDateTime.now());
//        task.setUpdatedAt(LocalDateTime.now());
//
//        EmpTask savedTask = empTaskRepository.save(task);
//
//        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline());
//
//        return AssignedTaskResponseDTO.builder()
//                .taskId(savedTask.getEmpTaskId())
//                .taskDetails("Task Assigned")
//                .description(savedTask.getDescription())
//                .status(savedTask.getStatus())
//                .assignedToUserId(assignedTo.getUserId())
//                .assignedToUserName(assignedTo.getName())
//                .createdByUserId(creator.getUserId())
//                .createdByName(creator.getName())
//                .deadline(request.getDeadline())
//                .createdAt(savedTask.getCreatedAt())
//                .updatedAt(savedTask.getUpdatedAt())
//                .build();
//    }
//
//    public List<AssignedTaskResponseDTO> getTasksByDepartment(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
//        List<Long> employeeUserIds = employees.stream()
//                .map(e -> e.getUser().getUserId())
//                .collect(Collectors.toList());
//
//        List<EmpTask> tasks = empTaskRepository.findAll().stream()
//                .filter(t -> employeeUserIds.contains(t.getAssignedTo().getUserId()))
//                .collect(Collectors.toList());
//
//        return tasks.stream().map(task -> AssignedTaskResponseDTO.builder()
//                .taskId(task.getEmpTaskId())
//                .taskDetails("Task Assigned")
//                .description(task.getDescription())
//                .status(task.getStatus())
//                .assignedToUserId(task.getAssignedTo().getUserId())
//                .assignedToUserName(task.getAssignedTo().getName())
//                .createdByUserId(task.getCreator().getUserId())
//                .createdByName(task.getCreator().getName())
//                .createdAt(task.getCreatedAt())
//                .updatedAt(task.getUpdatedAt())
//                .build()).collect(Collectors.toList());
//    }
//
//    public AssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status) {
//        validateCoreDirector(userId);
//
//        EmpTask task = empTaskRepository.findById(taskId)
//                .orElseThrow(() -> new RuntimeException("Task not found"));
//
//        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
//            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
//        }
//
//        task.setStatus(status);
//        EmpTask updatedTask = empTaskRepository.save(task);
//
//        sendTaskStatusUpdateEmail(task);
//
//        return AssignedTaskResponseDTO.builder()
//                .taskId(updatedTask.getEmpTaskId())
//                .taskDetails("Task Assigned")
//                .description(updatedTask.getDescription())
//                .status(updatedTask.getStatus())
//                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
//                .assignedToUserName(updatedTask.getAssignedTo().getName())
//                .createdByUserId(updatedTask.getCreator().getUserId())
//                .createdByName(updatedTask.getCreator().getName())
//                .createdAt(updatedTask.getCreatedAt())
//                .updatedAt(updatedTask.getUpdatedAt())
//                .build();
//    }
//
//    // ==================== DASHBOARD ====================
//
//    public Map<String, Object> getDashboardStats(Long userId) {
//        validateCoreDirector(userId);
//        Long departmentId = getDirectorDepartmentId(userId);
//
//        List<ProjectDetails> projects = projectRepository.findByDepartmentDepartmentId(departmentId);
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
//        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
//        List<TeamDetails> teams = teamRepository.findAll().stream()
//                .filter(t -> t.getProject().getDepartment().getDepartmentId().equals(departmentId))
//                .collect(Collectors.toList());
//        List<EmpTask> tasks = empTaskRepository.findAll().stream()
//                .filter(t -> employees.stream().anyMatch(e -> e.getUser().getUserId().equals(t.getAssignedTo().getUserId())))
//                .collect(Collectors.toList());
//
//        Map<String, Object> stats = new HashMap<>();
//        stats.put("totalProjects", projects.size());
//        stats.put("pendingProjects", projects.stream().filter(p -> "PENDING".equals(p.getStatus())).count());
//        stats.put("inProgressProjects", projects.stream().filter(p -> "IN_PROGRESS".equals(p.getStatus())).count());
//        stats.put("completedProjects", projects.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count());
//        stats.put("totalConsultants", consultants.size());
//        stats.put("totalEmployees", employees.size());
//        stats.put("totalTeams", teams.size());
//        stats.put("totalTasks", tasks.size());
//        stats.put("pendingTasks", tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());
//
//        return stats;
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private ProjectResponseDTO convertToProjectDTO(ProjectDetails project) {
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
//    private void sendConsultantStatusEmail(ConsultantDetails consultant, UpdateConsultantStatusRequest request) {
//        try {
//            String subject = "📋 Consultant Status Update - " + consultant.getUser().getName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #4CAF50;'>📋 Status Update Notification</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Your consultant status has been updated to: <strong style='color: #2196F3;'>%s</strong></p>
//                        <div style='background: #fff; padding: 10px; border-radius: 8px; margin: 10px 0;'>
//                            <p><strong>📝 Note:</strong> %s</p>
//                            <p><strong>📅 Date & Time:</strong> %s</p>
//                        </div>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>Please login to your dashboard for more details.</p>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    consultant.getUser().getName(),
//                    request.getStatus(),
//                    request.getNote() != null ? request.getNote() : "No additional notes",
//                    LocalDateTime.now().format(formatter)
//            );
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
//        try {
//            String subject = "🎉 Congratulations! You are now an Employee - " + consultant.getUser().getName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2 style='text-align: center;'>🎉 Welcome to the Team!</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Congratulations! You have been converted from Consultant to Employee.</p>
//                        <div style='background: rgba(255,255,255,0.2); padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📋 Employment Details:</strong></p>
//                            <p>• Position: <strong>%s</strong></p>
//                            <p>• Department: <strong>%s</strong></p>
//                            <p>• Salary: <strong>₹%.2f</strong></p>
//                        </div>
//                        <p>Please login to your dashboard for more details.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    consultant.getUser().getName(),
//                    employee.getPositionName(),
//                    department.getDepartmentName(),
//                    employee.getSalary()
//            );
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendProjectStatusUpdateEmail(ProjectDetails project, String status, String note) {
//        try {
//            String statusIcon = status.equals("COMPLETED") ? "✅" : status.equals("IN_PROGRESS") ? "🔄" : "📋";
//            String statusColor = status.equals("COMPLETED") ? "#4CAF50" : status.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
//
//            String subject = statusIcon + " Project Status Update - " + project.getProjectName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: %s;'>%s Project Status Updated</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Project <strong>%s</strong> status has been updated.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>New Status:</strong> %s %s</p>
//                            <p><strong>Director Note:</strong> %s</p>
//                        </div>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    statusColor,
//                    statusIcon,
//                    project.getUploader().getName(),
//                    project.getProjectName(),
//                    statusIcon,
//                    status,
//                    note != null ? note : "No additional notes"
//            );
//            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendProjectAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
//        try {
//            String subject = "📋 New Project Assigned to You - " + project.getProjectName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2 style='text-align: center;'>📋 New Project Assigned</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>A new project has been assigned to you.</p>
//                        <div style='background: rgba(255,255,255,0.2); padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📁 Project:</strong> %s</p>
//                            <p><strong>📝 Description:</strong> %s</p>
//                            <p><strong>💰 Budget:</strong> %s</p>
//                            <p><strong>📅 Deadline:</strong> %s</p>
//                            <p><strong>📝 Director Note:</strong> %s</p>
//                        </div>
//                        <p>Please login to create teams and assign tasks.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    manager.getName(),
//                    project.getProjectName(),
//                    project.getProjectDescription() != null ? project.getProjectDescription() : "No description",
//                    project.getCost() != null ? project.getCost() : "Not specified",
//                    project.getDeadline() != null ? project.getDeadline().format(formatter) : "Not set",
//                    note != null ? note : "No additional notes"
//            );
//            emailService.sendEmail(manager.getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendProjectAssignedToClientEmail(ProjectDetails project, User manager) {
//        try {
//            String subject = "✅ Project Update - " + project.getProjectName() + " has been assigned";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #e8f5e9; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #4CAF50;'>✅ Project Update</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Your project <strong>%s</strong> has been assigned to a manager.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>👨‍💼 Assigned Manager:</strong> %s</p>
//                            <p><strong>📅 Deadline:</strong> %s</p>
//                        </div>
//                        <p>You can track the project progress from your dashboard.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
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
//    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline) {
//        try {
//            String subject = "📋 New Task Assigned to You - " + task.getDescription();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #fff3e0; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #FF9800;'>📋 New Task Assigned</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>You have been assigned a new task by <strong>%s</strong>.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📝 Task:</strong> %s</p>
//                            <p><strong>⏰ Deadline:</strong> %s</p>
//                        </div>
//                        <p>Please login to your dashboard to update status.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    assignedTo.getName(),
//                    creator.getName(),
//                    task.getDescription(),
//                    deadline != null ? deadline.format(formatter) : "Not set"
//            );
//            emailService.sendEmail(assignedTo.getEmail(), subject, html);
//        } catch (Exception e) {
//            System.out.println("Email error: " + e.getMessage());
//        }
//    }
//
//    private void sendTaskStatusUpdateEmail(EmpTask task) {
//        try {
//            User assignedTo = task.getAssignedTo();
//            User creator = task.getCreator();
//
//            String subject = "🔄 Task Status Updated - " + task.getDescription();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #e3f2fd; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #2196F3;'>🔄 Task Status Updated</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Task status has been updated by <strong>%s</strong>.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📝 Task:</strong> %s</p>
//                            <p><strong>📊 New Status:</strong> %s</p>
//                        </div>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    creator.getName(),
//                    assignedTo.getName(),
//                    task.getDescription(),
//                    task.getStatus()
//            );
//            emailService.sendEmail(creator.getEmail(), subject, html);
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
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.*;
////import com.softmax.shardaos.dto.output.*;
////import com.softmax.shardaos.models.*;
////import com.softmax.shardaos.repository.*;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import org.springframework.transaction.annotation.Transactional;
////
////import java.time.LocalDateTime;
////import java.time.format.DateTimeFormatter;
////import java.util.*;
////import java.util.stream.Collectors;
////
////@Service
////public class CoreDirectorService {
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmployeeDetailsRepository employeeDetailsRepository;
////
////    @Autowired
////    private ConsultantDetailsRepository consultantDetailsRepository;
////
////    @Autowired
////    private ConsultantStatusRepository consultantStatusRepository;
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
////
////    @Autowired
////    private ProjectStatusRepository projectStatusRepository;
////
////    @Autowired
////    private DepartmentRepository departmentRepository;
////
////    @Autowired
////    private TeamDetailsRepository teamRepository;
////
////    @Autowired
////    private TeamMembersRepository teamMembersRepository;
////
////    @Autowired
////    private EmpTaskRepository empTaskRepository;
////
////    @Autowired
////    private ConsultantReviewRepository consultantReviewRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////
////    // ==================== VALIDATION ====================
////
////    public boolean isCoreDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////
////        if (!isEmp || !isDirector) return false;
////
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
////        if (employee == null) return false;
////
////        return employee.getDepartmentRef() != null;
////    }
////
////    private void validateCoreDirector(Long userId) {
////        if (!isCoreDirector(userId)) {
////            throw new RuntimeException("Access denied: Only Core Director can access this resource");
////        }
////    }
////
////    private Long getDirectorDepartmentId(Long userId) {
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId)
////                .orElseThrow(() -> new RuntimeException("Employee not found"));
////        if (employee.getDepartmentRef() == null) {
////            throw new RuntimeException("Director not assigned to any department");
////        }
////        return employee.getDepartmentRef().getDepartmentId();
////    }
////
////    // ==================== CONSULTANT MANAGEMENT ====================
////
////    public List<ConsultantListResponseDTO> getMyDepartmentConsultants(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
////
////        return consultants.stream().map(c -> {
////            String latestStatus = consultantStatusRepository.findLatestStatus(c.getConsultantId())
////                    .map(ConsultantStatus::getStatus).orElse("PENDING");
////
////            String isApproved = userTypeRepository.findByUserUserIdAndPtype(c.getUser().getUserId(), "CONSULTANT")
////                    .map(UserType::getIsapproved).orElse("PENDING");
////
////            return ConsultantListResponseDTO.builder()
////                    .consultantId(c.getConsultantId())
////                    .userId(c.getUser().getUserId())
////                    .name(c.getUser().getName())
////                    .email(c.getUser().getEmail())
////                    .photoUrl(c.getUser().getPhotoUrl())
////                    .skill(c.getSkill())
////                    .experience(c.getExperience())
////                    .currentStatus(latestStatus)
////                    .departmentId(departmentId)
////                    .departmentName(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : null)
////                    .isApproved(isApproved)
////                    .build();
////        }).collect(Collectors.toList());
////    }
////
////    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Consultant not found in your department");
////        }
////
////        String latestStatus = consultantStatusRepository.findLatestStatus(consultantId)
////                .map(ConsultantStatus::getStatus).orElse("PENDING");
////
////        String isApproved = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
////                .map(UserType::getIsapproved).orElse("PENDING");
////
////        return ConsultantListResponseDTO.builder()
////                .consultantId(consultant.getConsultantId())
////                .userId(consultant.getUser().getUserId())
////                .name(consultant.getUser().getName())
////                .email(consultant.getUser().getEmail())
////                .photoUrl(consultant.getUser().getPhotoUrl())
////                .skill(consultant.getSkill())
////                .experience(consultant.getExperience())
////                .currentStatus(latestStatus)
////                .departmentId(departmentId)
////                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
////                .isApproved(isApproved)
////                .build();
////    }
////
////    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Consultant not found in your department");
////        }
////
////        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
////
////        return statusList.stream().map(status -> ConsultantStatusHistoryDTO.builder()
////                .statusId(status.getStatusId())
////                .status(status.getStatus())
////                .note(status.getNote())
////                .dateTime(status.getDateTime())
////                .build()).collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Consultant not found in your department");
////        }
////
////        ConsultantStatus status = new ConsultantStatus();
////        status.setConsultant(consultant);
////        status.setStatus(request.getStatus());
////        status.setNote(request.getNote());
////        status.setDateTime(LocalDateTime.now());
////
////        ConsultantStatus savedStatus = consultantStatusRepository.save(status);
////
////        if ("APPROVED".equals(request.getStatus())) {
////            userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT")
////                    .ifPresent(ut -> {
////                        ut.setIsapproved("APPROVED");
////                        userTypeRepository.save(ut);
////                    });
////        }
////
////        sendConsultantStatusEmail(consultant, request);
////
////        return ConsultantStatusResponseDTO.builder()
////                .statusId(savedStatus.getStatusId())
////                .consultantId(consultantId)
////                .consultantName(consultant.getUser().getName())
////                .status(savedStatus.getStatus())
////                .note(savedStatus.getNote())
////                .dateTime(savedStatus.getDateTime())
////                .build();
////    }
////
////    @Transactional
////    public String convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ConsultantDetails consultant = consultantDetailsRepository.findById(consultantId)
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        if (consultant.getDepartment() == null || !consultant.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Consultant not found in your department");
////        }
////
////        User user = consultant.getUser();
////
////        if (employeeDetailsRepository.findByUserUserId(user.getUserId()).isPresent()) {
////            throw new RuntimeException("User is already an employee");
////        }
////
////        Department department = departmentRepository.findById(request.getDepartmentId())
////                .orElseThrow(() -> new RuntimeException("Department not found"));
////
////        EmployeeDetails employee = new EmployeeDetails();
////        employee.setUser(user);
////        employee.setIsDirectorVerified(true);
////        employee.setPositionName(request.getPositionName());
////        employee.setSalary(request.getSalary());
////        employee.setSkillSet(request.getSkillSet());
////        employee.setDepartmentRef(department);
////        employee.setDepartment(department.getDepartmentName());
////
////        employeeDetailsRepository.save(employee);
////
////        UserType empRole = new UserType();
////        empRole.setUser(user);
////        empRole.setPtype("EMP");
////        empRole.setStype(request.getPositionName());
////        empRole.setIsapproved("APPROVED");
////        empRole.setCreatedAt(LocalDateTime.now());
////        userTypeRepository.save(empRole);
////
////        UpdateConsultantStatusRequest statusRequest = new UpdateConsultantStatusRequest();
////        statusRequest.setStatus("CONVERTED_TO_EMPLOYEE");
////        statusRequest.setNote("Converted to employee as " + request.getPositionName());
////        updateConsultantStatus(userId, consultantId, statusRequest);
////
////        sendConversionEmail(consultant, employee, department);
////
////        return "Consultant converted to employee successfully. Position: " + request.getPositionName();
////    }
////
////    // ==================== EMPLOYEE MANAGEMENT (WITH NAMES) ====================
////    public List<EmployeeDTO> getMyDepartmentEmployees(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
////
////        return employees.stream()
////                .filter(e -> {
////                    List<UserType> userTypes = userTypeRepository.findByUserUserId(e.getUser().getUserId());
////                    return userTypes.stream().anyMatch(ut -> "MANAGER".equals(ut.getStype()));
////                })
////                .map(e -> {
////                    User u = e.getUser();
////                    return EmployeeDTO.builder()
////                            .employeeId(e.getEmployeeId())     // ✅ Employee ID
////                            .userId(u.getUserId())             // ✅ User ID
////                            .name(u.getName())                 // ✅ Name
////                            .email(u.getEmail())               // ✅ Email
////                            .photoUrl(u.getPhotoUrl())         // ✅ Photo
////                            .phoneNumber(u.getPhoneNumber())   // ✅ Phone
////                            .address(u.getAddress())           // ✅ Address
////                            .gender(u.getGender())             // ✅ Gender
////                            .isDirectorVerified(e.getIsDirectorVerified())
////                            .positionName(e.getPositionName())
////                            .salary(e.getSalary())
////                            .department(e.getDepartment())
////                            .skillSet(e.getSkillSet())
////                            .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
////                            .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
////                            .build();
////                })
////                .collect(Collectors.toList());
////    }
////
////    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        EmployeeDetails employee = employeeDetailsRepository.findById(employeeId)
////                .orElseThrow(() -> new RuntimeException("Employee not found"));
////
////        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Employee not found in your department");
////        }
////
////        User u = employee.getUser();
////        return EmployeeDTO.builder()
////                .employeeId(employee.getEmployeeId())
////                .userId(u.getUserId())
////                .name(u.getName())
////                .email(u.getEmail())
////                .photoUrl(u.getPhotoUrl())
////                .phoneNumber(u.getPhoneNumber())
////                .address(u.getAddress())
////                .gender(u.getGender())
////                .isDirectorVerified(employee.getIsDirectorVerified())
////                .positionName(employee.getPositionName())
////                .salary(employee.getSalary())
////                .department(employee.getDepartment())
////                .skillSet(employee.getSkillSet())
////                .departmentId(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentId() : null)
////                .departmentName(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : null)
////                .build();
////    }
////
////    // ==================== PROJECT MANAGEMENT ====================
////
////    public List<ProjectResponseDTO> getMyDepartmentProjects(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        return projectRepository.findByDepartmentDepartmentId(departmentId)
////                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
////    }
////
////    public List<ProjectResponseDTO> getPendingProjects(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        return projectRepository.findByDepartmentDepartmentIdAndStatus(departmentId, "PENDING")
////                .stream().map(this::convertToProjectDTO).collect(Collectors.toList());
////    }
////
////    @Transactional
////    public ProjectResponseDTO approveProject(Long userId, Long projectId, String status, String note) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Project not found in your department");
////        }
////
////        project.setStatus(status);
////        ProjectDetails updated = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus(status);
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setHandledBy(userRepository.findById(userId).get());
////        statusRecord.setDirectorNote(note);
////        projectStatusRepository.save(statusRecord);
////
////        sendProjectStatusUpdateEmail(project, status, note);
////        return convertToProjectDTO(updated);
////    }
////
////    @Transactional
////    public ProjectResponseDTO assignProjectToManager(Long userId, Long projectId, ProjectAssignRequest request) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Project not found in your department");
////        }
////
////        User manager = userRepository.findById(request.getManagerId())
////                .orElseThrow(() -> new RuntimeException("Manager not found"));
////
////        // Get manager details for email
////        EmployeeDetails managerEmp = employeeDetailsRepository.findByUserUserId(manager.getUserId()).orElse(null);
////        Double managerRating = consultantReviewRepository.getAverageRatingByUserId(manager.getUserId());
////
////        project.setAssignedManager(manager);
////        project.setStatus("ASSIGNED");
////        project.setDeadline(request.getDeadline());
////        if (request.getCost() != null) {
////            project.setCost(request.getCost());
////        }
////        ProjectDetails updated = projectRepository.save(project);
////
////        ProjectStatus statusRecord = new ProjectStatus();
////        statusRecord.setProject(project);
////        statusRecord.setStatus("ASSIGNED");
////        statusRecord.setDateTime(LocalDateTime.now());
////        statusRecord.setHandledBy(userRepository.findById(userId).get());
////        statusRecord.setAssignedTo(manager);
////        statusRecord.setDirectorNote(request.getDirectorNote());
////        statusRecord.setDeadline(request.getDeadline());
////        projectStatusRepository.save(statusRecord);
////
////        sendProjectAssignmentEmail(project, manager, request.getDirectorNote(), managerEmp, managerRating);
////        sendProjectAssignedToClientEmail(project, manager);
////
////        return convertToProjectDTO(updated);
////    }
////
////    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Project not found in your department");
////        }
////
////        return convertToProjectDTO(project);
////    }
////
////    public List<ProjectStatusDTO> getProjectHistory(Long userId, Long projectId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Project not found in your department");
////        }
////
////        return projectStatusRepository.findByProjectProjectId(projectId)
////                .stream().map(this::convertToStatusDTO).collect(Collectors.toList());
////    }
////
////    // ==================== TEAM MANAGEMENT ====================
////
////    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        if (project.getDepartment() == null || !project.getDepartment().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Project not found in your department");
////        }
////
////        List<TeamDetails> teams = teamRepository.findByProjectProjectId(projectId);
////
////        return teams.stream().map(team -> {
////            List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
////            List<TeamMemberDTO> memberDTOs = members.stream()
////                    .map(m -> TeamMemberDTO.builder()
////                            .memberId(m.getMember().getUserId())
////                            .memberName(m.getMember().getName())
////                            .memberEmail(m.getMember().getEmail())
////                            .photoUrl(m.getMember().getPhotoUrl())
////                            .role(m.getRole())
////                            .description(m.getDescription())
////                            .build())
////                    .collect(Collectors.toList());
////
////            return TeamResponseDTO.builder()
////                    .teamId(team.getTeamId())
////                    .teamName(team.getTeamName())
////                    .projectId(team.getProject().getProjectId())
////                    .projectName(team.getProject().getProjectName())
////                    .managerId(team.getManager().getUserId())
////                    .managerName(team.getManager().getName())
////                    .members(memberDTOs)
////                    .createdAt(team.getCreatedAt())
////                    .build();
////        }).collect(Collectors.toList());
////    }
////
////    // ==================== TASK MANAGEMENT ====================
////
////    @Transactional
////    public AssignedTaskResponseDTO assignTaskToEmployee(Long userId, AssignTaskToEmployeeRequest request) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        User creator = userRepository.findById(userId)
////                .orElseThrow(() -> new RuntimeException("Creator not found"));
////
////        User assignedTo = userRepository.findById(request.getAssignedToUserId())
////                .orElseThrow(() -> new RuntimeException("Assigned user not found"));
////
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(assignedTo.getUserId())
////                .orElseThrow(() -> new RuntimeException("Assigned user is not an employee"));
////
////        if (employee.getDepartmentRef() == null || !employee.getDepartmentRef().getDepartmentId().equals(departmentId)) {
////            throw new RuntimeException("Employee not found in your department");
////        }
////
////        EmpTask task = new EmpTask();
////        task.setCreator(creator);
////        task.setAssignedTo(assignedTo);
////        task.setStatus("PENDING");
////        task.setDescription(request.getDescription());
////        task.setCreatedAt(LocalDateTime.now());
////        task.setUpdatedAt(LocalDateTime.now());
////
////        EmpTask savedTask = empTaskRepository.save(task);
////
////        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline());
////
////        return AssignedTaskResponseDTO.builder()
////                .taskId(savedTask.getEmpTaskId())
////                .taskDetails("Task Assigned")
////                .description(savedTask.getDescription())
////                .status(savedTask.getStatus())
////                .assignedToUserId(assignedTo.getUserId())
////                .assignedToUserName(assignedTo.getName())
////                .createdByUserId(creator.getUserId())
////                .createdByName(creator.getName())
////                .deadline(request.getDeadline())
////                .createdAt(savedTask.getCreatedAt())
////                .updatedAt(savedTask.getUpdatedAt())
////                .build();
////    }
////
////    public List<AssignedTaskResponseDTO> getTasksByDepartment(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
////        List<Long> employeeUserIds = employees.stream()
////                .map(e -> e.getUser().getUserId())
////                .collect(Collectors.toList());
////
////        List<EmpTask> tasks = empTaskRepository.findAll().stream()
////                .filter(t -> employeeUserIds.contains(t.getAssignedTo().getUserId()))
////                .collect(Collectors.toList());
////
////        return tasks.stream().map(task -> AssignedTaskResponseDTO.builder()
////                .taskId(task.getEmpTaskId())
////                .taskDetails("Task Assigned")
////                .description(task.getDescription())
////                .status(task.getStatus())
////                .assignedToUserId(task.getAssignedTo().getUserId())
////                .assignedToUserName(task.getAssignedTo().getName())
////                .createdByUserId(task.getCreator().getUserId())
////                .createdByName(task.getCreator().getName())
////                .createdAt(task.getCreatedAt())
////                .updatedAt(task.getUpdatedAt())
////                .build()).collect(Collectors.toList());
////    }
////
////    public AssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status) {
////        validateCoreDirector(userId);
////
////        EmpTask task = empTaskRepository.findById(taskId)
////                .orElseThrow(() -> new RuntimeException("Task not found"));
////
////        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
////            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
////        }
////
////        task.setStatus(status);
////        EmpTask updatedTask = empTaskRepository.save(task);
////
////        sendTaskStatusUpdateEmail(task);
////
////        return AssignedTaskResponseDTO.builder()
////                .taskId(updatedTask.getEmpTaskId())
////                .taskDetails("Task Assigned")
////                .description(updatedTask.getDescription())
////                .status(updatedTask.getStatus())
////                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
////                .assignedToUserName(updatedTask.getAssignedTo().getName())
////                .createdByUserId(updatedTask.getCreator().getUserId())
////                .createdByName(updatedTask.getCreator().getName())
////                .createdAt(updatedTask.getCreatedAt())
////                .updatedAt(updatedTask.getUpdatedAt())
////                .build();
////    }
////
////    // ==================== DASHBOARD ====================
////
////    public Map<String, Object> getDashboardStats(Long userId) {
////        validateCoreDirector(userId);
////        Long departmentId = getDirectorDepartmentId(userId);
////
////        List<ProjectDetails> projects = projectRepository.findByDepartmentDepartmentId(departmentId);
////        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
////        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
////        List<TeamDetails> teams = teamRepository.findAll().stream()
////                .filter(t -> t.getProject().getDepartment().getDepartmentId().equals(departmentId))
////                .collect(Collectors.toList());
////        List<EmpTask> tasks = empTaskRepository.findAll().stream()
////                .filter(t -> employees.stream().anyMatch(e -> e.getUser().getUserId().equals(t.getAssignedTo().getUserId())))
////                .collect(Collectors.toList());
////
////        Map<String, Object> stats = new HashMap<>();
////        stats.put("totalProjects", projects.size());
////        stats.put("pendingProjects", projects.stream().filter(p -> "PENDING".equals(p.getStatus())).count());
////        stats.put("inProgressProjects", projects.stream().filter(p -> "IN_PROGRESS".equals(p.getStatus())).count());
////        stats.put("completedProjects", projects.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count());
////        stats.put("totalConsultants", consultants.size());
////        stats.put("totalEmployees", employees.size());
////        stats.put("totalTeams", teams.size());
////        stats.put("totalTasks", tasks.size());
////        stats.put("pendingTasks", tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());
////
////        return stats;
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    private ProjectResponseDTO convertToProjectDTO(ProjectDetails project) {
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
////    private void sendConsultantStatusEmail(ConsultantDetails consultant, UpdateConsultantStatusRequest request) {
////        try {
////            String subject = "📋 Consultant Status Update - " + consultant.getUser().getName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #4CAF50;'>📋 Status Update Notification</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Your consultant status has been updated to: <strong style='color: #2196F3;'>%s</strong></p>
////                        <div style='background: #fff; padding: 10px; border-radius: 8px; margin: 10px 0;'>
////                            <p><strong>📝 Note:</strong> %s</p>
////                            <p><strong>📅 Date & Time:</strong> %s</p>
////                        </div>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>Please login to your dashboard for more details.</p>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    consultant.getUser().getName(),
////                    request.getStatus(),
////                    request.getNote() != null ? request.getNote() : "No additional notes",
////                    LocalDateTime.now().format(formatter)
////            );
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
////        try {
////            String subject = "🎉 Congratulations! You are now an Employee - " + consultant.getUser().getName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; padding: 20px; color: white;'>
////                        <h2 style='text-align: center;'>🎉 Welcome to the Team!</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Congratulations! You have been converted from Consultant to Employee.</p>
////                        <div style='background: rgba(255,255,255,0.2); padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📋 Employment Details:</strong></p>
////                            <p>• Position: <strong>%s</strong></p>
////                            <p>• Department: <strong>%s</strong></p>
////                            <p>• Salary: <strong>₹%.2f</strong></p>
////                        </div>
////                        <p>Please login to your dashboard for more details.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    consultant.getUser().getName(),
////                    employee.getPositionName(),
////                    department.getDepartmentName(),
////                    employee.getSalary()
////            );
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendProjectStatusUpdateEmail(ProjectDetails project, String status, String note) {
////        try {
////            String statusIcon = status.equals("COMPLETED") ? "✅" : status.equals("IN_PROGRESS") ? "🔄" : "📋";
////            String statusColor = status.equals("COMPLETED") ? "#4CAF50" : status.equals("IN_PROGRESS") ? "#2196F3" : "#FF9800";
////
////            String subject = statusIcon + " Project Status Update - " + project.getProjectName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: %s;'>%s Project Status Updated</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Project <strong>%s</strong> status has been updated.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>New Status:</strong> %s %s</p>
////                            <p><strong>Director Note:</strong> %s</p>
////                        </div>
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
////                    status,
////                    note != null ? note : "No additional notes"
////            );
////            emailService.sendEmail(project.getUploader().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendProjectAssignmentEmail(ProjectDetails project, User manager, String note, EmployeeDetails managerEmp, Double managerRating) {
////        try {
////            String subject = "📋 New Project Assigned to You - " + project.getProjectName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%); border-radius: 10px; padding: 20px; color: white;'>
////                        <h2 style='text-align: center;'>📋 New Project Assigned</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>A new project has been assigned to you.</p>
////                        <div style='background: rgba(255,255,255,0.2); padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📁 Project:</strong> %s</p>
////                            <p><strong>📝 Description:</strong> %s</p>
////                            <p><strong>💰 Budget:</strong> %s</p>
////                            <p><strong>📅 Deadline:</strong> %s</p>
////                            <p><strong>📝 Director Note:</strong> %s</p>
////                        </div>
////                        <p>Please login to create teams and assign tasks.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='font-size: 12px;'>ShardaOS - Project Management System</p>
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
////            String subject = "✅ Project Update - " + project.getProjectName() + " has been assigned";
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #e8f5e9; border-radius: 10px; padding: 20px;'>
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
////    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline) {
////        try {
////            String subject = "📋 New Task Assigned to You - " + task.getDescription();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #fff3e0; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #FF9800;'>📋 New Task Assigned</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>You have been assigned a new task by <strong>%s</strong>.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📝 Task:</strong> %s</p>
////                            <p><strong>⏰ Deadline:</strong> %s</p>
////                        </div>
////                        <p>Please login to your dashboard to update status.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    assignedTo.getName(),
////                    creator.getName(),
////                    task.getDescription(),
////                    deadline != null ? deadline.format(formatter) : "Not set"
////            );
////            emailService.sendEmail(assignedTo.getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendTaskStatusUpdateEmail(EmpTask task) {
////        try {
////            User assignedTo = task.getAssignedTo();
////            User creator = task.getCreator();
////
////            String subject = "🔄 Task Status Updated - " + task.getDescription();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #e3f2fd; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #2196F3;'>🔄 Task Status Updated</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Task status has been updated by <strong>%s</strong>.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📝 Task:</strong> %s</p>
////                            <p><strong>📊 New Status:</strong> %s</p>
////                        </div>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Project Management System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    creator.getName(),
////                    assignedTo.getName(),
////                    task.getDescription(),
////                    task.getStatus()
////            );
////            emailService.sendEmail(creator.getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////}