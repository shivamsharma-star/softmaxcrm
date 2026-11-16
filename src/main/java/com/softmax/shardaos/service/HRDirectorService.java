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
public class HRDirectorService {

    @Autowired private UserRepository userRepository;
    @Autowired private UserTypeRepository userTypeRepository;
    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
    @Autowired private ConsultantStatusRepository consultantStatusRepository;
    @Autowired private ConsultantReviewRepository consultantReviewRepository;
    @Autowired private ProjectDetailsRepository projectRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TeamDetailsRepository teamRepository;
    @Autowired private TeamMembersRepository teamMembersRepository;
    @Autowired private EmpTaskRepository empTaskRepository;
    @Autowired private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");
    private final DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    // ==================== VALIDATION ====================

    public boolean isHRDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
        if (!employeeOpt.isPresent()) return false;
        EmployeeDetails employee = employeeOpt.get();
        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 2L;
    }

    public boolean isHeadDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
        if (!employeeOpt.isPresent()) return false;
        EmployeeDetails employee = employeeOpt.get();
        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 1L;
    }

    private void validateAccess(Long userId) {
        if (!isHRDirector(userId) && !isHeadDirector(userId)) {
            throw new RuntimeException("Access denied: Only HR Director or Head Director can access this resource");
        }
    }

    private String getLatestConsultantStatus(Long consultantId) {
        List<ConsultantStatus> statuses = consultantStatusRepository.findByConsultantConsultantId(consultantId);
        if (statuses == null || statuses.isEmpty()) return "PENDING";
        return statuses.get(statuses.size() - 1).getStatus();
    }

    // ==================== CONSULTANT MANAGEMENT ====================

    public List<ConsultantListResponseDTO> getAllConsultants(Long userId) {
        validateAccess(userId);
        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
        List<ConsultantListResponseDTO> result = new ArrayList<>();
        for (ConsultantDetails consultant : consultants) {
            String latestStatus = getLatestConsultantStatus(consultant.getConsultantId());
            String isApproved = "PENDING";
            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
            if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
            result.add(ConsultantListResponseDTO.builder()
                    .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
                    .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
                    .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
                    .experience(consultant.getExperience()).currentStatus(latestStatus)
                    .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
                    .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
                    .isApproved(isApproved).build());
        }
        return result;
    }

    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
        validateAccess(userId);
        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
        ConsultantDetails consultant = consultantOpt.get();
        String latestStatus = getLatestConsultantStatus(consultantId);
        String isApproved = "PENDING";
        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
        if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
        return ConsultantListResponseDTO.builder()
                .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
                .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
                .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
                .experience(consultant.getExperience()).currentStatus(latestStatus)
                .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
                .isApproved(isApproved).build();
    }

    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
        validateAccess(userId);
        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
        List<ConsultantStatusHistoryDTO> result = new ArrayList<>();
        for (ConsultantStatus status : statusList) {
            result.add(ConsultantStatusHistoryDTO.builder()
                    .statusId(status.getStatusId()).status(status.getStatus())
                    .note(status.getNote()).dateTime(status.getDateTime()).build());
        }
        return result;
    }

    @Transactional
    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
        validateAccess(userId);
        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
        ConsultantDetails consultant = consultantOpt.get();

        ConsultantStatus status = new ConsultantStatus();
        status.setConsultant(consultant);
        status.setStatus(request.getStatus());
        status.setNote(request.getNote());
        status.setDateTime(LocalDateTime.now());
        ConsultantStatus savedStatus = consultantStatusRepository.save(status);

        if ("APPROVED".equals(request.getStatus())) {
            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
            if (userTypeOpt.isPresent()) {
                UserType ut = userTypeOpt.get();
                ut.setIsapproved("APPROVED");
                userTypeRepository.save(ut);
            }
        }

        sendConsultantStatusChangeEmail(consultant, request.getStatus(), request.getNote());

        return ConsultantStatusResponseDTO.builder()
                .statusId(savedStatus.getStatusId()).consultantId(consultantId)
                .consultantName(consultant.getUser().getName()).status(savedStatus.getStatus())
                .note(savedStatus.getNote()).dateTime(savedStatus.getDateTime()).build();
    }

    @Transactional
    public ConsultantListResponseDTO assignDepartmentToConsultant(Long userId, Long consultantId, AssignDepartmentRequest request) {
        validateAccess(userId);
        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
        if (!departmentOpt.isPresent()) throw new RuntimeException("Department not found");
        ConsultantDetails consultant = consultantOpt.get();
        Department department = departmentOpt.get();
        String oldDepartment = consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : "Not Assigned";
        consultant.setDepartment(department);
        consultantDetailsRepository.save(consultant);
        sendDepartmentAssignmentEmail(consultant, department, oldDepartment);
        String latestStatus = getLatestConsultantStatus(consultantId);
        String isApproved = "PENDING";
        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
        if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
        return ConsultantListResponseDTO.builder()
                .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
                .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
                .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
                .experience(consultant.getExperience()).currentStatus(latestStatus)
                .departmentId(department.getDepartmentId()).departmentName(department.getDepartmentName())
                .isApproved(isApproved).build();
    }

    @Transactional
    public EmployeeDTO convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
        validateAccess(userId);
        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
        ConsultantDetails consultant = consultantOpt.get();
        User user = consultant.getUser();
        Optional<EmployeeDetails> existingEmployee = employeeDetailsRepository.findByUserUserId(user.getUserId());
        if (existingEmployee.isPresent()) throw new RuntimeException("User is already an employee");
        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
        if (!departmentOpt.isPresent()) throw new RuntimeException("Department not found");
        Department department = departmentOpt.get();
        EmployeeDetails employee = new EmployeeDetails();
        employee.setUser(user);
        employee.setIsDirectorVerified(true);
        employee.setPositionName(request.getPositionName());
        employee.setSalary(request.getSalary());
        employee.setSkillSet(request.getSkillSet());
        employee.setDepartmentRef(department);
        employee.setDepartment(department.getDepartmentName());
        employeeDetailsRepository.save(employee);
        String stype = request.getStype() != null && !request.getStype().isEmpty() ? request.getStype() : null;
        UserType empRole = new UserType();
        empRole.setUser(user);
        empRole.setPtype("EMP");
        empRole.setStype(stype);
        empRole.setIsapproved("APPROVED");
        empRole.setCreatedAt(LocalDateTime.now());
        userTypeRepository.save(empRole);
        ConsultantStatus status = new ConsultantStatus();
        status.setConsultant(consultant);
        status.setStatus("CONVERTED_TO_EMPLOYEE");
        status.setNote("Converted to employee as " + request.getPositionName() + (stype != null ? " (" + stype + ")" : "") + " in " + department.getDepartmentName());
        status.setDateTime(LocalDateTime.now());
        consultantStatusRepository.save(status);
        sendConversionEmail(consultant, employee, department, stype);
        return EmployeeDTO.builder()
                .employeeId(employee.getEmployeeId()).userId(user.getUserId()).name(user.getName())
                .email(user.getEmail()).photoUrl(user.getPhotoUrl()).phoneNumber(user.getPhoneNumber())
                .address(user.getAddress()).gender(user.getGender())
                .isDirectorVerified(employee.getIsDirectorVerified()).positionName(employee.getPositionName())
                .salary(employee.getSalary()).department(employee.getDepartment()).skillSet(employee.getSkillSet())
                .departmentId(department.getDepartmentId()).departmentName(department.getDepartmentName()).build();
    }

    public List<EmployeeDTO> getAllEmployees(Long userId) {
        validateAccess(userId);
        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
        List<EmployeeDTO> result = new ArrayList<>();
        for (EmployeeDetails e : employees) {
            User u = e.getUser();
            result.add(EmployeeDTO.builder()
                    .employeeId(e.getEmployeeId()).userId(u.getUserId()).name(u.getName())
                    .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
                    .address(u.getAddress()).gender(u.getGender())
                    .isDirectorVerified(e.getIsDirectorVerified()).positionName(e.getPositionName())
                    .salary(e.getSalary()).department(e.getDepartment()).skillSet(e.getSkillSet())
                    .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
                    .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null).build());
        }
        return result;
    }

    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
        validateAccess(userId);
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
        if (!employeeOpt.isPresent()) throw new RuntimeException("Employee not found");
        EmployeeDetails e = employeeOpt.get();
        User u = e.getUser();
        return EmployeeDTO.builder()
                .employeeId(e.getEmployeeId()).userId(u.getUserId()).name(u.getName())
                .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
                .address(u.getAddress()).gender(u.getGender())
                .isDirectorVerified(e.getIsDirectorVerified()).positionName(e.getPositionName())
                .salary(e.getSalary()).department(e.getDepartment()).skillSet(e.getSkillSet())
                .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
                .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null).build();
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long userId, Long employeeId, UpdateEmployeeRequest request) {
        validateAccess(userId);
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
        if (!employeeOpt.isPresent()) throw new RuntimeException("Employee not found");
        EmployeeDetails employee = employeeOpt.get();
        if (request.getPositionName() != null) employee.setPositionName(request.getPositionName());
        if (request.getSalary() != null) employee.setSalary(request.getSalary());
        if (request.getSkillSet() != null) employee.setSkillSet(request.getSkillSet());
        if (request.getDepartmentId() != null) {
            Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
            if (departmentOpt.isPresent()) {
                Department department = departmentOpt.get();
                employee.setDepartmentRef(department);
                employee.setDepartment(department.getDepartmentName());
            }
        }
        employeeDetailsRepository.save(employee);
        sendEmployeeUpdateEmail(employee);
        User u = employee.getUser();
        return EmployeeDTO.builder()
                .employeeId(employee.getEmployeeId()).userId(u.getUserId()).name(u.getName())
                .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
                .address(u.getAddress()).gender(u.getGender())
                .isDirectorVerified(employee.getIsDirectorVerified()).positionName(employee.getPositionName())
                .salary(employee.getSalary()).department(employee.getDepartment()).skillSet(employee.getSkillSet())
                .departmentId(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentId() : null)
                .departmentName(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : null).build();
    }

    public List<ProjectResponseDTO> getAllProjects(Long userId) {
        validateAccess(userId);
        List<ProjectDetails> projects = projectRepository.findAll();
        List<ProjectResponseDTO> result = new ArrayList<>();
        for (ProjectDetails p : projects) result.add(convertToProjectDTO(p));
        return result;
    }

    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
        validateAccess(userId);
        Optional<ProjectDetails> projectOpt = projectRepository.findById(projectId);
        if (!projectOpt.isPresent()) throw new RuntimeException("Project not found");
        return convertToProjectDTO(projectOpt.get());
    }

    @Transactional
    public HRAssignedTaskResponseDTO assignTaskToAnyEmployee(Long userId, AssignTaskToAnyEmployeeRequest request) {
        validateAccess(userId);
        Optional<User> creatorOpt = userRepository.findById(userId);
        if (!creatorOpt.isPresent()) throw new RuntimeException("Creator not found");
        Optional<User> assignedToOpt = userRepository.findById(request.getAssignedToUserId());
        if (!assignedToOpt.isPresent()) throw new RuntimeException("Assigned user not found");
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(request.getAssignedToUserId());
        if (!employeeOpt.isPresent()) throw new RuntimeException("Assigned user is not an employee");
        User creator = creatorOpt.get();
        User assignedTo = assignedToOpt.get();
        EmployeeDetails employee = employeeOpt.get();
        String departmentName = employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : "Unknown";
        EmpTask task = new EmpTask();
        task.setCreator(creator);
        task.setAssignedTo(assignedTo);
        task.setStatus("PENDING");
        task.setDescription(request.getDescription());
        task.setDeadline(request.getDeadline());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        EmpTask savedTask = empTaskRepository.save(task);
        String role = isHRDirector(userId) ? "HR Director" : "Head Director";
        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline(), departmentName, role);
        return HRAssignedTaskResponseDTO.builder()
                .taskId(savedTask.getEmpTaskId()).description(savedTask.getDescription())
                .status(savedTask.getStatus()).assignedToUserId(assignedTo.getUserId())
                .assignedToUserName(assignedTo.getName()).assignedToDepartment(departmentName)
                .createdByUserId(creator.getUserId()).createdByName(creator.getName())
                .deadline(request.getDeadline()).createdAt(savedTask.getCreatedAt())
                .updatedAt(savedTask.getUpdatedAt()).build();
    }

    public List<HRAssignedTaskResponseDTO> getAllDepartmentTasks(Long userId) {
        validateAccess(userId);
        List<EmpTask> tasks = empTaskRepository.findAll();
        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
        for (EmpTask task : tasks) {
            String departmentName = "Unknown";
            Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
            if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
                departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
            }
            result.add(HRAssignedTaskResponseDTO.builder()
                    .taskId(task.getEmpTaskId()).description(task.getDescription())
                    .status(task.getStatus()).assignedToUserId(task.getAssignedTo().getUserId())
                    .assignedToUserName(task.getAssignedTo().getName()).assignedToDepartment(departmentName)
                    .createdByUserId(task.getCreator().getUserId()).createdByName(task.getCreator().getName())
                    .deadline(task.getDeadline()).createdAt(task.getCreatedAt())
                    .updatedAt(task.getUpdatedAt()).build());
        }
        return result;
    }

    public List<HRAssignedTaskResponseDTO> getTasksByEmployee(Long userId, Long employeeUserId) {
        validateAccess(userId);
        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(employeeUserId);
        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
        String departmentName = "Unknown";
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(employeeUserId);
        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
        }
        final String finalDeptName = departmentName;
        for (EmpTask task : tasks) {
            result.add(HRAssignedTaskResponseDTO.builder()
                    .taskId(task.getEmpTaskId()).description(task.getDescription())
                    .status(task.getStatus()).assignedToUserId(task.getAssignedTo().getUserId())
                    .assignedToUserName(task.getAssignedTo().getName()).assignedToDepartment(finalDeptName)
                    .createdByUserId(task.getCreator().getUserId()).createdByName(task.getCreator().getName())
                    .deadline(task.getDeadline()).createdAt(task.getCreatedAt())
                    .updatedAt(task.getUpdatedAt()).build());
        }
        return result;
    }

    @Transactional
    public HRAssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
        validateAccess(userId);
        Optional<EmpTask> taskOpt = empTaskRepository.findById(taskId);
        if (!taskOpt.isPresent()) throw new RuntimeException("Task not found");
        EmpTask task = taskOpt.get();
        if (!request.getStatus().matches("PENDING|IN_PROGRESS|COMPLETED")) {
            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
        }
        String oldStatus = task.getStatus();
        task.setStatus(request.getStatus());
        task.setUpdatedAt(LocalDateTime.now());
        EmpTask updatedTask = empTaskRepository.save(task);
        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus(), request.getNote());
        String departmentName = "Unknown";
        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
        }
        return HRAssignedTaskResponseDTO.builder()
                .taskId(updatedTask.getEmpTaskId()).description(updatedTask.getDescription())
                .status(updatedTask.getStatus()).assignedToUserId(updatedTask.getAssignedTo().getUserId())
                .assignedToUserName(updatedTask.getAssignedTo().getName()).assignedToDepartment(departmentName)
                .createdByUserId(updatedTask.getCreator().getUserId()).createdByName(updatedTask.getCreator().getName())
                .deadline(updatedTask.getDeadline()).createdAt(updatedTask.getCreatedAt())
                .updatedAt(updatedTask.getUpdatedAt()).build();
    }

    public Map<String, Object> getHRDashboard(Long userId) {
        validateAccess(userId);
        Map<String, Object> dashboard = new HashMap<>();
        List<ConsultantDetails> allConsultants = consultantDetailsRepository.findAll();
        List<EmployeeDetails> allEmployees = employeeDetailsRepository.findAll();
        List<ProjectDetails> allProjects = projectRepository.findAll();
        List<EmpTask> allTasks = empTaskRepository.findAll();
        List<Department> allDepartments = departmentRepository.findAll();
        Map<String, Long> consultantsByStatus = new HashMap<>();
        Map<String, Long> consultantsByDepartment = new HashMap<>();
        for (ConsultantDetails c : allConsultants) {
            String status = getLatestConsultantStatus(c.getConsultantId());
            consultantsByStatus.put(status, consultantsByStatus.getOrDefault(status, 0L) + 1);
            if (c.getDepartment() != null) {
                consultantsByDepartment.put(c.getDepartment().getDepartmentName(),
                        consultantsByDepartment.getOrDefault(c.getDepartment().getDepartmentName(), 0L) + 1);
            }
        }
        long pendingConsultants = consultantsByStatus.getOrDefault("APPLIED", 0L) + consultantsByStatus.getOrDefault("PENDING", 0L);
        dashboard.put("totalConsultants", allConsultants.size());
        dashboard.put("pendingConsultants", pendingConsultants);
        dashboard.put("totalEmployees", allEmployees.size());
        dashboard.put("totalProjects", allProjects.size());
        dashboard.put("totalTasks", allTasks.size());
        dashboard.put("totalDepartments", allDepartments.size());
        dashboard.put("consultantsByStatus", consultantsByStatus);
        dashboard.put("consultantsByDepartment", consultantsByDepartment);
        dashboard.put("employeesByDepartment", getEmployeesByDepartment(allEmployees));
        dashboard.put("projectsByStatus", getProjectsByStatus(allProjects));
        dashboard.put("tasksByStatus", getTasksByStatus(allTasks));
        return dashboard;
    }

    private Map<String, Long> getEmployeesByDepartment(List<EmployeeDetails> employees) {
        Map<String, Long> result = new HashMap<>();
        for (EmployeeDetails e : employees) {
            if (e.getDepartmentRef() != null) {
                String deptName = e.getDepartmentRef().getDepartmentName();
                result.put(deptName, result.getOrDefault(deptName, 0L) + 1);
            }
        }
        return result;
    }

    private Map<String, Long> getProjectsByStatus(List<ProjectDetails> projects) {
        Map<String, Long> result = new HashMap<>();
        for (ProjectDetails p : projects) result.put(p.getStatus(), result.getOrDefault(p.getStatus(), 0L) + 1);
        return result;
    }

    private Map<String, Long> getTasksByStatus(List<EmpTask> tasks) {
        Map<String, Long> result = new HashMap<>();
        for (EmpTask t : tasks) result.put(t.getStatus(), result.getOrDefault(t.getStatus(), 0L) + 1);
        return result;
    }

    private ProjectResponseDTO convertToProjectDTO(ProjectDetails project) {
        return ProjectResponseDTO.builder()
                .projectId(project.getProjectId()).projectName(project.getProjectName())
                .projectDescription(project.getProjectDescription()).projectType(project.getProjectType())
                .status(project.getStatus())
                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
                .clientId(project.getUploader().getUserId()).clientName(project.getUploader().getName())
                .clientEmail(project.getUploader().getEmail())
                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
                .deadline(project.getDeadline()).cost(project.getCost())
                .createdAt(project.getCreatedAt()).updatedAt(project.getUpdatedAt()).build();
    }

    // ==================== PROFESSIONAL EMAIL TEMPLATE (Impetus Style) ====================

    private String buildEmail(String title, String preheader, String bodyContent) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>" + title + "</title></head>" +
                "<body style='margin:0;padding:0;background-color:#ffffff;font-family:Arial,Helvetica,sans-serif;color:#222222;'>" +
                "<div style='display:none;max-height:0;overflow:hidden;'>" + preheader + "</div>" +
                "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color:#ffffff;'>" +
                "<tr><td align='left' style='padding:32px 40px;'>" +

                // Body content
                "<div style='font-size:14px;line-height:1.7;color:#222222;'>" +
                bodyContent +
                "</div>" +

                // Footer
                "<div style='margin-top:32px;padding-top:20px;border-top:1px solid #e0e0e0;font-size:13px;color:#555555;line-height:1.7;'>" +
                "<p style='margin:0 0 4px 0;'>Regards,</p>" +
                "<p style='margin:0 0 4px 0;'><strong>Human Empowerment Group</strong></p>" +
                "<p style='margin:0 0 4px 0;'>Softmax Technologies and IT Services</p>" +
                "<p style='margin:0 0 4px 0;'><a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a></p>" +
                "<p style='margin:0;'><a href='https://softmax.in' style='color:#1a73e8;text-decoration:none;'>softmax.in</a> | <a href='https://nexus.softmax.in' style='color:#1a73e8;text-decoration:none;'>nexus.softmax.in</a></p>" +
                "</div>" +

                "<div style='margin-top:20px;font-size:11px;color:#999999;line-height:1.6;'>" +
                "<p style='margin:0 0 4px 0;'>This is an automated message. Please do not reply to this email.</p>" +
                "<p style='margin:0;'>&copy; 2026 Softmax Technologies and IT Services. All rights reserved.</p>" +
                "</div>" +

                "</td></tr></table></body></html>";
    }

    private void sendConsultantStatusChangeEmail(ConsultantDetails consultant, String status, String note) {
        try {
            String statusUpper = status != null ? status.toUpperCase() : "";
            String subjectLine;
            String heading;
            String intro;
            String actionHtml = "";

            switch (statusUpper) {
                case "APPROVED":
                    subjectLine = "Application Update - Candidate Profile Approved";
                    heading = "Congratulations! Your Application is Approved";
                    intro = "We are pleased to inform you that your candidate application has been approved by our team.";
                    actionHtml = "<p style='margin:16px 0 0 0;'>You can now log in to your candidate portal to access further details.</p>";
                    break;
                case "REJECTED":
                    subjectLine = "Application Update - Candidate Profile Not Selected";
                    heading = "Update on Your Application";
                    intro = "After careful evaluation, we regret to inform you that your application has not been selected at this time.";
                    actionHtml = "<p style='margin:16px 0 0 0;'>You may reapply after 3 months. We wish you all the best for your future endeavors.</p>";
                    break;
                case "INTERVIEW_SCHEDULED":
                    subjectLine = "Interview Invitation - Softmax Technologies";
                    heading = "Invitation for Interview";
                    intro = "Thank you for your interest in our organization. Based on your profile, we would like to invite you for an interview. Please find the interview details below.";
                    break;
                case "SHORTLISTED":
                    subjectLine = "Application Update - You Have Been Shortlisted";
                    heading = "You Have Been Shortlisted";
                    intro = "We are pleased to inform you that your application has been shortlisted. Our team will reach out to you shortly with the next steps.";
                    break;
                case "ON_HOLD":
                    subjectLine = "Application Update - On Hold";
                    heading = "Application Status Update";
                    intro = "Your application has been placed on hold. We will get back to you with further updates in due course.";
                    break;
                case "APPLIED":
                    subjectLine = "Application Received - Softmax Technologies";
                    heading = "Application Received";
                    intro = "Thank you for your interest in joining our organization. We have received your application and it is currently under review.";
                    break;
                default:
                    subjectLine = "Application Status Update";
                    heading = "Application Status Update";
                    intro = "Your application status has been updated.";
                    break;
            }

            String safeNote = EmailService.makeLinksClickable(note != null ? note : "");

            StringBuilder body = new StringBuilder();
            body.append("<p style='margin:0 0 16px 0;'>Dear ").append(consultant.getUser().getName()).append(",</p>");
            body.append("<p style='margin:0 0 20px 0;'>").append(intro).append("</p>");

            // Status block (clean, corporate style) — Date & Time rows REMOVED.
            // The HR note below carries the actual interview date/time written by HR.
            body.append("<table role='presentation' cellspacing='0' cellpadding='0' style='margin:20px 0;border-collapse:collapse;'>")
                    .append("<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Status:</strong></td>")
                    .append("<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>").append(status).append("</td></tr>")
                    .append("</table>");

            if (safeNote != null && !safeNote.isEmpty()) {
                body.append("<p style='margin:0 0 8px 0;'><strong>Note from HR team:</strong></p>")
                        .append("<p style='margin:0 0 20px 0;'>").append(safeNote).append("</p>");
            }

            body.append(actionHtml);

            body.append("<p style='margin:20px 0 0 0;'>You can log in to your candidate portal to view the details and stay updated.</p>");

            // CTA Button
            body.append("<p style='margin:24px 0 0 0;'>")
                    .append("<a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a>")
                    .append("</p>");

            String html = buildEmail(heading, subjectLine, body.toString());
            emailService.sendEmail(consultant.getUser().getEmail(), subjectLine, html);

        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendConsultantApprovalEmail(ConsultantDetails consultant) {
        try {
            String subject = "Application Update - Candidate Profile Approved";
            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>We are pleased to inform you that your candidate application has been approved. You can now log in to your candidate portal to access further details.</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a></p>";
            String html = buildEmail("Application Approved", subject, body);
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendConsultantRejectionEmail(ConsultantDetails consultant, String reason) {
        try {
            String subject = "Application Update - Candidate Profile Not Selected";
            String safeReason = EmailService.makeLinksClickable(reason != null ? reason : "Not specified");
            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>Thank you for your interest in our organization. After careful evaluation, we regret to inform you that your application has not been selected at this time.</p>" +
                    "<p style='margin:0 0 8px 0;'><strong>Reason:</strong></p>" +
                    "<p style='margin:0 0 20px 0;'>" + safeReason + "</p>" +
                    "<p style='margin:0 0 16px 0;'>You may reapply after 3 months. We wish you all the best for your future endeavors.</p>";
            String html = buildEmail("Application Status Update", subject, body);
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendDepartmentAssignmentEmail(ConsultantDetails consultant, Department department, String oldDepartment) {
        try {
            String subject = "Department Assignment Update - Softmax Technologies";
            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>You have been assigned to a new department. Please find the details below:</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>New Department:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + department.getDepartmentName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Previous Department:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + oldDepartment + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:20px 0 0 0;'>You can log in to your candidate portal to view available opportunities.</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a></p>";
            String html = buildEmail("Department Assignment", subject, body);
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department, String stype) {
        try {
            String roleText = "Employee";
            if ("MANAGER".equals(stype)) roleText = "Manager";
            else if ("DIRECTOR".equals(stype)) roleText = "Director";
            String subject = "Welcome Aboard - You are now a " + roleText;
            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>Congratulations! We are pleased to welcome you as a <strong>" + roleText + "</strong> at Softmax Technologies and IT Services.</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Position:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getPositionName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + department.getDepartmentName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Salary:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>INR " + String.format("%.2f", employee.getSalary()) + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:20px 0 0 0;'>You can now log in to your employee portal to complete the onboarding process.</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Start Onboarding</a></p>";
            String html = buildEmail("Welcome Aboard", subject, body);
            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendEmployeeUpdateEmail(EmployeeDetails employee) {
        try {
            String subject = "Profile Update Confirmation";
            String body = "<p style='margin:0 0 16px 0;'>Dear " + employee.getUser().getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>Your profile has been updated by the HR team. Please find the updated details below:</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Position:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getPositionName() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getDepartment() + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Skills:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getSkillSet() + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:20px 0 0 0;'>If you have any questions, please reach out to the HR team.</p>";
            String html = buildEmail("Profile Updated", subject, body);
            emailService.sendEmail(employee.getUser().getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline, String departmentName, String role) {
        try {
            String deadlineStr = deadline != null ? deadline.format(formatter) : "Not specified";
            String subject = "New Task Assignment";
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");
            String body = "<p style='margin:0 0 16px 0;'>Dear " + assignedTo.getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>A new task has been assigned to you by <strong>" + creator.getName() + "</strong> (" + role + "). Please find the details below:</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Task:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeDesc + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + departmentName + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Deadline:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + deadlineStr + "</td></tr>" +
                    "</table>" +
                    "<p style='margin:20px 0 0 0;'>Please log in to your dashboard to view and update the task status.</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>View Task</a></p>";
            String html = buildEmail("New Task Assigned", subject, body);
            emailService.sendEmail(assignedTo.getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
    }

    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus, String note) {
        try {
            User creator = task.getCreator();
            String subject = "Task Status Update";
            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");
            String safeNote = EmailService.makeLinksClickable(note != null ? note : "");
            String body = "<p style='margin:0 0 16px 0;'>Dear " + creator.getName() + ",</p>" +
                    "<p style='margin:0 0 20px 0;'>The status of the following task has been updated by <strong>" + task.getAssignedTo().getName() + "</strong>:</p>" +
                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Task:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeDesc + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Previous Status:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + oldStatus + "</td></tr>" +
                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>New Status:</strong></td>" +
                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + newStatus + "</td></tr>" +
                    (safeNote != null && !safeNote.isEmpty() ?
                            "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Note:</strong></td>" +
                                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeNote + "</td></tr>" : "") +
                    "</table>" +
                    "<p style='margin:20px 0 0 0;'>Please log in to your dashboard to review the details.</p>" +
                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in" + task.getEmpTaskId() + "' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>View Details</a></p>";
            String html = buildEmail("Task Status Updated", subject, body);
            emailService.sendEmail(creator.getEmail(), subject, html);
        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
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
//public class HRDirectorService {
//
//    @Autowired private UserRepository userRepository;
//    @Autowired private UserTypeRepository userTypeRepository;
//    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
//    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
//    @Autowired private ConsultantStatusRepository consultantStatusRepository;
//    @Autowired private ConsultantReviewRepository consultantReviewRepository;
//    @Autowired private ProjectDetailsRepository projectRepository;
//    @Autowired private DepartmentRepository departmentRepository;
//    @Autowired private TeamDetailsRepository teamRepository;
//    @Autowired private TeamMembersRepository teamMembersRepository;
//    @Autowired private EmpTaskRepository empTaskRepository;
//    @Autowired private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");
//    private final DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
//
//    // ==================== VALIDATION ====================
//
//    public boolean isHRDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//        if (!isEmp || !isDirector) return false;
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
//        if (!employeeOpt.isPresent()) return false;
//        EmployeeDetails employee = employeeOpt.get();
//        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 2L;
//    }
//
//    public boolean isHeadDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//        if (!isEmp || !isDirector) return false;
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
//        if (!employeeOpt.isPresent()) return false;
//        EmployeeDetails employee = employeeOpt.get();
//        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 1L;
//    }
//
//    private void validateAccess(Long userId) {
//        if (!isHRDirector(userId) && !isHeadDirector(userId)) {
//            throw new RuntimeException("Access denied: Only HR Director or Head Director can access this resource");
//        }
//    }
//
//    private String getLatestConsultantStatus(Long consultantId) {
//        List<ConsultantStatus> statuses = consultantStatusRepository.findByConsultantConsultantId(consultantId);
//        if (statuses == null || statuses.isEmpty()) return "PENDING";
//        return statuses.get(statuses.size() - 1).getStatus();
//    }
//
//    // ==================== CONSULTANT MANAGEMENT ====================
//
//    public List<ConsultantListResponseDTO> getAllConsultants(Long userId) {
//        validateAccess(userId);
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
//        List<ConsultantListResponseDTO> result = new ArrayList<>();
//        for (ConsultantDetails consultant : consultants) {
//            String latestStatus = getLatestConsultantStatus(consultant.getConsultantId());
//            String isApproved = "PENDING";
//            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//            if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
//            result.add(ConsultantListResponseDTO.builder()
//                    .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
//                    .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
//                    .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
//                    .experience(consultant.getExperience()).currentStatus(latestStatus)
//                    .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
//                    .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
//                    .isApproved(isApproved).build());
//        }
//        return result;
//    }
//
//    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
//        ConsultantDetails consultant = consultantOpt.get();
//        String latestStatus = getLatestConsultantStatus(consultantId);
//        String isApproved = "PENDING";
//        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//        if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
//        return ConsultantListResponseDTO.builder()
//                .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
//                .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
//                .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
//                .experience(consultant.getExperience()).currentStatus(latestStatus)
//                .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
//                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
//                .isApproved(isApproved).build();
//    }
//
//    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
//        validateAccess(userId);
//        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
//        List<ConsultantStatusHistoryDTO> result = new ArrayList<>();
//        for (ConsultantStatus status : statusList) {
//            result.add(ConsultantStatusHistoryDTO.builder()
//                    .statusId(status.getStatusId()).status(status.getStatus())
//                    .note(status.getNote()).dateTime(status.getDateTime()).build());
//        }
//        return result;
//    }
//
//    @Transactional
//    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
//        ConsultantDetails consultant = consultantOpt.get();
//
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(consultant);
//        status.setStatus(request.getStatus());
//        status.setNote(request.getNote());
//        status.setDateTime(LocalDateTime.now());
//        ConsultantStatus savedStatus = consultantStatusRepository.save(status);
//
//        if ("APPROVED".equals(request.getStatus())) {
//            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//            if (userTypeOpt.isPresent()) {
//                UserType ut = userTypeOpt.get();
//                ut.setIsapproved("APPROVED");
//                userTypeRepository.save(ut);
//            }
//        }
//
//        sendConsultantStatusChangeEmail(consultant, request.getStatus(), request.getNote());
//
//        return ConsultantStatusResponseDTO.builder()
//                .statusId(savedStatus.getStatusId()).consultantId(consultantId)
//                .consultantName(consultant.getUser().getName()).status(savedStatus.getStatus())
//                .note(savedStatus.getNote()).dateTime(savedStatus.getDateTime()).build();
//    }
//
//    @Transactional
//    public ConsultantListResponseDTO assignDepartmentToConsultant(Long userId, Long consultantId, AssignDepartmentRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
//        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//        if (!departmentOpt.isPresent()) throw new RuntimeException("Department not found");
//        ConsultantDetails consultant = consultantOpt.get();
//        Department department = departmentOpt.get();
//        String oldDepartment = consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : "Not Assigned";
//        consultant.setDepartment(department);
//        consultantDetailsRepository.save(consultant);
//        sendDepartmentAssignmentEmail(consultant, department, oldDepartment);
//        String latestStatus = getLatestConsultantStatus(consultantId);
//        String isApproved = "PENDING";
//        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//        if (userTypeOpt.isPresent()) isApproved = userTypeOpt.get().getIsapproved();
//        return ConsultantListResponseDTO.builder()
//                .consultantId(consultant.getConsultantId()).userId(consultant.getUser().getUserId())
//                .name(consultant.getUser().getName()).email(consultant.getUser().getEmail())
//                .photoUrl(consultant.getUser().getPhotoUrl()).skill(consultant.getSkill())
//                .experience(consultant.getExperience()).currentStatus(latestStatus)
//                .departmentId(department.getDepartmentId()).departmentName(department.getDepartmentName())
//                .isApproved(isApproved).build();
//    }
//
//    @Transactional
//    public EmployeeDTO convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) throw new RuntimeException("Consultant not found");
//        ConsultantDetails consultant = consultantOpt.get();
//        User user = consultant.getUser();
//        Optional<EmployeeDetails> existingEmployee = employeeDetailsRepository.findByUserUserId(user.getUserId());
//        if (existingEmployee.isPresent()) throw new RuntimeException("User is already an employee");
//        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//        if (!departmentOpt.isPresent()) throw new RuntimeException("Department not found");
//        Department department = departmentOpt.get();
//        EmployeeDetails employee = new EmployeeDetails();
//        employee.setUser(user);
//        employee.setIsDirectorVerified(true);
//        employee.setPositionName(request.getPositionName());
//        employee.setSalary(request.getSalary());
//        employee.setSkillSet(request.getSkillSet());
//        employee.setDepartmentRef(department);
//        employee.setDepartment(department.getDepartmentName());
//        employeeDetailsRepository.save(employee);
//        String stype = request.getStype() != null && !request.getStype().isEmpty() ? request.getStype() : null;
//        UserType empRole = new UserType();
//        empRole.setUser(user);
//        empRole.setPtype("EMP");
//        empRole.setStype(stype);
//        empRole.setIsapproved("APPROVED");
//        empRole.setCreatedAt(LocalDateTime.now());
//        userTypeRepository.save(empRole);
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(consultant);
//        status.setStatus("CONVERTED_TO_EMPLOYEE");
//        status.setNote("Converted to employee as " + request.getPositionName() + (stype != null ? " (" + stype + ")" : "") + " in " + department.getDepartmentName());
//        status.setDateTime(LocalDateTime.now());
//        consultantStatusRepository.save(status);
//        sendConversionEmail(consultant, employee, department, stype);
//        return EmployeeDTO.builder()
//                .employeeId(employee.getEmployeeId()).userId(user.getUserId()).name(user.getName())
//                .email(user.getEmail()).photoUrl(user.getPhotoUrl()).phoneNumber(user.getPhoneNumber())
//                .address(user.getAddress()).gender(user.getGender())
//                .isDirectorVerified(employee.getIsDirectorVerified()).positionName(employee.getPositionName())
//                .salary(employee.getSalary()).department(employee.getDepartment()).skillSet(employee.getSkillSet())
//                .departmentId(department.getDepartmentId()).departmentName(department.getDepartmentName()).build();
//    }
//
//    public List<EmployeeDTO> getAllEmployees(Long userId) {
//        validateAccess(userId);
//        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//        List<EmployeeDTO> result = new ArrayList<>();
//        for (EmployeeDetails e : employees) {
//            User u = e.getUser();
//            result.add(EmployeeDTO.builder()
//                    .employeeId(e.getEmployeeId()).userId(u.getUserId()).name(u.getName())
//                    .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
//                    .address(u.getAddress()).gender(u.getGender())
//                    .isDirectorVerified(e.getIsDirectorVerified()).positionName(e.getPositionName())
//                    .salary(e.getSalary()).department(e.getDepartment()).skillSet(e.getSkillSet())
//                    .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
//                    .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null).build());
//        }
//        return result;
//    }
//
//    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
//        validateAccess(userId);
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
//        if (!employeeOpt.isPresent()) throw new RuntimeException("Employee not found");
//        EmployeeDetails e = employeeOpt.get();
//        User u = e.getUser();
//        return EmployeeDTO.builder()
//                .employeeId(e.getEmployeeId()).userId(u.getUserId()).name(u.getName())
//                .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
//                .address(u.getAddress()).gender(u.getGender())
//                .isDirectorVerified(e.getIsDirectorVerified()).positionName(e.getPositionName())
//                .salary(e.getSalary()).department(e.getDepartment()).skillSet(e.getSkillSet())
//                .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
//                .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null).build();
//    }
//
//    @Transactional
//    public EmployeeDTO updateEmployee(Long userId, Long employeeId, UpdateEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
//        if (!employeeOpt.isPresent()) throw new RuntimeException("Employee not found");
//        EmployeeDetails employee = employeeOpt.get();
//        if (request.getPositionName() != null) employee.setPositionName(request.getPositionName());
//        if (request.getSalary() != null) employee.setSalary(request.getSalary());
//        if (request.getSkillSet() != null) employee.setSkillSet(request.getSkillSet());
//        if (request.getDepartmentId() != null) {
//            Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//            if (departmentOpt.isPresent()) {
//                Department department = departmentOpt.get();
//                employee.setDepartmentRef(department);
//                employee.setDepartment(department.getDepartmentName());
//            }
//        }
//        employeeDetailsRepository.save(employee);
//        sendEmployeeUpdateEmail(employee);
//        User u = employee.getUser();
//        return EmployeeDTO.builder()
//                .employeeId(employee.getEmployeeId()).userId(u.getUserId()).name(u.getName())
//                .email(u.getEmail()).photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
//                .address(u.getAddress()).gender(u.getGender())
//                .isDirectorVerified(employee.getIsDirectorVerified()).positionName(employee.getPositionName())
//                .salary(employee.getSalary()).department(employee.getDepartment()).skillSet(employee.getSkillSet())
//                .departmentId(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentId() : null)
//                .departmentName(employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : null).build();
//    }
//
//    public List<ProjectResponseDTO> getAllProjects(Long userId) {
//        validateAccess(userId);
//        List<ProjectDetails> projects = projectRepository.findAll();
//        List<ProjectResponseDTO> result = new ArrayList<>();
//        for (ProjectDetails p : projects) result.add(convertToProjectDTO(p));
//        return result;
//    }
//
//    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
//        validateAccess(userId);
//        Optional<ProjectDetails> projectOpt = projectRepository.findById(projectId);
//        if (!projectOpt.isPresent()) throw new RuntimeException("Project not found");
//        return convertToProjectDTO(projectOpt.get());
//    }
//
//    @Transactional
//    public HRAssignedTaskResponseDTO assignTaskToAnyEmployee(Long userId, AssignTaskToAnyEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<User> creatorOpt = userRepository.findById(userId);
//        if (!creatorOpt.isPresent()) throw new RuntimeException("Creator not found");
//        Optional<User> assignedToOpt = userRepository.findById(request.getAssignedToUserId());
//        if (!assignedToOpt.isPresent()) throw new RuntimeException("Assigned user not found");
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(request.getAssignedToUserId());
//        if (!employeeOpt.isPresent()) throw new RuntimeException("Assigned user is not an employee");
//        User creator = creatorOpt.get();
//        User assignedTo = assignedToOpt.get();
//        EmployeeDetails employee = employeeOpt.get();
//        String departmentName = employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : "Unknown";
//        EmpTask task = new EmpTask();
//        task.setCreator(creator);
//        task.setAssignedTo(assignedTo);
//        task.setStatus("PENDING");
//        task.setDescription(request.getDescription());
//        task.setDeadline(request.getDeadline());
//        task.setCreatedAt(LocalDateTime.now());
//        task.setUpdatedAt(LocalDateTime.now());
//        EmpTask savedTask = empTaskRepository.save(task);
//        String role = isHRDirector(userId) ? "HR Director" : "Head Director";
//        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline(), departmentName, role);
//        return HRAssignedTaskResponseDTO.builder()
//                .taskId(savedTask.getEmpTaskId()).description(savedTask.getDescription())
//                .status(savedTask.getStatus()).assignedToUserId(assignedTo.getUserId())
//                .assignedToUserName(assignedTo.getName()).assignedToDepartment(departmentName)
//                .createdByUserId(creator.getUserId()).createdByName(creator.getName())
//                .deadline(request.getDeadline()).createdAt(savedTask.getCreatedAt())
//                .updatedAt(savedTask.getUpdatedAt()).build();
//    }
//
//    public List<HRAssignedTaskResponseDTO> getAllDepartmentTasks(Long userId) {
//        validateAccess(userId);
//        List<EmpTask> tasks = empTaskRepository.findAll();
//        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
//        for (EmpTask task : tasks) {
//            String departmentName = "Unknown";
//            Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
//            if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//                departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//            }
//            result.add(HRAssignedTaskResponseDTO.builder()
//                    .taskId(task.getEmpTaskId()).description(task.getDescription())
//                    .status(task.getStatus()).assignedToUserId(task.getAssignedTo().getUserId())
//                    .assignedToUserName(task.getAssignedTo().getName()).assignedToDepartment(departmentName)
//                    .createdByUserId(task.getCreator().getUserId()).createdByName(task.getCreator().getName())
//                    .deadline(task.getDeadline()).createdAt(task.getCreatedAt())
//                    .updatedAt(task.getUpdatedAt()).build());
//        }
//        return result;
//    }
//
//    public List<HRAssignedTaskResponseDTO> getTasksByEmployee(Long userId, Long employeeUserId) {
//        validateAccess(userId);
//        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(employeeUserId);
//        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
//        String departmentName = "Unknown";
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(employeeUserId);
//        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//        }
//        final String finalDeptName = departmentName;
//        for (EmpTask task : tasks) {
//            result.add(HRAssignedTaskResponseDTO.builder()
//                    .taskId(task.getEmpTaskId()).description(task.getDescription())
//                    .status(task.getStatus()).assignedToUserId(task.getAssignedTo().getUserId())
//                    .assignedToUserName(task.getAssignedTo().getName()).assignedToDepartment(finalDeptName)
//                    .createdByUserId(task.getCreator().getUserId()).createdByName(task.getCreator().getName())
//                    .deadline(task.getDeadline()).createdAt(task.getCreatedAt())
//                    .updatedAt(task.getUpdatedAt()).build());
//        }
//        return result;
//    }
//
//    @Transactional
//    public HRAssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
//        validateAccess(userId);
//        Optional<EmpTask> taskOpt = empTaskRepository.findById(taskId);
//        if (!taskOpt.isPresent()) throw new RuntimeException("Task not found");
//        EmpTask task = taskOpt.get();
//        if (!request.getStatus().matches("PENDING|IN_PROGRESS|COMPLETED")) {
//            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
//        }
//        String oldStatus = task.getStatus();
//        task.setStatus(request.getStatus());
//        task.setUpdatedAt(LocalDateTime.now());
//        EmpTask updatedTask = empTaskRepository.save(task);
//        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus(), request.getNote());
//        String departmentName = "Unknown";
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
//        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//        }
//        return HRAssignedTaskResponseDTO.builder()
//                .taskId(updatedTask.getEmpTaskId()).description(updatedTask.getDescription())
//                .status(updatedTask.getStatus()).assignedToUserId(updatedTask.getAssignedTo().getUserId())
//                .assignedToUserName(updatedTask.getAssignedTo().getName()).assignedToDepartment(departmentName)
//                .createdByUserId(updatedTask.getCreator().getUserId()).createdByName(updatedTask.getCreator().getName())
//                .deadline(updatedTask.getDeadline()).createdAt(updatedTask.getCreatedAt())
//                .updatedAt(updatedTask.getUpdatedAt()).build();
//    }
//
//    public Map<String, Object> getHRDashboard(Long userId) {
//        validateAccess(userId);
//        Map<String, Object> dashboard = new HashMap<>();
//        List<ConsultantDetails> allConsultants = consultantDetailsRepository.findAll();
//        List<EmployeeDetails> allEmployees = employeeDetailsRepository.findAll();
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        List<EmpTask> allTasks = empTaskRepository.findAll();
//        List<Department> allDepartments = departmentRepository.findAll();
//        Map<String, Long> consultantsByStatus = new HashMap<>();
//        Map<String, Long> consultantsByDepartment = new HashMap<>();
//        for (ConsultantDetails c : allConsultants) {
//            String status = getLatestConsultantStatus(c.getConsultantId());
//            consultantsByStatus.put(status, consultantsByStatus.getOrDefault(status, 0L) + 1);
//            if (c.getDepartment() != null) {
//                consultantsByDepartment.put(c.getDepartment().getDepartmentName(),
//                        consultantsByDepartment.getOrDefault(c.getDepartment().getDepartmentName(), 0L) + 1);
//            }
//        }
//        long pendingConsultants = consultantsByStatus.getOrDefault("APPLIED", 0L) + consultantsByStatus.getOrDefault("PENDING", 0L);
//        dashboard.put("totalConsultants", allConsultants.size());
//        dashboard.put("pendingConsultants", pendingConsultants);
//        dashboard.put("totalEmployees", allEmployees.size());
//        dashboard.put("totalProjects", allProjects.size());
//        dashboard.put("totalTasks", allTasks.size());
//        dashboard.put("totalDepartments", allDepartments.size());
//        dashboard.put("consultantsByStatus", consultantsByStatus);
//        dashboard.put("consultantsByDepartment", consultantsByDepartment);
//        dashboard.put("employeesByDepartment", getEmployeesByDepartment(allEmployees));
//        dashboard.put("projectsByStatus", getProjectsByStatus(allProjects));
//        dashboard.put("tasksByStatus", getTasksByStatus(allTasks));
//        return dashboard;
//    }
//
//    private Map<String, Long> getEmployeesByDepartment(List<EmployeeDetails> employees) {
//        Map<String, Long> result = new HashMap<>();
//        for (EmployeeDetails e : employees) {
//            if (e.getDepartmentRef() != null) {
//                String deptName = e.getDepartmentRef().getDepartmentName();
//                result.put(deptName, result.getOrDefault(deptName, 0L) + 1);
//            }
//        }
//        return result;
//    }
//
//    private Map<String, Long> getProjectsByStatus(List<ProjectDetails> projects) {
//        Map<String, Long> result = new HashMap<>();
//        for (ProjectDetails p : projects) result.put(p.getStatus(), result.getOrDefault(p.getStatus(), 0L) + 1);
//        return result;
//    }
//
//    private Map<String, Long> getTasksByStatus(List<EmpTask> tasks) {
//        Map<String, Long> result = new HashMap<>();
//        for (EmpTask t : tasks) result.put(t.getStatus(), result.getOrDefault(t.getStatus(), 0L) + 1);
//        return result;
//    }
//
//    private ProjectResponseDTO convertToProjectDTO(ProjectDetails project) {
//        return ProjectResponseDTO.builder()
//                .projectId(project.getProjectId()).projectName(project.getProjectName())
//                .projectDescription(project.getProjectDescription()).projectType(project.getProjectType())
//                .status(project.getStatus())
//                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
//                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
//                .clientId(project.getUploader().getUserId()).clientName(project.getUploader().getName())
//                .clientEmail(project.getUploader().getEmail())
//                .assignedManagerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
//                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
//                .deadline(project.getDeadline()).cost(project.getCost())
//                .createdAt(project.getCreatedAt()).updatedAt(project.getUpdatedAt()).build();
//    }
//
//    // ==================== PROFESSIONAL EMAIL TEMPLATE (Impetus Style) ====================
//
//    private String buildEmail(String title, String preheader, String bodyContent) {
//        return "<!DOCTYPE html>" +
//                "<html><head><meta charset='UTF-8'>" +
//                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
//                "<title>" + title + "</title></head>" +
//                "<body style='margin:0;padding:0;background-color:#ffffff;font-family:Arial,Helvetica,sans-serif;color:#222222;'>" +
//                "<div style='display:none;max-height:0;overflow:hidden;'>" + preheader + "</div>" +
//                "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color:#ffffff;'>" +
//                "<tr><td align='left' style='padding:32px 40px;'>" +
//
//                // Body content
//                "<div style='font-size:14px;line-height:1.7;color:#222222;'>" +
//                bodyContent +
//                "</div>" +
//
//                // Footer
//                "<div style='margin-top:32px;padding-top:20px;border-top:1px solid #e0e0e0;font-size:13px;color:#555555;line-height:1.7;'>" +
//                "<p style='margin:0 0 4px 0;'>Regards,</p>" +
//                "<p style='margin:0 0 4px 0;'><strong>Human Empowerment Group</strong></p>" +
//                "<p style='margin:0 0 4px 0;'>Softmax Technologies and IT Services</p>" +
//                "<p style='margin:0 0 4px 0;'><a href='mailto:softmaxdigitals@gmail.com' style='color:#1a73e8;text-decoration:none;'>softmaxdigitals@gmail.com</a></p>" +
//                "<p style='margin:0;'><a href='https://softmax.in' style='color:#1a73e8;text-decoration:none;'>softmax.in</a> | <a href='https://nexus.softmax.in' style='color:#1a73e8;text-decoration:none;'>nexus.softmax.in</a></p>" +
//                "</div>" +
//
//                "<div style='margin-top:20px;font-size:11px;color:#999999;line-height:1.6;'>" +
//                "<p style='margin:0 0 4px 0;'>This is an automated message. Please do not reply to this email.</p>" +
//                "<p style='margin:0;'>&copy; 2026 Softmax Technologies and IT Services. All rights reserved.</p>" +
//                "</div>" +
//
//                "</td></tr></table></body></html>";
//    }
//
//    private void sendConsultantStatusChangeEmail(ConsultantDetails consultant, String status, String note) {
//        try {
//            String statusUpper = status != null ? status.toUpperCase() : "";
//            String subjectLine;
//            String heading;
//            String intro;
//            String actionHtml = "";
//
//            switch (statusUpper) {
//                case "APPROVED":
//                    subjectLine = "Application Update - Candidate Profile Approved";
//                    heading = "Congratulations! Your Application is Approved";
//                    intro = "We are pleased to inform you that your candidate application has been approved by our team.";
//                    actionHtml = "<p style='margin:16px 0 0 0;'>You can now log in to your candidate portal to access further details.</p>";
//                    break;
//                case "REJECTED":
//                    subjectLine = "Application Update - Candidate Profile Not Selected";
//                    heading = "Update on Your Application";
//                    intro = "After careful evaluation, we regret to inform you that your application has not been selected at this time.";
//                    actionHtml = "<p style='margin:16px 0 0 0;'>You may reapply after 3 months. We wish you all the best for your future endeavors.</p>";
//                    break;
//                case "INTERVIEW_SCHEDULED":
//                    subjectLine = "Interview Invitation - Softmax Technologies";
//                    heading = "Invitation for Interview";
//                    intro = "Thank you for your interest in our organization. Based on your profile, we would like to invite you for an interview.";
//                    break;
//                case "SHORTLISTED":
//                    subjectLine = "Application Update - You Have Been Shortlisted";
//                    heading = "You Have Been Shortlisted";
//                    intro = "We are pleased to inform you that your application has been shortlisted. Our team will reach out to you shortly with the next steps.";
//                    break;
//                case "ON_HOLD":
//                    subjectLine = "Application Update - On Hold";
//                    heading = "Application Status Update";
//                    intro = "Your application has been placed on hold. We will get back to you with further updates in due course.";
//                    break;
//                case "APPLIED":
//                    subjectLine = "Application Received - Softmax Technologies";
//                    heading = "Application Received";
//                    intro = "Thank you for your interest in joining our organization. We have received your application and it is currently under review.";
//                    break;
//                default:
//                    subjectLine = "Application Status Update";
//                    heading = "Application Status Update";
//                    intro = "Your application status has been updated.";
//                    break;
//            }
//
//            String safeNote = EmailService.makeLinksClickable(note != null ? note : "");
//
//            StringBuilder body = new StringBuilder();
//            body.append("<p style='margin:0 0 16px 0;'>Dear ").append(consultant.getUser().getName()).append(",</p>");
//            body.append("<p style='margin:0 0 20px 0;'>").append(intro).append("</p>");
//
//            // Status block (clean, corporate style like Impetus)
//            body.append("<table role='presentation' cellspacing='0' cellpadding='0' style='margin:20px 0;border-collapse:collapse;'>")
//                    .append("<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Status:</strong></td>")
//                    .append("<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>").append(status).append("</td></tr>")
//                    .append("<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Date:</strong></td>")
//                    .append("<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>").append(LocalDateTime.now().format(dateOnly)).append("</td></tr>")
//                    .append("<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Time:</strong></td>")
//                    .append("<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>").append(LocalDateTime.now().format(formatter)).append("</td></tr>")
//                    .append("</table>");
//
//            if (safeNote != null && !safeNote.isEmpty()) {
//                body.append("<p style='margin:0 0 8px 0;'><strong>Note from HR team:</strong></p>")
//                        .append("<p style='margin:0 0 20px 0;'>").append(safeNote).append("</p>");
//            }
//
//            body.append(actionHtml);
//
//            body.append("<p style='margin:20px 0 0 0;'>You can log in to your candidate portal to view the details and stay updated.</p>");
//
//            // CTA Button
//            body.append("<p style='margin:24px 0 0 0;'>")
//                    .append("<a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a>")
//                    .append("</p>");
//
//            String html = buildEmail(heading, subjectLine, body.toString());
//            emailService.sendEmail(consultant.getUser().getEmail(), subjectLine, html);
//
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendConsultantApprovalEmail(ConsultantDetails consultant) {
//        try {
//            String subject = "Application Update - Candidate Profile Approved";
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>We are pleased to inform you that your candidate application has been approved. You can now log in to your candidate portal to access further details.</p>" +
//                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a></p>";
//            String html = buildEmail("Application Approved", subject, body);
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendConsultantRejectionEmail(ConsultantDetails consultant, String reason) {
//        try {
//            String subject = "Application Update - Candidate Profile Not Selected";
//            String safeReason = EmailService.makeLinksClickable(reason != null ? reason : "Not specified");
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>Thank you for your interest in our organization. After careful evaluation, we regret to inform you that your application has not been selected at this time.</p>" +
//                    "<p style='margin:0 0 8px 0;'><strong>Reason:</strong></p>" +
//                    "<p style='margin:0 0 20px 0;'>" + safeReason + "</p>" +
//                    "<p style='margin:0 0 16px 0;'>You may reapply after 3 months. We wish you all the best for your future endeavors.</p>";
//            String html = buildEmail("Application Status Update", subject, body);
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendDepartmentAssignmentEmail(ConsultantDetails consultant, Department department, String oldDepartment) {
//        try {
//            String subject = "Department Assignment Update - Softmax Technologies";
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>You have been assigned to a new department. Please find the details below:</p>" +
//                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>New Department:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + department.getDepartmentName() + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Previous Department:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + oldDepartment + "</td></tr>" +
//                    "</table>" +
//                    "<p style='margin:20px 0 0 0;'>You can log in to your candidate portal to view available opportunities.</p>" +
//                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Candidate Portal Login</a></p>";
//            String html = buildEmail("Department Assignment", subject, body);
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department, String stype) {
//        try {
//            String roleText = "Employee";
//            if ("MANAGER".equals(stype)) roleText = "Manager";
//            else if ("DIRECTOR".equals(stype)) roleText = "Director";
//            String subject = "Welcome Aboard - You are now a " + roleText;
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + consultant.getUser().getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>Congratulations! We are pleased to welcome you as a <strong>" + roleText + "</strong> at Softmax Technologies and IT Services.</p>" +
//                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Position:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getPositionName() + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + department.getDepartmentName() + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Salary:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>INR " + String.format("%.2f", employee.getSalary()) + "</td></tr>" +
//                    "</table>" +
//                    "<p style='margin:20px 0 0 0;'>You can now log in to your employee portal to complete the onboarding process.</p>" +
//                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>Start Onboarding</a></p>";
//            String html = buildEmail("Welcome Aboard", subject, body);
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendEmployeeUpdateEmail(EmployeeDetails employee) {
//        try {
//            String subject = "Profile Update Confirmation";
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + employee.getUser().getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>Your profile has been updated by the HR team. Please find the updated details below:</p>" +
//                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Position:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getPositionName() + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getDepartment() + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Skills:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + employee.getSkillSet() + "</td></tr>" +
//                    "</table>" +
//                    "<p style='margin:20px 0 0 0;'>If you have any questions, please reach out to the HR team.</p>";
//            String html = buildEmail("Profile Updated", subject, body);
//            emailService.sendEmail(employee.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline, String departmentName, String role) {
//        try {
//            String deadlineStr = deadline != null ? deadline.format(formatter) : "Not specified";
//            String subject = "New Task Assignment";
//            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + assignedTo.getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>A new task has been assigned to you by <strong>" + creator.getName() + "</strong> (" + role + "). Please find the details below:</p>" +
//                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Task:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeDesc + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Department:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + departmentName + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Deadline:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + deadlineStr + "</td></tr>" +
//                    "</table>" +
//                    "<p style='margin:20px 0 0 0;'>Please log in to your dashboard to view and update the task status.</p>" +
//                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>View Task</a></p>";
//            String html = buildEmail("New Task Assigned", subject, body);
//            emailService.sendEmail(assignedTo.getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus, String note) {
//        try {
//            User creator = task.getCreator();
//            String subject = "Task Status Update";
//            String safeDesc = EmailService.makeLinksClickable(task.getDescription() != null ? task.getDescription() : "");
//            String safeNote = EmailService.makeLinksClickable(note != null ? note : "");
//            String body = "<p style='margin:0 0 16px 0;'>Dear " + creator.getName() + ",</p>" +
//                    "<p style='margin:0 0 20px 0;'>The status of the following task has been updated by <strong>" + task.getAssignedTo().getName() + "</strong>:</p>" +
//                    "<table role='presentation' cellspacing='0' cellpadding='0' style='margin:16px 0;border-collapse:collapse;'>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Task:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeDesc + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>Previous Status:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + oldStatus + "</td></tr>" +
//                    "<tr><td style='padding:6px 0;font-size:14px;color:#222222;'><strong>New Status:</strong></td>" +
//                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + newStatus + "</td></tr>" +
//                    (safeNote != null && !safeNote.isEmpty() ?
//                            "<tr><td style='padding:6px 0;font-size:14px;color:#222222;vertical-align:top;'><strong>Note:</strong></td>" +
//                                    "<td style='padding:6px 0 6px 20px;font-size:14px;color:#222222;'>" + safeNote + "</td></tr>" : "") +
//                    "</table>" +
//                    "<p style='margin:20px 0 0 0;'>Please log in to your dashboard to review the details.</p>" +
//                    "<p style='margin:24px 0 0 0;'><a href='https://nexus.softmax.in" + task.getEmpTaskId() + "' style='display:inline-block;background:#1a73e8;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;font-size:14px;'>View Details</a></p>";
//            String html = buildEmail("Task Status Updated", subject, body);
//            emailService.sendEmail(creator.getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//}









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
//public class HRDirectorService {
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
//    private ConsultantReviewRepository consultantReviewRepository;
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
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
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
//    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
//
//    // ==================== VALIDATION ====================
//
//    public boolean isHRDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//
//        if (!isEmp || !isDirector) return false;
//
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
//        if (!employeeOpt.isPresent()) return false;
//
//        EmployeeDetails employee = employeeOpt.get();
//        return employee.getDepartmentRef() != null &&
//                employee.getDepartmentRef().getDepartmentId() == 2L;
//    }
//
//    public boolean isHeadDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//
//        if (!isEmp || !isDirector) return false;
//
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
//        if (!employeeOpt.isPresent()) return false;
//
//        EmployeeDetails employee = employeeOpt.get();
//        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 1L;
//    }
//
//    private void validateAccess(Long userId) {
//        if (!isHRDirector(userId) && !isHeadDirector(userId)) {
//            throw new RuntimeException("Access denied: Only HR Director or Head Director can access this resource");
//        }
//    }
//
//    private String getLatestConsultantStatus(Long consultantId) {
//        List<ConsultantStatus> statuses = consultantStatusRepository.findByConsultantConsultantId(consultantId);
//        if (statuses == null || statuses.isEmpty()) {
//            return "PENDING";
//        }
//        return statuses.get(statuses.size() - 1).getStatus();
//    }
//
//    // ==================== CONSULTANT MANAGEMENT ====================
//
//    public List<ConsultantListResponseDTO> getAllConsultants(Long userId) {
//        validateAccess(userId);
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
//        List<ConsultantListResponseDTO> result = new ArrayList<>();
//
//        for (ConsultantDetails consultant : consultants) {
//            String latestStatus = getLatestConsultantStatus(consultant.getConsultantId());
//            String isApproved = "PENDING";
//
//            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//            if (userTypeOpt.isPresent()) {
//                isApproved = userTypeOpt.get().getIsapproved();
//            }
//
//            result.add(ConsultantListResponseDTO.builder()
//                    .consultantId(consultant.getConsultantId())
//                    .userId(consultant.getUser().getUserId())
//                    .name(consultant.getUser().getName())
//                    .email(consultant.getUser().getEmail())
//                    .photoUrl(consultant.getUser().getPhotoUrl())
//                    .skill(consultant.getSkill())
//                    .experience(consultant.getExperience())
//                    .currentStatus(latestStatus)
//                    .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
//                    .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
//                    .isApproved(isApproved)
//                    .build());
//        }
//        return result;
//    }
//
//    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) {
//            throw new RuntimeException("Consultant not found");
//        }
//        ConsultantDetails consultant = consultantOpt.get();
//        String latestStatus = getLatestConsultantStatus(consultantId);
//        String isApproved = "PENDING";
//
//        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//        if (userTypeOpt.isPresent()) {
//            isApproved = userTypeOpt.get().getIsapproved();
//        }
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
//                .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
//                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
//                .isApproved(isApproved)
//                .build();
//    }
//
//    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
//        validateAccess(userId);
//        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
//        List<ConsultantStatusHistoryDTO> result = new ArrayList<>();
//        for (ConsultantStatus status : statusList) {
//            result.add(ConsultantStatusHistoryDTO.builder()
//                    .statusId(status.getStatusId())
//                    .status(status.getStatus())
//                    .note(status.getNote())
//                    .dateTime(status.getDateTime())
//                    .build());
//        }
//        return result;
//    }
//
//    @Transactional
//    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) {
//            throw new RuntimeException("Consultant not found");
//        }
//        ConsultantDetails consultant = consultantOpt.get();
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
//            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//            if (userTypeOpt.isPresent()) {
//                UserType ut = userTypeOpt.get();
//                ut.setIsapproved("APPROVED");
//                userTypeRepository.save(ut);
//                sendConsultantApprovalEmail(consultant);
//            }
//        } else if ("REJECTED".equals(request.getStatus())) {
//            sendConsultantRejectionEmail(consultant, request.getNote());
//        }
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
//
//    @Transactional
//    public ConsultantListResponseDTO assignDepartmentToConsultant(Long userId, Long consultantId, AssignDepartmentRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) {
//            throw new RuntimeException("Consultant not found");
//        }
//        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//        if (!departmentOpt.isPresent()) {
//            throw new RuntimeException("Department not found");
//        }
//
//        ConsultantDetails consultant = consultantOpt.get();
//        Department department = departmentOpt.get();
//        String oldDepartment = consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : "Not Assigned";
//        consultant.setDepartment(department);
//        consultantDetailsRepository.save(consultant);
//        sendDepartmentAssignmentEmail(consultant, department, oldDepartment);
//
//        String latestStatus = getLatestConsultantStatus(consultantId);
//        String isApproved = "PENDING";
//
//        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
//        if (userTypeOpt.isPresent()) {
//            isApproved = userTypeOpt.get().getIsapproved();
//        }
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
//                .departmentId(department.getDepartmentId())
//                .departmentName(department.getDepartmentName())
//                .isApproved(isApproved)
//                .build();
//    }
//
//    // ==================== CONVERT CONSULTANT TO EMPLOYEE ====================
//
//    @Transactional
//    public EmployeeDTO convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
//        if (!consultantOpt.isPresent()) {
//            throw new RuntimeException("Consultant not found");
//        }
//
//        ConsultantDetails consultant = consultantOpt.get();
//        User user = consultant.getUser();
//
//        Optional<EmployeeDetails> existingEmployee = employeeDetailsRepository.findByUserUserId(user.getUserId());
//        if (existingEmployee.isPresent()) {
//            throw new RuntimeException("User is already an employee");
//        }
//
//        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//        if (!departmentOpt.isPresent()) {
//            throw new RuntimeException("Department not found");
//        }
//
//        Department department = departmentOpt.get();
//
//        EmployeeDetails employee = new EmployeeDetails();
//        employee.setUser(user);
//        employee.setIsDirectorVerified(true);
//        employee.setPositionName(request.getPositionName());
//        employee.setSalary(request.getSalary());
//        employee.setSkillSet(request.getSkillSet());
//        employee.setDepartmentRef(department);
//        employee.setDepartment(department.getDepartmentName());
//        employeeDetailsRepository.save(employee);
//
//        // Use stype from request - HR/Head Director can send "MANAGER" or "DIRECTOR"
//        String stype = request.getStype() != null && !request.getStype().isEmpty() ? request.getStype() : null;
//
//        UserType empRole = new UserType();
//        empRole.setUser(user);
//        empRole.setPtype("EMP");
//        empRole.setStype(stype);
//        empRole.setIsapproved("APPROVED");
//        empRole.setCreatedAt(LocalDateTime.now());
//        userTypeRepository.save(empRole);
//
//        ConsultantStatus status = new ConsultantStatus();
//        status.setConsultant(consultant);
//        status.setStatus("CONVERTED_TO_EMPLOYEE");
//        status.setNote("Converted to employee as " + request.getPositionName() + (stype != null ? " (" + stype + ")" : "") + " in " + department.getDepartmentName());
//        status.setDateTime(LocalDateTime.now());
//        consultantStatusRepository.save(status);
//
//        sendConversionEmail(consultant, employee, department, stype);
//
//        return EmployeeDTO.builder()
//                .employeeId(employee.getEmployeeId())
//                .userId(user.getUserId())
//                .name(user.getName())
//                .email(user.getEmail())
//                .photoUrl(user.getPhotoUrl())
//                .phoneNumber(user.getPhoneNumber())
//                .address(user.getAddress())
//                .gender(user.getGender())
//                .isDirectorVerified(employee.getIsDirectorVerified())
//                .positionName(employee.getPositionName())
//                .salary(employee.getSalary())
//                .department(employee.getDepartment())
//                .skillSet(employee.getSkillSet())
//                .departmentId(department.getDepartmentId())
//                .departmentName(department.getDepartmentName())
//                .build();
//    }
//
//    // ==================== EMPLOYEE MANAGEMENT ====================
//
//    public List<EmployeeDTO> getAllEmployees(Long userId) {
//        validateAccess(userId);
//        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
//        List<EmployeeDTO> result = new ArrayList<>();
//
//        for (EmployeeDetails e : employees) {
//            User u = e.getUser();
//            result.add(EmployeeDTO.builder()
//                    .employeeId(e.getEmployeeId())
//                    .userId(u.getUserId())
//                    .name(u.getName())
//                    .email(u.getEmail())
//                    .photoUrl(u.getPhotoUrl())
//                    .phoneNumber(u.getPhoneNumber())
//                    .address(u.getAddress())
//                    .gender(u.getGender())
//                    .isDirectorVerified(e.getIsDirectorVerified())
//                    .positionName(e.getPositionName())
//                    .salary(e.getSalary())
//                    .department(e.getDepartment())
//                    .skillSet(e.getSkillSet())
//                    .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
//                    .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                    .build());
//        }
//        return result;
//    }
//
//    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
//        validateAccess(userId);
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
//        if (!employeeOpt.isPresent()) {
//            throw new RuntimeException("Employee not found");
//        }
//        EmployeeDetails e = employeeOpt.get();
//        User u = e.getUser();
//
//        return EmployeeDTO.builder()
//                .employeeId(e.getEmployeeId())
//                .userId(u.getUserId())
//                .name(u.getName())
//                .email(u.getEmail())
//                .photoUrl(u.getPhotoUrl())
//                .phoneNumber(u.getPhoneNumber())
//                .address(u.getAddress())
//                .gender(u.getGender())
//                .isDirectorVerified(e.getIsDirectorVerified())
//                .positionName(e.getPositionName())
//                .salary(e.getSalary())
//                .department(e.getDepartment())
//                .skillSet(e.getSkillSet())
//                .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
//                .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
//                .build();
//    }
//
//    @Transactional
//    public EmployeeDTO updateEmployee(Long userId, Long employeeId, UpdateEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
//        if (!employeeOpt.isPresent()) {
//            throw new RuntimeException("Employee not found");
//        }
//        EmployeeDetails employee = employeeOpt.get();
//
//        if (request.getPositionName() != null) employee.setPositionName(request.getPositionName());
//        if (request.getSalary() != null) employee.setSalary(request.getSalary());
//        if (request.getSkillSet() != null) employee.setSkillSet(request.getSkillSet());
//
//        if (request.getDepartmentId() != null) {
//            Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
//            if (departmentOpt.isPresent()) {
//                Department department = departmentOpt.get();
//                employee.setDepartmentRef(department);
//                employee.setDepartment(department.getDepartmentName());
//            }
//        }
//        employeeDetailsRepository.save(employee);
//        sendEmployeeUpdateEmail(employee);
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
//    public List<ProjectResponseDTO> getAllProjects(Long userId) {
//        validateAccess(userId);
//        List<ProjectDetails> projects = projectRepository.findAll();
//        List<ProjectResponseDTO> result = new ArrayList<>();
//        for (ProjectDetails p : projects) {
//            result.add(convertToProjectDTO(p));
//        }
//        return result;
//    }
//
//    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
//        validateAccess(userId);
//        Optional<ProjectDetails> projectOpt = projectRepository.findById(projectId);
//        if (!projectOpt.isPresent()) {
//            throw new RuntimeException("Project not found");
//        }
//        return convertToProjectDTO(projectOpt.get());
//    }
//
//    // ==================== TASK MANAGEMENT ====================
//
//    @Transactional
//    public HRAssignedTaskResponseDTO assignTaskToAnyEmployee(Long userId, AssignTaskToAnyEmployeeRequest request) {
//        validateAccess(userId);
//        Optional<User> creatorOpt = userRepository.findById(userId);
//        if (!creatorOpt.isPresent()) {
//            throw new RuntimeException("Creator not found");
//        }
//        Optional<User> assignedToOpt = userRepository.findById(request.getAssignedToUserId());
//        if (!assignedToOpt.isPresent()) {
//            throw new RuntimeException("Assigned user not found");
//        }
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(request.getAssignedToUserId());
//        if (!employeeOpt.isPresent()) {
//            throw new RuntimeException("Assigned user is not an employee");
//        }
//
//        User creator = creatorOpt.get();
//        User assignedTo = assignedToOpt.get();
//        EmployeeDetails employee = employeeOpt.get();
//        String departmentName = employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : "Unknown";
//
//        EmpTask task = new EmpTask();
//        task.setCreator(creator);
//        task.setAssignedTo(assignedTo);
//        task.setStatus("PENDING");
//        task.setDescription(request.getDescription());
//        task.setDeadline(request.getDeadline());
//        task.setCreatedAt(LocalDateTime.now());
//        task.setUpdatedAt(LocalDateTime.now());
//        EmpTask savedTask = empTaskRepository.save(task);
//
//        String role = isHRDirector(userId) ? "HR Director" : "Head Director";
//        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline(), departmentName, role);
//
//        return HRAssignedTaskResponseDTO.builder()
//                .taskId(savedTask.getEmpTaskId())
//                .description(savedTask.getDescription())
//                .status(savedTask.getStatus())
//                .assignedToUserId(assignedTo.getUserId())
//                .assignedToUserName(assignedTo.getName())
//                .assignedToDepartment(departmentName)
//                .createdByUserId(creator.getUserId())
//                .createdByName(creator.getName())
//                .deadline(request.getDeadline())
//                .createdAt(savedTask.getCreatedAt())
//                .updatedAt(savedTask.getUpdatedAt())
//                .build();
//    }
//
//    public List<HRAssignedTaskResponseDTO> getAllDepartmentTasks(Long userId) {
//        validateAccess(userId);
//        List<EmpTask> tasks = empTaskRepository.findAll();
//        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
//        for (EmpTask task : tasks) {
//            String departmentName = "Unknown";
//            Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
//            if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//                departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//            }
//            result.add(HRAssignedTaskResponseDTO.builder()
//                    .taskId(task.getEmpTaskId())
//                    .description(task.getDescription())
//                    .status(task.getStatus())
//                    .assignedToUserId(task.getAssignedTo().getUserId())
//                    .assignedToUserName(task.getAssignedTo().getName())
//                    .assignedToDepartment(departmentName)
//                    .createdByUserId(task.getCreator().getUserId())
//                    .createdByName(task.getCreator().getName())
//                    .deadline(task.getDeadline())
//                    .createdAt(task.getCreatedAt())
//                    .updatedAt(task.getUpdatedAt())
//                    .build());
//        }
//        return result;
//    }
//
//    public List<HRAssignedTaskResponseDTO> getTasksByEmployee(Long userId, Long employeeUserId) {
//        validateAccess(userId);
//        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(employeeUserId);
//        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
//        String departmentName = "Unknown";
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(employeeUserId);
//        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//        }
//        final String finalDeptName = departmentName;
//        for (EmpTask task : tasks) {
//            result.add(HRAssignedTaskResponseDTO.builder()
//                    .taskId(task.getEmpTaskId())
//                    .description(task.getDescription())
//                    .status(task.getStatus())
//                    .assignedToUserId(task.getAssignedTo().getUserId())
//                    .assignedToUserName(task.getAssignedTo().getName())
//                    .assignedToDepartment(finalDeptName)
//                    .createdByUserId(task.getCreator().getUserId())
//                    .createdByName(task.getCreator().getName())
//                    .deadline(task.getDeadline())
//                    .createdAt(task.getCreatedAt())
//                    .updatedAt(task.getUpdatedAt())
//                    .build());
//        }
//        return result;
//    }
//
//    @Transactional
//    public HRAssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
//        validateAccess(userId);
//        Optional<EmpTask> taskOpt = empTaskRepository.findById(taskId);
//        if (!taskOpt.isPresent()) {
//            throw new RuntimeException("Task not found");
//        }
//        EmpTask task = taskOpt.get();
//        if (!request.getStatus().matches("PENDING|IN_PROGRESS|COMPLETED")) {
//            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
//        }
//        String oldStatus = task.getStatus();
//        task.setStatus(request.getStatus());
//        task.setUpdatedAt(LocalDateTime.now());
//        EmpTask updatedTask = empTaskRepository.save(task);
//        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus(), request.getNote());
//
//        String departmentName = "Unknown";
//        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
//        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
//            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
//        }
//
//        return HRAssignedTaskResponseDTO.builder()
//                .taskId(updatedTask.getEmpTaskId())
//                .description(updatedTask.getDescription())
//                .status(updatedTask.getStatus())
//                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
//                .assignedToUserName(updatedTask.getAssignedTo().getName())
//                .assignedToDepartment(departmentName)
//                .createdByUserId(updatedTask.getCreator().getUserId())
//                .createdByName(updatedTask.getCreator().getName())
//                .deadline(updatedTask.getDeadline())
//                .createdAt(updatedTask.getCreatedAt())
//                .updatedAt(updatedTask.getUpdatedAt())
//                .build();
//    }
//
//    // ==================== DASHBOARD ====================
//
//    public Map<String, Object> getHRDashboard(Long userId) {
//        validateAccess(userId);
//        Map<String, Object> dashboard = new HashMap<>();
//
//        List<ConsultantDetails> allConsultants = consultantDetailsRepository.findAll();
//        List<EmployeeDetails> allEmployees = employeeDetailsRepository.findAll();
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        List<EmpTask> allTasks = empTaskRepository.findAll();
//        List<Department> allDepartments = departmentRepository.findAll();
//
//        Map<String, Long> consultantsByStatus = new HashMap<>();
//        Map<String, Long> consultantsByDepartment = new HashMap<>();
//
//        for (ConsultantDetails c : allConsultants) {
//            String status = getLatestConsultantStatus(c.getConsultantId());
//            consultantsByStatus.put(status, consultantsByStatus.getOrDefault(status, 0L) + 1);
//            if (c.getDepartment() != null) {
//                consultantsByDepartment.put(c.getDepartment().getDepartmentName(),
//                        consultantsByDepartment.getOrDefault(c.getDepartment().getDepartmentName(), 0L) + 1);
//            }
//        }
//
//        long pendingConsultants = consultantsByStatus.getOrDefault("APPLIED", 0L) + consultantsByStatus.getOrDefault("PENDING", 0L);
//
//        dashboard.put("totalConsultants", allConsultants.size());
//        dashboard.put("pendingConsultants", pendingConsultants);
//        dashboard.put("totalEmployees", allEmployees.size());
//        dashboard.put("totalProjects", allProjects.size());
//        dashboard.put("totalTasks", allTasks.size());
//        dashboard.put("totalDepartments", allDepartments.size());
//        dashboard.put("consultantsByStatus", consultantsByStatus);
//        dashboard.put("consultantsByDepartment", consultantsByDepartment);
//        dashboard.put("employeesByDepartment", getEmployeesByDepartment(allEmployees));
//        dashboard.put("projectsByStatus", getProjectsByStatus(allProjects));
//        dashboard.put("tasksByStatus", getTasksByStatus(allTasks));
//
//        return dashboard;
//    }
//
//    private Map<String, Long> getEmployeesByDepartment(List<EmployeeDetails> employees) {
//        Map<String, Long> result = new HashMap<>();
//        for (EmployeeDetails e : employees) {
//            if (e.getDepartmentRef() != null) {
//                String deptName = e.getDepartmentRef().getDepartmentName();
//                result.put(deptName, result.getOrDefault(deptName, 0L) + 1);
//            }
//        }
//        return result;
//    }
//
//    private Map<String, Long> getProjectsByStatus(List<ProjectDetails> projects) {
//        Map<String, Long> result = new HashMap<>();
//        for (ProjectDetails p : projects) {
//            result.put(p.getStatus(), result.getOrDefault(p.getStatus(), 0L) + 1);
//        }
//        return result;
//    }
//
//    private Map<String, Long> getTasksByStatus(List<EmpTask> tasks) {
//        Map<String, Long> result = new HashMap<>();
//        for (EmpTask t : tasks) {
//            result.put(t.getStatus(), result.getOrDefault(t.getStatus(), 0L) + 1);
//        }
//        return result;
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
//    // ==================== EMAIL METHODS ====================
//
//    private void sendConsultantApprovalEmail(ConsultantDetails consultant) {
//        try {
//            String subject = "🎉 Congratulations! Your Consultant Application is Approved";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2 style='text-align: center;'>🎉 Welcome to ShardaOS!</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Your consultant application has been <strong>APPROVED</strong>!</p>
//                        <p>You can now access the consultant dashboard and apply for projects.</p>
//                        <a href='https://shardaos.com/consultant/dashboard' style='display: inline-block; background: white; color: #667eea; padding: 10px 20px; text-decoration: none; border-radius: 5px; margin-top: 15px;'>Go to Dashboard</a>
//                    </div>
//                </body>
//                </html>
//                """, consultant.getUser().getName());
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendConsultantRejectionEmail(ConsultantDetails consultant, String reason) {
//        try {
//            String subject = "📢 Update on Your Consultant Application";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px; border: 1px solid #ff4757;'>
//                        <h2 style='color: #ff4757;'>Application Status Update</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>After careful evaluation, your application has been <strong>REJECTED</strong>.</p>
//                        <p><strong>Reason:</strong> %s</p>
//                        <p>You may reapply after 3 months.</p>
//                    </div>
//                </body>
//                </html>
//                """, consultant.getUser().getName(), reason != null ? reason : "Not specified");
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendDepartmentAssignmentEmail(ConsultantDetails consultant, Department department, String oldDepartment) {
//        try {
//            String subject = "🏢 Department Assignment Update";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2>🏢 Department Assigned</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>You have been assigned to <strong>%s Department</strong></p>
//                        <p>Previous Department: %s</p>
//                        <p>Please login to view available projects.</p>
//                    </div>
//                </body>
//                </html>
//                """, consultant.getUser().getName(), department.getDepartmentName(), oldDepartment);
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department, String stype) {
//        try {
//            String roleText = "Employee";
//            if ("MANAGER".equals(stype)) roleText = "Manager";
//            else if ("DIRECTOR".equals(stype)) roleText = "Director";
//
//            String subject = "🎉 Congratulations! You're Now a ShardaOS " + roleText + "!";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2 style='text-align: center;'>🎉 WELCOME ABOARD!</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>You have been converted to <strong>%s</strong> as <strong>%s</strong> in <strong>%s</strong> department.</p>
//                        <p><strong>Salary:</strong> ₹%.2f</p>
//                        <a href='https://shardaos.com/employee/onboarding' style='display: inline-block; background: white; color: #f5576c; padding: 10px 20px; text-decoration: none; border-radius: 5px; margin-top: 15px;'>Start Your Journey</a>
//                    </div>
//                </body>
//                </html>
//                """, consultant.getUser().getName(), roleText, employee.getPositionName(), department.getDepartmentName(), employee.getSalary());
//            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendEmployeeUpdateEmail(EmployeeDetails employee) {
//        try {
//            String subject = "📝 Profile Update Confirmation";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px; border: 1px solid #4CAF50;'>
//                        <h2 style='color: #4CAF50;'>📝 Profile Updated</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Your profile has been updated by HR team.</p>
//                        <p><strong>Position:</strong> %s</p>
//                        <p><strong>Department:</strong> %s</p>
//                        <p><strong>Skills:</strong> %s</p>
//                    </div>
//                </body>
//                </html>
//                """, employee.getUser().getName(), employee.getPositionName(), employee.getDepartment(), employee.getSkillSet());
//            emailService.sendEmail(employee.getUser().getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline, String departmentName, String role) {
//        try {
//            String deadlineStr = deadline != null ? deadline.format(formatter) : "Not specified";
//            String subject = "📋 New Task Assignment";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #FF9800 0%, #FF5722 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2>📋 New Task Assigned</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>You have a new task from <strong>%s (%s)</strong>.</p>
//                        <p><strong>Task:</strong> %s</p>
//                        <p><strong>Department:</strong> %s</p>
//                        <p><strong>Deadline:</strong> %s</p>
//                        <a href='https://shardaos.com/tasks' style='display: inline-block; background: white; color: #FF9800; padding: 8px 16px; text-decoration: none; border-radius: 5px; margin-top: 10px;'>View Task</a>
//                    </div>
//                </body>
//                </html>
//                """, assignedTo.getName(), creator.getName(), role, task.getDescription(), departmentName, deadlineStr);
//            emailService.sendEmail(assignedTo.getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
//    }
//
//    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus, String note) {
//        try {
//            User creator = task.getCreator();
//            String subject = "🔄 Task Status Updated";
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: linear-gradient(135deg, #2196F3 0%, #1a237e 100%); border-radius: 10px; padding: 20px; color: white;'>
//                        <h2>🔄 Task Status Updated</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Task status updated by <strong>%s</strong>.</p>
//                        <p><strong>Task:</strong> %s</p>
//                        <p><strong>Status:</strong> %s → %s</p>
//                        %s
//                        <a href='https://shardaos.com/tasks/%d' style='display: inline-block; background: white; color: #2196F3; padding: 8px 16px; text-decoration: none; border-radius: 5px; margin-top: 10px;'>View Details</a>
//                    </div>
//                </body>
//                </html>
//                """, creator.getName(), task.getAssignedTo().getName(), task.getDescription(), oldStatus, newStatus,
//                    note != null ? "<p><strong>Note:</strong> " + note + "</p>" : "", task.getEmpTaskId());
//            emailService.sendEmail(creator.getEmail(), subject, html);
//        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
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
////public class HRDirectorService {
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
////    private ConsultantReviewRepository consultantReviewRepository;
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
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
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
////    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
////
////    // ==================== VALIDATION ====================
////
////    public boolean isHRDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////
////        if (!isEmp || !isDirector) return false;
////
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
////        if (!employeeOpt.isPresent()) return false;
////
////        EmployeeDetails employee = employeeOpt.get();
////        return employee.getDepartmentRef() != null &&
////                employee.getDepartmentRef().getDepartmentId() == 2L;
////    }
////
////    public boolean isHeadDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////
////        if (!isEmp || !isDirector) return false;
////
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(userId);
////        if (!employeeOpt.isPresent()) return false;
////
////        EmployeeDetails employee = employeeOpt.get();
////        return employee.getDepartmentRef() != null && employee.getDepartmentRef().getDepartmentId() == 1L;
////    }
////
////    private void validateAccess(Long userId) {
////        if (!isHRDirector(userId) && !isHeadDirector(userId)) {
////            throw new RuntimeException("Access denied: Only HR Director or Head Director can access this resource");
////        }
////    }
////
////    // ==================== CONSULTANT MANAGEMENT ====================
////
////    public List<ConsultantListResponseDTO> getAllConsultants(Long userId) {
////        validateAccess(userId);
////
////        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();
////        List<ConsultantListResponseDTO> result = new ArrayList<>();
////
////        for (ConsultantDetails consultant : consultants) {
////            String latestStatus = getLatestConsultantStatus(consultant.getConsultantId());
////            String isApproved = "PENDING";
////
////            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
////            if (userTypeOpt.isPresent()) {
////                isApproved = userTypeOpt.get().getIsapproved();
////            }
////
////            result.add(ConsultantListResponseDTO.builder()
////                    .consultantId(consultant.getConsultantId())
////                    .userId(consultant.getUser().getUserId())
////                    .name(consultant.getUser().getName())
////                    .email(consultant.getUser().getEmail())
////                    .photoUrl(consultant.getUser().getPhotoUrl())
////                    .skill(consultant.getSkill())
////                    .experience(consultant.getExperience())
////                    .currentStatus(latestStatus)
////                    .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
////                    .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
////                    .isApproved(isApproved)
////                    .build());
////        }
////
////        return result;
////    }
////
////    public ConsultantListResponseDTO getConsultantById(Long userId, Long consultantId) {
////        validateAccess(userId);
////
////        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
////        if (!consultantOpt.isPresent()) {
////            throw new RuntimeException("Consultant not found");
////        }
////
////        ConsultantDetails consultant = consultantOpt.get();
////        String latestStatus = getLatestConsultantStatus(consultantId);
////        String isApproved = "PENDING";
////
////        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
////        if (userTypeOpt.isPresent()) {
////            isApproved = userTypeOpt.get().getIsapproved();
////        }
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
////                .departmentId(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentId() : null)
////                .departmentName(consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : null)
////                .isApproved(isApproved)
////                .build();
////    }
////
////    public List<ConsultantStatusHistoryDTO> getConsultantStatusHistory(Long userId, Long consultantId) {
////        validateAccess(userId);
////
////        List<ConsultantStatus> statusList = consultantStatusRepository.findByConsultantConsultantId(consultantId);
////        List<ConsultantStatusHistoryDTO> result = new ArrayList<>();
////
////        for (ConsultantStatus status : statusList) {
////            result.add(ConsultantStatusHistoryDTO.builder()
////                    .statusId(status.getStatusId())
////                    .status(status.getStatus())
////                    .note(status.getNote())
////                    .dateTime(status.getDateTime())
////                    .build());
////        }
////
////        return result;
////    }
////
////    @Transactional
////    public ConsultantStatusResponseDTO updateConsultantStatus(Long userId, Long consultantId, UpdateConsultantStatusRequest request) {
////        validateAccess(userId);
////
////        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
////        if (!consultantOpt.isPresent()) {
////            throw new RuntimeException("Consultant not found");
////        }
////
////        ConsultantDetails consultant = consultantOpt.get();
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
////            Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
////            if (userTypeOpt.isPresent()) {
////                UserType ut = userTypeOpt.get();
////                ut.setIsapproved("APPROVED");
////                userTypeRepository.save(ut);
////                sendConsultantApprovalEmail(consultant);
////            }
////        } else if ("REJECTED".equals(request.getStatus())) {
////            sendConsultantRejectionEmail(consultant, request.getNote());
////        }
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
////    public ConsultantListResponseDTO assignDepartmentToConsultant(Long userId, Long consultantId, AssignDepartmentRequest request) {
////        validateAccess(userId);
////
////        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
////        if (!consultantOpt.isPresent()) {
////            throw new RuntimeException("Consultant not found");
////        }
////
////        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
////        if (!departmentOpt.isPresent()) {
////            throw new RuntimeException("Department not found");
////        }
////
////        ConsultantDetails consultant = consultantOpt.get();
////        Department department = departmentOpt.get();
////
////        String oldDepartment = consultant.getDepartment() != null ? consultant.getDepartment().getDepartmentName() : "Not Assigned";
////        consultant.setDepartment(department);
////        consultantDetailsRepository.save(consultant);
////
////        sendDepartmentAssignmentEmail(consultant, department, oldDepartment);
////
////        String latestStatus = getLatestConsultantStatus(consultantId);
////        String isApproved = "PENDING";
////
////        Optional<UserType> userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(consultant.getUser().getUserId(), "CONSULTANT");
////        if (userTypeOpt.isPresent()) {
////            isApproved = userTypeOpt.get().getIsapproved();
////        }
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
////                .departmentId(department.getDepartmentId())
////                .departmentName(department.getDepartmentName())
////                .isApproved(isApproved)
////                .build();
////    }
////
////    @Transactional
////    public EmployeeDTO convertConsultantToEmployee(Long userId, Long consultantId, ConvertToEmployeeRequest request) {
////        validateAccess(userId);
////
////        Optional<ConsultantDetails> consultantOpt = consultantDetailsRepository.findById(consultantId);
////        if (!consultantOpt.isPresent()) {
////            throw new RuntimeException("Consultant not found");
////        }
////
////        ConsultantDetails consultant = consultantOpt.get();
////        User user = consultant.getUser();
////
////        Optional<EmployeeDetails> existingEmployee = employeeDetailsRepository.findByUserUserId(user.getUserId());
////        if (existingEmployee.isPresent()) {
////            throw new RuntimeException("User is already an employee");
////        }
////
////        Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
////        if (!departmentOpt.isPresent()) {
////            throw new RuntimeException("Department not found");
////        }
////
////        Department department = departmentOpt.get();
////
////        EmployeeDetails employee = new EmployeeDetails();
////        employee.setUser(user);
////        employee.setIsDirectorVerified(true);
////        employee.setPositionName(request.getPositionName());
////        employee.setSalary(request.getSalary());
////        employee.setSkillSet(request.getSkillSet());
////        employee.setDepartmentRef(department);
////        employee.setDepartment(department.getDepartmentName());
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
////        ConsultantStatus status = new ConsultantStatus();
////        status.setConsultant(consultant);
////        status.setStatus("CONVERTED_TO_EMPLOYEE");
////        status.setNote("Converted to employee as " + request.getPositionName() + " in " + department.getDepartmentName());
////        status.setDateTime(LocalDateTime.now());
////        consultantStatusRepository.save(status);
////
////        sendConversionEmail(consultant, employee, department);
////
////        return EmployeeDTO.builder()
////                .employeeId(employee.getEmployeeId())
////                .userId(user.getUserId())
////                .name(user.getName())
////                .email(user.getEmail())
////                .photoUrl(user.getPhotoUrl())
////                .phoneNumber(user.getPhoneNumber())
////                .address(user.getAddress())
////                .gender(user.getGender())
////                .isDirectorVerified(employee.getIsDirectorVerified())
////                .positionName(employee.getPositionName())
////                .salary(employee.getSalary())
////                .department(employee.getDepartment())
////                .skillSet(employee.getSkillSet())
////                .departmentId(department.getDepartmentId())
////                .departmentName(department.getDepartmentName())
////                .build();
////    }
////
////    // ==================== EMPLOYEE MANAGEMENT ====================
////
////    public List<EmployeeDTO> getAllEmployees(Long userId) {
////        validateAccess(userId);
////
////        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();
////        List<EmployeeDTO> result = new ArrayList<>();
////
////        for (EmployeeDetails e : employees) {
////            User u = e.getUser();
////            result.add(EmployeeDTO.builder()
////                    .employeeId(e.getEmployeeId())
////                    .userId(u.getUserId())
////                    .name(u.getName())
////                    .email(u.getEmail())
////                    .photoUrl(u.getPhotoUrl())
////                    .phoneNumber(u.getPhoneNumber())
////                    .address(u.getAddress())
////                    .gender(u.getGender())
////                    .isDirectorVerified(e.getIsDirectorVerified())
////                    .positionName(e.getPositionName())
////                    .salary(e.getSalary())
////                    .department(e.getDepartment())
////                    .skillSet(e.getSkillSet())
////                    .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
////                    .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
////                    .build());
////        }
////
////        return result;
////    }
////
////    public EmployeeDTO getEmployeeById(Long userId, Long employeeId) {
////        validateAccess(userId);
////
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
////        if (!employeeOpt.isPresent()) {
////            throw new RuntimeException("Employee not found");
////        }
////
////        EmployeeDetails e = employeeOpt.get();
////        User u = e.getUser();
////
////        return EmployeeDTO.builder()
////                .employeeId(e.getEmployeeId())
////                .userId(u.getUserId())
////                .name(u.getName())
////                .email(u.getEmail())
////                .photoUrl(u.getPhotoUrl())
////                .phoneNumber(u.getPhoneNumber())
////                .address(u.getAddress())
////                .gender(u.getGender())
////                .isDirectorVerified(e.getIsDirectorVerified())
////                .positionName(e.getPositionName())
////                .salary(e.getSalary())
////                .department(e.getDepartment())
////                .skillSet(e.getSkillSet())
////                .departmentId(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentId() : null)
////                .departmentName(e.getDepartmentRef() != null ? e.getDepartmentRef().getDepartmentName() : null)
////                .build();
////    }
////
////    @Transactional
////    public EmployeeDTO updateEmployee(Long userId, Long employeeId, UpdateEmployeeRequest request) {
////        validateAccess(userId);
////
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findById(employeeId);
////        if (!employeeOpt.isPresent()) {
////            throw new RuntimeException("Employee not found");
////        }
////
////        EmployeeDetails employee = employeeOpt.get();
////
////        if (request.getPositionName() != null) employee.setPositionName(request.getPositionName());
////        if (request.getSalary() != null) employee.setSalary(request.getSalary());
////        if (request.getSkillSet() != null) employee.setSkillSet(request.getSkillSet());
////
////        if (request.getDepartmentId() != null) {
////            Optional<Department> departmentOpt = departmentRepository.findById(request.getDepartmentId());
////            if (departmentOpt.isPresent()) {
////                Department department = departmentOpt.get();
////                employee.setDepartmentRef(department);
////                employee.setDepartment(department.getDepartmentName());
////            }
////        }
////
////        employeeDetailsRepository.save(employee);
////        sendEmployeeUpdateEmail(employee);
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
////    public List<ProjectResponseDTO> getAllProjects(Long userId) {
////        validateAccess(userId);
////
////        List<ProjectDetails> projects = projectRepository.findAll();
////        List<ProjectResponseDTO> result = new ArrayList<>();
////
////        for (ProjectDetails p : projects) {
////            result.add(convertToProjectDTO(p));
////        }
////
////        return result;
////    }
////
////    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
////        validateAccess(userId);
////
////        Optional<ProjectDetails> projectOpt = projectRepository.findById(projectId);
////        if (!projectOpt.isPresent()) {
////            throw new RuntimeException("Project not found");
////        }
////
////        return convertToProjectDTO(projectOpt.get());
////    }
////
////    // ==================== TASK MANAGEMENT ====================
////
////    @Transactional
////    public HRAssignedTaskResponseDTO assignTaskToAnyEmployee(Long userId, AssignTaskToAnyEmployeeRequest request) {
////        validateAccess(userId);
////
////        Optional<User> creatorOpt = userRepository.findById(userId);
////        if (!creatorOpt.isPresent()) {
////            throw new RuntimeException("Creator not found");
////        }
////
////        Optional<User> assignedToOpt = userRepository.findById(request.getAssignedToUserId());
////        if (!assignedToOpt.isPresent()) {
////            throw new RuntimeException("Assigned user not found");
////        }
////
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(request.getAssignedToUserId());
////        if (!employeeOpt.isPresent()) {
////            throw new RuntimeException("Assigned user is not an employee");
////        }
////
////        User creator = creatorOpt.get();
////        User assignedTo = assignedToOpt.get();
////        EmployeeDetails employee = employeeOpt.get();
////
////        String departmentName = employee.getDepartmentRef() != null ? employee.getDepartmentRef().getDepartmentName() : "Unknown";
////
////        EmpTask task = new EmpTask();
////        task.setCreator(creator);
////        task.setAssignedTo(assignedTo);
////        task.setStatus("PENDING");
////        task.setDescription(request.getDescription());
////        task.setDeadline(request.getDeadline());
////        task.setCreatedAt(LocalDateTime.now());
////        task.setUpdatedAt(LocalDateTime.now());
////
////        EmpTask savedTask = empTaskRepository.save(task);
////
////        String role = isHRDirector(userId) ? "HR Director" : "Head Director";
////        sendTaskAssignmentEmail(assignedTo, creator, task, request.getDeadline(), departmentName, role);
////
////        return HRAssignedTaskResponseDTO.builder()
////                .taskId(savedTask.getEmpTaskId())
////                .description(savedTask.getDescription())
////                .status(savedTask.getStatus())
////                .assignedToUserId(assignedTo.getUserId())
////                .assignedToUserName(assignedTo.getName())
////                .assignedToDepartment(departmentName)
////                .createdByUserId(creator.getUserId())
////                .createdByName(creator.getName())
////                .deadline(request.getDeadline())
////                .createdAt(savedTask.getCreatedAt())
////                .updatedAt(savedTask.getUpdatedAt())
////                .build();
////    }
////
////    public List<HRAssignedTaskResponseDTO> getAllDepartmentTasks(Long userId) {
////        validateAccess(userId);
////
////        List<EmpTask> tasks = empTaskRepository.findAll();
////        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
////
////        for (EmpTask task : tasks) {
////            String departmentName = "Unknown";
////            Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
////            if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
////                departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
////            }
////
////            result.add(HRAssignedTaskResponseDTO.builder()
////                    .taskId(task.getEmpTaskId())
////                    .description(task.getDescription())
////                    .status(task.getStatus())
////                    .assignedToUserId(task.getAssignedTo().getUserId())
////                    .assignedToUserName(task.getAssignedTo().getName())
////                    .assignedToDepartment(departmentName)
////                    .createdByUserId(task.getCreator().getUserId())
////                    .createdByName(task.getCreator().getName())
////                    .deadline(task.getDeadline())
////                    .createdAt(task.getCreatedAt())
////                    .updatedAt(task.getUpdatedAt())
////                    .build());
////        }
////
////        return result;
////    }
////
////    public List<HRAssignedTaskResponseDTO> getTasksByEmployee(Long userId, Long employeeUserId) {
////        validateAccess(userId);
////
////        List<EmpTask> tasks = empTaskRepository.findByAssignedToUserId(employeeUserId);
////        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
////
////        String departmentName = "Unknown";
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(employeeUserId);
////        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
////            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
////        }
////
////        final String finalDeptName = departmentName;
////
////        for (EmpTask task : tasks) {
////            result.add(HRAssignedTaskResponseDTO.builder()
////                    .taskId(task.getEmpTaskId())
////                    .description(task.getDescription())
////                    .status(task.getStatus())
////                    .assignedToUserId(task.getAssignedTo().getUserId())
////                    .assignedToUserName(task.getAssignedTo().getName())
////                    .assignedToDepartment(finalDeptName)
////                    .createdByUserId(task.getCreator().getUserId())
////                    .createdByName(task.getCreator().getName())
////                    .deadline(task.getDeadline())
////                    .createdAt(task.getCreatedAt())
////                    .updatedAt(task.getUpdatedAt())
////                    .build());
////        }
////
////        return result;
////    }
////
////    @Transactional
////    public HRAssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
////        validateAccess(userId);
////
////        Optional<EmpTask> taskOpt = empTaskRepository.findById(taskId);
////        if (!taskOpt.isPresent()) {
////            throw new RuntimeException("Task not found");
////        }
////
////        EmpTask task = taskOpt.get();
////
////        if (!request.getStatus().matches("PENDING|IN_PROGRESS|COMPLETED")) {
////            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
////        }
////
////        String oldStatus = task.getStatus();
////        task.setStatus(request.getStatus());
////        task.setUpdatedAt(LocalDateTime.now());
////        EmpTask updatedTask = empTaskRepository.save(task);
////
////        sendTaskStatusUpdateEmail(task, oldStatus, request.getStatus(), request.getNote());
////
////        String departmentName = "Unknown";
////        Optional<EmployeeDetails> employeeOpt = employeeDetailsRepository.findByUserUserId(task.getAssignedTo().getUserId());
////        if (employeeOpt.isPresent() && employeeOpt.get().getDepartmentRef() != null) {
////            departmentName = employeeOpt.get().getDepartmentRef().getDepartmentName();
////        }
////
////        return HRAssignedTaskResponseDTO.builder()
////                .taskId(updatedTask.getEmpTaskId())
////                .description(updatedTask.getDescription())
////                .status(updatedTask.getStatus())
////                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
////                .assignedToUserName(updatedTask.getAssignedTo().getName())
////                .assignedToDepartment(departmentName)
////                .createdByUserId(updatedTask.getCreator().getUserId())
////                .createdByName(updatedTask.getCreator().getName())
////                .deadline(updatedTask.getDeadline())
////                .createdAt(updatedTask.getCreatedAt())
////                .updatedAt(updatedTask.getUpdatedAt())
////                .build();
////    }
////
////    // ==================== DASHBOARD ====================
////
////    public Map<String, Object> getHRDashboard(Long userId) {
////        validateAccess(userId);
////
////        List<ConsultantDetails> allConsultants = consultantDetailsRepository.findAll();
////        List<EmployeeDetails> allEmployees = employeeDetailsRepository.findAll();
////        List<ProjectDetails> allProjects = projectRepository.findAll();
////        List<EmpTask> allTasks = empTaskRepository.findAll();
////        List<Department> allDepartments = departmentRepository.findAll();
////
////        Map<String, Object> dashboard = new HashMap<>();
////
////        Map<String, Long> consultantsByStatus = new HashMap<>();
////        Map<String, Long> consultantsByDepartment = new HashMap<>();
////
////        for (ConsultantDetails c : allConsultants) {
////            String status = getLatestConsultantStatus(c.getConsultantId());
////            consultantsByStatus.put(status, consultantsByStatus.getOrDefault(status, 0L) + 1);
////
////            if (c.getDepartment() != null) {
////                String deptName = c.getDepartment().getDepartmentName();
////                consultantsByDepartment.put(deptName, consultantsByDepartment.getOrDefault(deptName, 0L) + 1);
////            }
////        }
////
////        Map<String, Long> employeesByDepartment = new HashMap<>();
////        for (EmployeeDetails e : allEmployees) {
////            if (e.getDepartmentRef() != null) {
////                String deptName = e.getDepartmentRef().getDepartmentName();
////                employeesByDepartment.put(deptName, employeesByDepartment.getOrDefault(deptName, 0L) + 1);
////            }
////        }
////
////        Map<String, Long> projectsByStatus = new HashMap<>();
////        for (ProjectDetails p : allProjects) {
////            projectsByStatus.put(p.getStatus(), projectsByStatus.getOrDefault(p.getStatus(), 0L) + 1);
////        }
////
////        Map<String, Long> tasksByStatus = new HashMap<>();
////        for (EmpTask t : allTasks) {
////            tasksByStatus.put(t.getStatus(), tasksByStatus.getOrDefault(t.getStatus(), 0L) + 1);
////        }
////
////        long pendingConsultants = consultantsByStatus.getOrDefault("APPLIED", 0L) +
////                consultantsByStatus.getOrDefault("PENDING", 0L);
////
////        dashboard.put("totalConsultants", allConsultants.size());
////        dashboard.put("pendingConsultants", pendingConsultants);
////        dashboard.put("totalEmployees", allEmployees.size());
////        dashboard.put("totalProjects", allProjects.size());
////        dashboard.put("totalTasks", allTasks.size());
////        dashboard.put("totalDepartments", allDepartments.size());
////
////        dashboard.put("consultantsByStatus", consultantsByStatus);
////        dashboard.put("consultantsByDepartment", consultantsByDepartment);
////        dashboard.put("employeesByDepartment", employeesByDepartment);
////        dashboard.put("projectsByStatus", projectsByStatus);
////        dashboard.put("tasksByStatus", tasksByStatus);
////
////        dashboard.put("pendingTasks", tasksByStatus.getOrDefault("PENDING", 0L));
////        dashboard.put("inProgressTasks", tasksByStatus.getOrDefault("IN_PROGRESS", 0L));
////        dashboard.put("completedTasks", tasksByStatus.getOrDefault("COMPLETED", 0L));
////
////        double conversionRate = 0.0;
////        if (!allConsultants.isEmpty()) {
////            long convertedCount = 0;
////            for (ConsultantDetails c : allConsultants) {
////                String status = getLatestConsultantStatus(c.getConsultantId());
////                if ("CONVERTED_TO_EMPLOYEE".equals(status)) {
////                    convertedCount++;
////                }
////            }
////            conversionRate = (double) convertedCount / allConsultants.size() * 100;
////        }
////        dashboard.put("conversionRate", conversionRate);
////
////        return dashboard;
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    private String getLatestConsultantStatus(Long consultantId) {
////        List<ConsultantStatus> statuses = consultantStatusRepository.findByConsultantConsultantId(consultantId);
////        if (statuses == null || statuses.isEmpty()) {
////            return "PENDING";
////        }
////        return statuses.get(statuses.size() - 1).getStatus();
////    }
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
////    // ==================== EMAIL METHODS ====================
////
////    private void sendConsultantApprovalEmail(ConsultantDetails consultant) {
////        try {
////            String subject = "🎉 Congratulations! Your Consultant Application is Approved - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Application Approved</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
////                    .header h1 { color: white; margin: 0; font-size: 32px; }
////                    .content { padding: 40px 30px; background: white; }
////                    .btn { display: inline-block; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; padding: 14px 35px; text-decoration: none; border-radius: 50px; font-weight: bold; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px">
////                        <div class="container">
////                            <div class="header"><h1>🎉 ShardaOS</h1><p>Enterprise Project Management System</p></div>
////                            <div class="content">
////                                <h2>Welcome aboard, %s! 🎊</h2>
////                                <p>Your consultant application has been <strong style="color:#11998e">APPROVED</strong>!</p>
////                                <div style="text-align:center; margin:25px 0"><a href="https://shardaos.com/consultant/dashboard" class="btn">🚀 Go to Dashboard</a></div>
////                                <p>We're excited to have you on board!</p>
////                            </div>
////                            <div class="footer"><p>© 2024 ShardaOS - All Rights Reserved</p></div>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """, consultant.getUser().getName());
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendConsultantRejectionEmail(ConsultantDetails consultant, String reason) {
////        try {
////            String subject = "📢 Update on Your Consultant Application - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Application Update</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: linear-gradient(135deg, #ee5a24 0%, #ff4757 100%); padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px">
////                        <div class="container">
////                            <div class="header"><h1>ShardaOS</h1><p>Application Status Update</p></div>
////                            <div class="content">
////                                <h2>Dear %s,</h2>
////                                <p>After careful evaluation, your application has been <strong style="color:#ff4757">not selected</strong> at this time.</p>
////                                <div class="reason-box"><strong>Feedback:</strong> %s</div>
////                                <p>We encourage you to reapply after 3 months. Best wishes!</p>
////                            </div>
////                            <div class="footer"><p>© 2024 ShardaOS</p></div>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """, consultant.getUser().getName(), reason != null ? reason : "Your profile does not match current requirements");
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendDepartmentAssignmentEmail(ConsultantDetails consultant, Department department, String oldDepartment) {
////        try {
////            String subject = "🏢 Department Assignment Update - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Department Assignment</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%); padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px">
////                        <div class="container">
////                            <div class="header"><h1>🏢 Department Update</h1></div>
////                            <div class="content">
////                                <h2>Dear %s,</h2>
////                                <p>You have been assigned to <strong>%s Department</strong> (Previously: %s)</p>
////                                <p>Please login to view available projects in your new department.</p>
////                            </div>
////                            <div class="footer"><p>© 2024 ShardaOS</p></div>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """, consultant.getUser().getName(), department.getDepartmentName(), oldDepartment);
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendConversionEmail(ConsultantDetails consultant, EmployeeDetails employee, Department department) {
////        try {
////            String subject = "🎉 Congratulations! You're Now a ShardaOS Employee!";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Welcome to ShardaOS Family!</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .btn { display: inline-block; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 14px 35px; text-decoration: none; border-radius: 50px; font-weight: bold; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px">
////                        <div class="container">
////                            <div class="header"><h1>🎉 WELCOME ABOARD!</h1></div>
////                            <div class="content">
////                                <h2>Dear %s,</h2>
////                                <p>You have been converted to <strong>Employee</strong> as <strong>%s</strong> in <strong>%s</strong> department.</p>
////                                <p><strong>Salary:</strong> ₹%.2f</p>
////                                <div style="text-align:center; margin:25px 0"><a href="https://shardaos.com/employee/onboarding" class="btn">🚀 Start Your Journey</a></div>
////                                <p>Congratulations! We're proud to have you on our team! 🎊</p>
////                            </div>
////                            <div class="footer"><p>© 2024 ShardaOS - All Rights Reserved</p></div>
////                        </div>
////                    </div>
////                </body>
////                </html>
////                """, consultant.getUser().getName(), employee.getPositionName(), department.getDepartmentName(), employee.getSalary());
////            emailService.sendEmail(consultant.getUser().getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendEmployeeUpdateEmail(EmployeeDetails employee) {
////        try {
////            String subject = "📝 Profile Update Confirmation - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Profile Updated</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: #4CAF50; padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px"><div class="container"><div class="header"><h1>📝 Profile Update</h1></div>
////                    <div class="content"><h2>Dear %s,</h2><p>Your profile has been updated by HR team.</p>
////                    <p><strong>Position:</strong> %s<br><strong>Department:</strong> %s<br><strong>Skills:</strong> %s</p>
////                    </div><div class="footer"><p>© 2024 ShardaOS</p></div></div></div>
////                </body>
////                </html>
////                """, employee.getUser().getName(), employee.getPositionName(), employee.getDepartment(), employee.getSkillSet());
////            emailService.sendEmail(employee.getUser().getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendTaskAssignmentEmail(User assignedTo, User creator, EmpTask task, LocalDateTime deadline, String departmentName, String role) {
////        try {
////            String deadlineStr = deadline != null ? deadline.format(formatter) : "Not specified";
////            String subject = "📋 New Task Assignment - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>New Task Assigned</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: #FF9800; padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .btn { display: inline-block; background: #FF9800; color: white; padding: 12px 30px; text-decoration: none; border-radius: 50px; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px"><div class="container"><div class="header"><h1>📋 New Task Assignment</h1></div>
////                    <div class="content"><h2>Dear %s,</h2><p>You have a new task from <strong>%s (%s)</strong>.</p>
////                    <p><strong>Task:</strong> %s<br><strong>Department:</strong> %s<br><strong>Deadline:</strong> %s</p>
////                    <div style="text-align:center"><a href="https://shardaos.com/tasks" class="btn">📊 View Task</a></div>
////                    </div><div class="footer"><p>© 2024 ShardaOS</p></div></div></div>
////                </body>
////                </html>
////                """, assignedTo.getName(), creator.getName(), role, task.getDescription(), departmentName, deadlineStr);
////            emailService.sendEmail(assignedTo.getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////
////    private void sendTaskStatusUpdateEmail(EmpTask task, String oldStatus, String newStatus, String note) {
////        try {
////            User creator = task.getCreator();
////            String subject = "🔄 Task Status Update - ShardaOS";
////            String html = String.format("""
////                <!DOCTYPE html>
////                <html>
////                <head><meta charset="UTF-8"><title>Task Status Update</title>
////                <style>
////                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; }
////                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }
////                    .header { background: #2196F3; padding: 40px 30px; text-align: center; }
////                    .content { padding: 40px 30px; background: white; }
////                    .btn { display: inline-block; background: #2196F3; color: white; padding: 12px 30px; text-decoration: none; border-radius: 50px; }
////                    .footer { background: #f8f9fa; padding: 25px 30px; text-align: center; }
////                </style>
////                </head>
////                <body>
////                    <div style="padding:20px"><div class="container"><div class="header"><h1>🔄 Task Status Updated</h1></div>
////                    <div class="content"><h2>Dear %s,</h2><p>Task status updated by <strong>%s</strong>.</p>
////                    <p><strong>Task:</strong> %s<br><strong>Status:</strong> %s → %s<br>%s</p>
////                    <div style="text-align:center"><a href="https://shardaos.com/tasks/%d" class="btn">🔍 View Details</a></div>
////                    </div><div class="footer"><p>© 2024 ShardaOS</p></div></div></div>
////                </body>
////                </html>
////                """, creator.getName(), task.getAssignedTo().getName(), task.getDescription(), oldStatus, newStatus,
////                    note != null ? "<p><strong>Note:</strong> " + note + "</p>" : "", task.getEmpTaskId());
////            emailService.sendEmail(creator.getEmail(), subject, html);
////        } catch (Exception e) { System.out.println("Email error: " + e.getMessage()); }
////    }
////}