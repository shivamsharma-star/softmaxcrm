package com.softmax.shardaos.service;

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
public class HeadDirectorService {

    @Autowired private UserRepository userRepository;
    @Autowired private UserTypeRepository userTypeRepository;
    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
    @Autowired private ClientDetailsRepository clientDetailsRepository;
    @Autowired private ProjectDetailsRepository projectRepository;
    @Autowired private ProjectStatusRepository projectStatusRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TaskDetailsRepository taskRepository;
    @Autowired private ConsultantReviewRepository consultantReviewRepository;
    @Autowired private TeamDetailsRepository teamRepository;
    @Autowired private TeamMembersRepository teamMembersRepository;
    @Autowired private EmpTaskRepository empTaskRepository;

    private static final Long HEAD_DEPARTMENT_ID = 1L;

    public boolean isHeadDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = false, isDirector = false;
        for (UserType ut : userTypes) {
            if ("EMP".equals(ut.getPtype())) isEmp = true;
            if ("DIRECTOR".equals(ut.getStype())) isDirector = true;
        }
        if (!isEmp || !isDirector) return false;
        Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(userId);
        return emp.isPresent() && emp.get().getDepartmentRef() != null &&
                HEAD_DEPARTMENT_ID.equals(emp.get().getDepartmentRef().getDepartmentId());
    }

    private void validate(Long userId) {
        if (!isHeadDirector(userId)) throw new RuntimeException("Access denied: Only Head Director can access this resource");
    }

    // ==================== USERS ====================

    public AllUsersResponseDTO getAllUsers(Long userId) {
        validate(userId);
        List<User> users = userRepository.findAll();
        List<UserWithRolesDTO> list = new ArrayList<>();
        int empCount = 0, conCount = 0, cliCount = 0;

        for (User u : users) {
            List<String> roles = new ArrayList<>();
            for (UserType ut : userTypeRepository.findByUserUserId(u.getUserId())) {
                roles.add(ut.getPtype() + (ut.getStype() != null ? ":" + ut.getStype() : ""));
            }
            String dept = null, pos = null;
            Optional<EmployeeDetails> e = employeeDetailsRepository.findByUserUserId(u.getUserId());
            if (e.isPresent()) {
                empCount++;
                if (e.get().getDepartmentRef() != null) dept = e.get().getDepartmentRef().getDepartmentName();
                pos = e.get().getPositionName();
            }
            if (!consultantDetailsRepository.findByUserUserId(u.getUserId()).isEmpty()) conCount++;
            if (clientDetailsRepository.findByUserUserId(u.getUserId()).isPresent()) cliCount++;

            UserWithRolesDTO dto = UserWithRolesDTO.builder()
                    .userId(u.getUserId()).name(u.getName()).email(u.getEmail())
                    .photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
                    .address(u.getAddress()).gender(u.getGender()).roles(roles)
                    .department(dept).position(pos).isActive(true).joinedAt(u.getCreatedAt())
                    .build();
            list.add(dto);
        }
        return new AllUsersResponseDTO(list, users.size(), empCount, conCount, cliCount);
    }

    public UserWithRolesDTO getUserById(Long userId, Long targetId) {
        validate(userId);
        User u = userRepository.findById(targetId).orElseThrow(() -> new RuntimeException("User not found"));
        List<String> roles = new ArrayList<>();
        for (UserType ut : userTypeRepository.findByUserUserId(targetId)) {
            roles.add(ut.getPtype() + (ut.getStype() != null ? ":" + ut.getStype() : ""));
        }
        String dept = null, pos = null;
        Optional<EmployeeDetails> e = employeeDetailsRepository.findByUserUserId(targetId);
        if (e.isPresent() && e.get().getDepartmentRef() != null) {
            dept = e.get().getDepartmentRef().getDepartmentName();
            pos = e.get().getPositionName();
        }
        return UserWithRolesDTO.builder()
                .userId(u.getUserId()).name(u.getName()).email(u.getEmail())
                .photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
                .address(u.getAddress()).gender(u.getGender()).roles(roles)
                .department(dept).position(pos).isActive(true).joinedAt(u.getCreatedAt())
                .build();
    }

    // ==================== DEPARTMENT CRUD ====================

    public List<Department> getAllDepartments(Long userId) {
        validate(userId);
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(Long userId, Long deptId) {
        validate(userId);
        return departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    @Transactional
    public Department createDepartment(Long userId, String departmentName) {
        validate(userId);
        Department department = new Department();
        department.setDepartmentName(departmentName);
        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(Long userId, Long deptId, String departmentName) {
        validate(userId);
        Department existing = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        existing.setDepartmentName(departmentName);
        return departmentRepository.save(existing);
    }

    @Transactional
    public void deleteDepartment(Long userId, Long deptId) {
        validate(userId);
        Department dept = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        departmentRepository.delete(dept);
    }

    // ==================== DEPARTMENT KPIs ====================

    public List<DepartmentKPISummaryDTO> getAllDepartmentsWithKPIs(Long userId) {
        validate(userId);
        List<DepartmentKPISummaryDTO> result = new ArrayList<>();
        for (Department dept : departmentRepository.findAll()) {
            List<EmployeeDetails> emps = employeeDetailsRepository.findByDepartmentRefDepartmentId(dept.getDepartmentId());
            List<ProjectDetails> projs = projectRepository.findByDepartmentDepartmentId(dept.getDepartmentId());
            long completed = projs.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
            double rate = projs.isEmpty() ? 0 : (completed * 100.0) / projs.size();
            long mgrCount = emps.stream().filter(e -> isManager(e.getUser().getUserId())).count();

            result.add(new DepartmentKPISummaryDTO(
                    dept.getDepartmentId(), dept.getDepartmentName(), emps.size(),
                    (int) mgrCount, projs.size(), (int) completed, rate, 0.0, 0.0));
        }
        return result;
    }

    // ==================== PROJECT MANAGEMENT ====================

    public List<ProjectResponseDTO> getAllProjects(Long userId) {
        validate(userId);
        return projectRepository.findAll().stream().map(this::toProjectDTO).collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> getProjectsByDepartment(Long userId, Long deptId) {
        validate(userId);
        return projectRepository.findByDepartmentDepartmentId(deptId).stream().map(this::toProjectDTO).collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> getProjectsByStatus(Long userId, String status) {
        validate(userId);
        return projectRepository.findAll().stream()
                .filter(p -> p.getStatus().equalsIgnoreCase(status))
                .map(this::toProjectDTO)
                .collect(Collectors.toList());
    }

    public ProjectResponseDTO getProjectById(Long userId, Long projectId) {
        validate(userId);
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return toProjectDTO(project);
    }

    @Transactional
    public ProjectResponseDTO updateProjectStatus(Long userId, Long projectId, String status, String note) {
        validate(userId);
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        project.setStatus(status);
        ProjectDetails updated = projectRepository.save(project);

        ProjectStatus statusRecord = new ProjectStatus();
        statusRecord.setProject(project);
        statusRecord.setStatus(status);
        statusRecord.setDateTime(LocalDateTime.now());
        statusRecord.setHandledBy(userRepository.findById(userId).get());
        statusRecord.setDirectorNote(note);
        projectStatusRepository.save(statusRecord);

        return toProjectDTO(updated);
    }

    public List<ProjectStatusDTO> getProjectHistory(Long userId, Long projectId) {
        validate(userId);
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        return projectStatusRepository.findByProjectProjectId(projectId)
                .stream().map(this::toStatusDTO).collect(Collectors.toList());
    }

    // ==================== PROJECT TEAMS ====================

    public List<TeamResponseDTO> getAllProjectTeams(Long userId) {
        validate(userId);
        List<TeamResponseDTO> allTeams = new ArrayList<>();

        for (TeamDetails team : teamRepository.findAll()) {
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

            allTeams.add(TeamResponseDTO.builder()
                    .teamId(team.getTeamId())
                    .teamName(team.getTeamName())
                    .projectId(team.getProject().getProjectId())
                    .projectName(team.getProject().getProjectName())
                    .managerId(team.getManager().getUserId())
                    .managerName(team.getManager().getName())
                    .members(memberDTOs)
                    .createdAt(team.getCreatedAt())
                    .build());
        }
        return allTeams;
    }

    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
        validate(userId);
        return teamRepository.findByProjectProjectId(projectId).stream().map(team -> {
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

    // ==================== TASKS MANAGEMENT ====================

    public List<HRAssignedTaskResponseDTO> getAllTasks(Long userId) {
        validate(userId);
        List<HRAssignedTaskResponseDTO> allTasks = new ArrayList<>();

        for (EmpTask task : empTaskRepository.findAll()) {
            String departmentName = getDepartmentNameForUser(task.getAssignedTo().getUserId());

            allTasks.add(HRAssignedTaskResponseDTO.builder()
                    .taskId(task.getEmpTaskId())
                    .description(task.getDescription())
                    .status(task.getStatus())
                    .assignedToUserId(task.getAssignedTo().getUserId())
                    .assignedToUserName(task.getAssignedTo().getName())
                    .assignedToDepartment(departmentName)
                    .createdByUserId(task.getCreator().getUserId())
                    .createdByName(task.getCreator().getName())
                    .deadline(task.getDeadline())
                    .createdAt(task.getCreatedAt())
                    .updatedAt(task.getUpdatedAt())
                    .build());
        }
        return allTasks;
    }

    public List<HRAssignedTaskResponseDTO> getTasksByProject(Long userId, Long projectId) {
        validate(userId);
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<TeamDetails> teams = teamRepository.findByProjectProjectId(projectId);
        Set<Long> teamMemberIds = new HashSet<>();

        for (TeamDetails team : teams) {
            List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
            for (TeamMembers m : members) {
                teamMemberIds.add(m.getMember().getUserId());
            }
        }

        List<HRAssignedTaskResponseDTO> result = new ArrayList<>();
        for (EmpTask task : empTaskRepository.findAll()) {
            if (teamMemberIds.contains(task.getAssignedTo().getUserId())) {
                String departmentName = getDepartmentNameForUser(task.getAssignedTo().getUserId());
                result.add(HRAssignedTaskResponseDTO.builder()
                        .taskId(task.getEmpTaskId())
                        .description(task.getDescription())
                        .status(task.getStatus())
                        .assignedToUserId(task.getAssignedTo().getUserId())
                        .assignedToUserName(task.getAssignedTo().getName())
                        .assignedToDepartment(departmentName)
                        .createdByUserId(task.getCreator().getUserId())
                        .createdByName(task.getCreator().getName())
                        .deadline(task.getDeadline())
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .build());
            }
        }
        return result;
    }

    @Transactional
    public HRAssignedTaskResponseDTO updateTaskStatus(Long userId, Long taskId, String status, String note) {
        validate(userId);
        EmpTask task = empTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!status.matches("PENDING|IN_PROGRESS|COMPLETED")) {
            throw new RuntimeException("Invalid status. Allowed: PENDING, IN_PROGRESS, COMPLETED");
        }

        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        EmpTask updatedTask = empTaskRepository.save(task);

        String departmentName = getDepartmentNameForUser(task.getAssignedTo().getUserId());

        return HRAssignedTaskResponseDTO.builder()
                .taskId(updatedTask.getEmpTaskId())
                .description(updatedTask.getDescription())
                .status(updatedTask.getStatus())
                .assignedToUserId(updatedTask.getAssignedTo().getUserId())
                .assignedToUserName(updatedTask.getAssignedTo().getName())
                .assignedToDepartment(departmentName)
                .createdByUserId(updatedTask.getCreator().getUserId())
                .createdByName(updatedTask.getCreator().getName())
                .deadline(updatedTask.getDeadline())
                .createdAt(updatedTask.getCreatedAt())
                .updatedAt(updatedTask.getUpdatedAt())
                .build();
    }

    // ==================== DASHBOARD ====================

    public HeadDirectorKPIDTO getDashboardKPIs(Long userId) {
        validate(userId);
        List<EmployeeDetails> emps = employeeDetailsRepository.findAll();
        List<ConsultantDetails> cons = consultantDetailsRepository.findAll();
        List<ClientDetails> clients = clientDetailsRepository.findAll();
        List<ProjectDetails> projects = projectRepository.findAll();

        long completed = projects.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long inProgress = projects.stream().filter(p -> "IN_PROGRESS".equals(p.getStatus())).count();
        long pending = projects.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

        long mgrCount = emps.stream().filter(e -> isManager(e.getUser().getUserId())).count();
        double rate = projects.isEmpty() ? 0 : (completed * 100.0) / projects.size();

        CompanySummaryDTO summary = new CompanySummaryDTO(
                emps.size(), (int) mgrCount, cons.size(), clients.size(),
                projects.size(), (int) completed, (int) inProgress, (int) pending,
                rate, 0.0, 0.0, 0.0);

        return HeadDirectorKPIDTO.builder()
                .companySummary(summary)
                .departmentKPISummary(getAllDepartmentsWithKPIs(userId))
                .monthlyTrends(getMonthlyTrends())
                .recentActivities(getRecentActivities())
                .topPerformers(getTopPerformers())
                .build();
    }

    // ==================== HELPERS ====================

    private boolean isManager(Long uid) {
        Optional<UserType> ut = userTypeRepository.findByUserUserIdAndPtype(uid, "EMP");
        return ut.isPresent() && "MANAGER".equals(ut.get().getStype());
    }

    private String getDepartmentNameForUser(Long userId) {
        Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(userId);
        if (emp.isPresent() && emp.get().getDepartmentRef() != null) {
            return emp.get().getDepartmentRef().getDepartmentName();
        }
        return "Unknown";
    }

    private ProjectResponseDTO toProjectDTO(ProjectDetails p) {
        return ProjectResponseDTO.builder()
                .projectId(p.getProjectId()).projectName(p.getProjectName())
                .projectDescription(p.getProjectDescription()).projectType(p.getProjectType())
                .status(p.getStatus())
                .departmentId(p.getDepartment() != null ? p.getDepartment().getDepartmentId() : null)
                .departmentName(p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null)
                .clientId(p.getUploader().getUserId()).clientName(p.getUploader().getName())
                .clientEmail(p.getUploader().getEmail())
                .assignedManagerId(p.getAssignedManager() != null ? p.getAssignedManager().getUserId() : null)
                .assignedManagerName(p.getAssignedManager() != null ? p.getAssignedManager().getName() : null)
                .deadline(p.getDeadline()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }

    private ProjectStatusDTO toStatusDTO(ProjectStatus status) {
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

    private List<MonthlyCompanyTrendDTO> getMonthlyTrends() {
        List<MonthlyCompanyTrendDTO> trends = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        for (int i = 5; i >= 0; i--) {
            LocalDateTime start = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0);
            LocalDateTime end = start.plusMonths(1);
            int newEmps = 0, newClients = 0, newProjects = 0, completed = 0;
            for (EmployeeDetails e : employeeDetailsRepository.findAll())
                if (e.getCreatedAt().isAfter(start) && e.getCreatedAt().isBefore(end)) newEmps++;
            for (ClientDetails c : clientDetailsRepository.findAll())
                if (c.getCreatedAt().isAfter(start) && c.getCreatedAt().isBefore(end)) newClients++;
            for (ProjectDetails p : projectRepository.findAll())
                if (p.getCreatedAt().isAfter(start) && p.getCreatedAt().isBefore(end)) newProjects++;
            for (ProjectStatus ps : projectStatusRepository.findAll())
                if ("COMPLETED".equals(ps.getStatus()) && ps.getDateTime().isAfter(start) && ps.getDateTime().isBefore(end)) completed++;
            trends.add(new MonthlyCompanyTrendDTO(start.format(fmt), newEmps, newClients, newProjects, completed, 0.0, 0.0));
        }
        return trends;
    }

    private List<RecentActivityDTO> getRecentActivities() {
        List<RecentActivityDTO> acts = new ArrayList<>();
        LocalDateTime seven = LocalDateTime.now().minusDays(7);
        for (ProjectDetails p : projectRepository.findAll()) {
            if (p.getCreatedAt().isAfter(seven)) {
                acts.add(new RecentActivityDTO("PROJECT_CREATED", "New project: " + p.getProjectName(),
                        p.getUploader().getName(), p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null, p.getCreatedAt()));
            }
        }
        for (ProjectStatus ps : projectStatusRepository.findAll()) {
            if (ps.getDateTime().isAfter(seven)) {
                acts.add(new RecentActivityDTO("PROJECT_STATUS_CHANGED",
                        "Project " + ps.getProject().getProjectName() + " → " + ps.getStatus(),
                        ps.getHandledBy() != null ? ps.getHandledBy().getName() : "System",
                        ps.getProject().getDepartment() != null ? ps.getProject().getDepartment().getDepartmentName() : null,
                        ps.getDateTime()));
            }
        }
        acts.sort((a,b) -> b.getActivityTime().compareTo(a.getActivityTime()));
        return acts.size() > 20 ? acts.subList(0, 20) : acts;
    }

    private TopPerformersDTO getTopPerformers() {
        Map<Long, Integer> taskCount = new HashMap<>();
        for (TaskDetails t : taskRepository.findAll()) {
            if ("COMPLETED".equals(t.getTaskStatus())) {
                taskCount.put(t.getUser().getUserId(), taskCount.getOrDefault(t.getUser().getUserId(), 0) + 1);
            }
        }
        List<TopEmployeeDTO> topEmps = new ArrayList<>();
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(taskCount.entrySet());
        sorted.sort((a,b) -> b.getValue().compareTo(a.getValue()));
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            Map.Entry<Long,Integer> e = sorted.get(i);
            Optional<User> u = userRepository.findById(e.getKey());
            if (u.isPresent()) {
                Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(e.getKey());
                Double avg = consultantReviewRepository.getAverageRatingByUserId(e.getKey());
                topEmps.add(new TopEmployeeDTO(e.getKey(), u.get().getName(),
                        emp.isPresent() ? emp.get().getPositionName() : null,
                        emp.isPresent() && emp.get().getDepartmentRef() != null ? emp.get().getDepartmentRef().getDepartmentName() : null,
                        e.getValue(), avg != null ? avg : 0));
            }
        }
        return new TopPerformersDTO(topEmps, new ArrayList<>(), new ArrayList<>());
    }
}














//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class HeadDirectorService {
//
//    @Autowired private UserRepository userRepository;
//    @Autowired private UserTypeRepository userTypeRepository;
//    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
//    @Autowired private ConsultantDetailsRepository consultantDetailsRepository;
//    @Autowired private ClientDetailsRepository clientDetailsRepository;
//    @Autowired private ProjectDetailsRepository projectRepository;
//    @Autowired private ProjectStatusRepository projectStatusRepository;
//    @Autowired private DepartmentRepository departmentRepository;
//    @Autowired private TaskDetailsRepository taskRepository;
//    @Autowired private ConsultantReviewRepository consultantReviewRepository;
//
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    public boolean isHeadDirector(Long userId) {
//        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//        boolean isEmp = false, isDirector = false;
//        for (UserType ut : userTypes) {
//            if ("EMP".equals(ut.getPtype())) isEmp = true;
//            if ("DIRECTOR".equals(ut.getStype())) isDirector = true;
//        }
//        if (!isEmp || !isDirector) return false;
//        Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(userId);
//        return emp.isPresent() && emp.get().getDepartmentRef() != null &&
//                HEAD_DEPARTMENT_ID.equals(emp.get().getDepartmentRef().getDepartmentId());
//    }
//
//    private void validate(Long userId) {
//        if (!isHeadDirector(userId)) throw new RuntimeException("Access denied: Only Head Director can access this resource");
//    }
//
//    // ==================== USERS ====================
//
//    public AllUsersResponseDTO getAllUsers(Long userId) {
//        validate(userId);
//        List<User> users = userRepository.findAll();
//        List<UserWithRolesDTO> list = new ArrayList<>();
//        int empCount = 0, conCount = 0, cliCount = 0;
//
//        for (User u : users) {
//            List<String> roles = new ArrayList<>();
//            for (UserType ut : userTypeRepository.findByUserUserId(u.getUserId())) {
//                roles.add(ut.getPtype() + (ut.getStype() != null ? ":" + ut.getStype() : ""));
//            }
//            String dept = null, pos = null;
//            Optional<EmployeeDetails> e = employeeDetailsRepository.findByUserUserId(u.getUserId());
//            if (e.isPresent()) {
//                empCount++;
//                if (e.get().getDepartmentRef() != null) dept = e.get().getDepartmentRef().getDepartmentName();
//                pos = e.get().getPositionName();
//            }
//            if (!consultantDetailsRepository.findByUserUserId(u.getUserId()).isEmpty()) conCount++;
//            if (clientDetailsRepository.findByUserUserId(u.getUserId()).isPresent()) cliCount++;
//
//            UserWithRolesDTO dto = UserWithRolesDTO.builder()
//                    .userId(u.getUserId()).name(u.getName()).email(u.getEmail())
//                    .photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
//                    .address(u.getAddress()).gender(u.getGender()).roles(roles)
//                    .department(dept).position(pos).isActive(true).joinedAt(u.getCreatedAt())
//                    .build();
//            list.add(dto);
//        }
//        return new AllUsersResponseDTO(list, users.size(), empCount, conCount, cliCount);
//    }
//
//    public UserWithRolesDTO getUserById(Long userId, Long targetId) {
//        validate(userId);
//        User u = userRepository.findById(targetId).orElseThrow(() -> new RuntimeException("User not found"));
//        List<String> roles = new ArrayList<>();
//        for (UserType ut : userTypeRepository.findByUserUserId(targetId)) {
//            roles.add(ut.getPtype() + (ut.getStype() != null ? ":" + ut.getStype() : ""));
//        }
//        String dept = null, pos = null;
//        Optional<EmployeeDetails> e = employeeDetailsRepository.findByUserUserId(targetId);
//        if (e.isPresent() && e.get().getDepartmentRef() != null) {
//            dept = e.get().getDepartmentRef().getDepartmentName();
//            pos = e.get().getPositionName();
//        }
//        return UserWithRolesDTO.builder()
//                .userId(u.getUserId()).name(u.getName()).email(u.getEmail())
//                .photoUrl(u.getPhotoUrl()).phoneNumber(u.getPhoneNumber())
//                .address(u.getAddress()).gender(u.getGender()).roles(roles)
//                .department(dept).position(pos).isActive(true).joinedAt(u.getCreatedAt())
//                .build();
//    }
//
//    // ==================== DEPARTMENTS ====================
//
//    public List<DepartmentKPISummaryDTO> getAllDepartmentsWithKPIs(Long userId) {
//        validate(userId);
//        List<DepartmentKPISummaryDTO> result = new ArrayList<>();
//        for (Department dept : departmentRepository.findAll()) {
//            List<EmployeeDetails> emps = employeeDetailsRepository.findByDepartmentRefDepartmentId(dept.getDepartmentId());
//            List<ProjectDetails> projs = projectRepository.findByDepartmentDepartmentId(dept.getDepartmentId());
//            long completed = projs.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
//            double rate = projs.isEmpty() ? 0 : (completed * 100.0) / projs.size();
//            long mgrCount = emps.stream().filter(e -> isManager(e.getUser().getUserId())).count();
//
//            result.add(new DepartmentKPISummaryDTO(
//                    dept.getDepartmentId(), dept.getDepartmentName(), emps.size(),
//                    (int) mgrCount, projs.size(), (int) completed, rate, 0.0, 0.0));
//        }
//        return result;
//    }
//
//    // ==================== PROJECTS ====================
//
//    public List<ProjectResponseDTO> getAllProjects(Long userId) {
//        validate(userId);
//        return projectRepository.findAll().stream().map(this::toProjectDTO).collect(Collectors.toList());
//    }
//
//    public List<ProjectResponseDTO> getProjectsByDepartment(Long userId, Long deptId) {
//        validate(userId);
//        return projectRepository.findByDepartmentDepartmentId(deptId).stream().map(this::toProjectDTO).collect(Collectors.toList());
//    }
//
//    public List<ProjectResponseDTO> getProjectsByStatus(Long userId, String status) {
//        validate(userId);
//        return projectRepository.findAll().stream()
//                .filter(p -> p.getStatus().equalsIgnoreCase(status))
//                .map(this::toProjectDTO)
//                .collect(Collectors.toList());
//    }
//
//    // ==================== DASHBOARD ====================
//
//    public HeadDirectorKPIDTO getDashboardKPIs(Long userId) {
//        validate(userId);
//        List<EmployeeDetails> emps = employeeDetailsRepository.findAll();
//        List<ConsultantDetails> cons = consultantDetailsRepository.findAll();
//        List<ClientDetails> clients = clientDetailsRepository.findAll();
//        List<ProjectDetails> projects = projectRepository.findAll();
//
//        long completed = projects.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
//        long inProgress = projects.stream().filter(p -> "IN_PROGRESS".equals(p.getStatus())).count();
//        long pending = projects.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
//
//        long mgrCount = emps.stream().filter(e -> isManager(e.getUser().getUserId())).count();
//        double rate = projects.isEmpty() ? 0 : (completed * 100.0) / projects.size();
//
//        CompanySummaryDTO summary = new CompanySummaryDTO(
//                emps.size(), (int) mgrCount, cons.size(), clients.size(),
//                projects.size(), (int) completed, (int) inProgress, (int) pending,
//                rate, 0.0, 0.0, 0.0);
//
//        return HeadDirectorKPIDTO.builder()
//                .companySummary(summary)
//                .departmentKPISummary(getAllDepartmentsWithKPIs(userId))
//                .monthlyTrends(getMonthlyTrends())
//                .recentActivities(getRecentActivities())
//                .topPerformers(getTopPerformers())
//                .build();
//    }
//
//    // ==================== HELPERS ====================
//
//    private boolean isManager(Long uid) {
//        Optional<UserType> ut = userTypeRepository.findByUserUserIdAndPtype(uid, "EMP");
//        return ut.isPresent() && "MANAGER".equals(ut.get().getStype());
//    }
//
//    private ProjectResponseDTO toProjectDTO(ProjectDetails p) {
//        return ProjectResponseDTO.builder()
//                .projectId(p.getProjectId()).projectName(p.getProjectName())
//                .projectDescription(p.getProjectDescription()).projectType(p.getProjectType())
//                .status(p.getStatus())
//                .departmentId(p.getDepartment() != null ? p.getDepartment().getDepartmentId() : null)
//                .departmentName(p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null)
//                .clientId(p.getUploader().getUserId()).clientName(p.getUploader().getName())
//                .clientEmail(p.getUploader().getEmail())
//                .assignedManagerId(p.getAssignedManager() != null ? p.getAssignedManager().getUserId() : null)
//                .assignedManagerName(p.getAssignedManager() != null ? p.getAssignedManager().getName() : null)
//                .deadline(p.getDeadline()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
//                .build();
//    }
//
//    private List<MonthlyCompanyTrendDTO> getMonthlyTrends() {
//        List<MonthlyCompanyTrendDTO> trends = new ArrayList<>();
//        LocalDateTime now = LocalDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
//        for (int i = 5; i >= 0; i--) {
//            LocalDateTime start = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0);
//            LocalDateTime end = start.plusMonths(1);
//            int newEmps = 0, newClients = 0, newProjects = 0, completed = 0;
//            for (EmployeeDetails e : employeeDetailsRepository.findAll())
//                if (e.getCreatedAt().isAfter(start) && e.getCreatedAt().isBefore(end)) newEmps++;
//            for (ClientDetails c : clientDetailsRepository.findAll())
//                if (c.getCreatedAt().isAfter(start) && c.getCreatedAt().isBefore(end)) newClients++;
//            for (ProjectDetails p : projectRepository.findAll())
//                if (p.getCreatedAt().isAfter(start) && p.getCreatedAt().isBefore(end)) newProjects++;
//            for (ProjectStatus ps : projectStatusRepository.findAll())
//                if ("COMPLETED".equals(ps.getStatus()) && ps.getDateTime().isAfter(start) && ps.getDateTime().isBefore(end)) completed++;
//            trends.add(new MonthlyCompanyTrendDTO(start.format(fmt), newEmps, newClients, newProjects, completed, 0.0, 0.0));
//        }
//        return trends;
//    }
//
//    private List<RecentActivityDTO> getRecentActivities() {
//        List<RecentActivityDTO> acts = new ArrayList<>();
//        LocalDateTime seven = LocalDateTime.now().minusDays(7);
//        for (ProjectDetails p : projectRepository.findAll()) {
//            if (p.getCreatedAt().isAfter(seven)) {
//                acts.add(new RecentActivityDTO("PROJECT_CREATED", "New project: " + p.getProjectName(),
//                        p.getUploader().getName(), p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null, p.getCreatedAt()));
//            }
//        }
//        for (ProjectStatus ps : projectStatusRepository.findAll()) {
//            if (ps.getDateTime().isAfter(seven)) {
//                acts.add(new RecentActivityDTO("PROJECT_STATUS_CHANGED",
//                        "Project " + ps.getProject().getProjectName() + " → " + ps.getStatus(),
//                        ps.getHandledBy() != null ? ps.getHandledBy().getName() : "System",
//                        ps.getProject().getDepartment() != null ? ps.getProject().getDepartment().getDepartmentName() : null,
//                        ps.getDateTime()));
//            }
//        }
//        acts.sort((a,b) -> b.getActivityTime().compareTo(a.getActivityTime()));
//        return acts.size() > 20 ? acts.subList(0, 20) : acts;
//    }
//
//    private TopPerformersDTO getTopPerformers() {
//        Map<Long, Integer> taskCount = new HashMap<>();
//        for (TaskDetails t : taskRepository.findAll()) {
//            if ("COMPLETED".equals(t.getTaskStatus())) {
//                taskCount.put(t.getUser().getUserId(), taskCount.getOrDefault(t.getUser().getUserId(), 0) + 1);
//            }
//        }
//        List<TopEmployeeDTO> topEmps = new ArrayList<>();
//        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(taskCount.entrySet());
//        sorted.sort((a,b) -> b.getValue().compareTo(a.getValue()));
//        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
//            Map.Entry<Long,Integer> e = sorted.get(i);
//            Optional<User> u = userRepository.findById(e.getKey());
//            if (u.isPresent()) {
//                Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(e.getKey());
//                Double avg = consultantReviewRepository.getAverageRatingByUserId(e.getKey());
//                topEmps.add(new TopEmployeeDTO(e.getKey(), u.get().getName(),
//                        emp.isPresent() ? emp.get().getPositionName() : null,
//                        emp.isPresent() && emp.get().getDepartmentRef() != null ? emp.get().getDepartmentRef().getDepartmentName() : null,
//                        e.getValue(), avg != null ? avg : 0));
//            }
//        }
//        return new TopPerformersDTO(topEmps, new ArrayList<>(), new ArrayList<>());
//    }
//}
//
//
//
