package com.softmax.shardaos.AIservice;

import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIDataFetcherService {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private ConsultantReviewRepository consultantReviewRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private TaskDetailsRepository taskDetailsRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    // ==================== CONSULTANT METHODS ====================

    public ConsultantDetails getConsultantById(Long consultantId) {
        return consultantDetailsRepository.findById(consultantId).orElse(null);
    }

    public ConsultantDetails getConsultantByUserId(Long userId) {
        List<ConsultantDetails> consultants = consultantDetailsRepository.findByUserUserId(userId);
        return consultants.isEmpty() ? null : consultants.get(0);
    }

    public String getConsultantBasicInfo(Long loggedInUserId, Long consultantId) {
        ConsultantDetails c = consultantDetailsRepository.findById(consultantId).orElse(null);
        if (c == null) return "Not found";

        if (!canAccessConsultant(loggedInUserId, c)) return "Access denied";

        Double rating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
        return String.format("ID:%d, Name:%s, Dept:%s, Skills:%s, Rating:%.1f",
                c.getConsultantId(),
                c.getUser().getName(),
                c.getDepartment() != null ? c.getDepartment().getDepartmentName() : "Not Assigned",
                c.getSkill() != null ? c.getSkill() : "Not specified",
                rating != null ? rating : 0);
    }

    public String getConsultantTasks(Long loggedInUserId, Long consultantId) {
        ConsultantDetails c = consultantDetailsRepository.findById(consultantId).orElse(null);
        if (c == null || !canAccessConsultant(loggedInUserId, c)) return "No access";

        List<TaskDetails> tasks = taskDetailsRepository.findByUserUserId(c.getUser().getUserId());

        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getTaskStatus())).count();
        long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getTaskStatus())).count();

        return String.format("Total:%d, Completed:%d, InProgress:%d", tasks.size(), completed, inProgress);
    }

    public String getConsultantProjects(Long loggedInUserId, Long consultantId) {
        ConsultantDetails c = consultantDetailsRepository.findById(consultantId).orElse(null);
        if (c == null || !canAccessConsultant(loggedInUserId, c)) return "No access";

        List<TaskDetails> tasks = taskDetailsRepository.findByUserUserId(c.getUser().getUserId());
        long uniqueProjects = tasks.stream().map(t -> t.getProject()).filter(p -> p != null).distinct().count();

        return String.format("Active Projects:%d", uniqueProjects);
    }

    public String getConsultantPerformanceMetrics(Long loggedInUserId, Long consultantId) {
        ConsultantDetails c = consultantDetailsRepository.findById(consultantId).orElse(null);
        if (c == null || !canAccessConsultant(loggedInUserId, c)) return "No access";

        Double rating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
        List<TaskDetails> tasks = taskDetailsRepository.findByUserUserId(c.getUser().getUserId());

        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getTaskStatus())).count();
        double completionRate = tasks.isEmpty() ? 0 : (completed * 100.0 / tasks.size());

        return String.format("Rating:%.1f/5, Completion Rate:%.0f%%", rating != null ? rating : 0, completionRate);
    }

    public String getConsultantTeamInfo(Long loggedInUserId, Long consultantId) {
        ConsultantDetails c = consultantDetailsRepository.findById(consultantId).orElse(null);
        if (c == null || !canAccessConsultant(loggedInUserId, c)) return "No access";

        return String.format("Department:%s", c.getDepartment() != null ? c.getDepartment().getDepartmentName() : "Not Assigned");
    }

    // ==================== DEPARTMENT METHODS ====================

    public String getAllDepartmentsInfo(Long userId) {
        StringBuilder sb = new StringBuilder();

        if (isHeadDirector(userId)) {
            List<Department> departments = departmentRepository.findAll();
            for (Department dept : departments) {
                sb.append("- ID:").append(dept.getDepartmentId()).append(", Name:").append(dept.getDepartmentName()).append("\n");
            }
        } else if (isCoreDirector(userId)) {
            EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
            if (emp != null && emp.getDepartmentRef() != null) {
                sb.append("- ID:").append(emp.getDepartmentRef().getDepartmentId())
                        .append(", Name:").append(emp.getDepartmentRef().getDepartmentName()).append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== MANAGER METHODS ====================

    public String getAllManagersInfo(Long userId) {
        StringBuilder sb = new StringBuilder();
        List<EmployeeDetails> employees = employeeDetailsRepository.findAll();

        for (EmployeeDetails emp : employees) {
            if (isManager(emp.getUser().getUserId()) && emp.getDepartmentRef() != null) {
                if (isHeadDirector(userId)) {
                    addManagerInfo(sb, emp);
                } else if (isCoreDirector(userId) || isHRDirector(userId) || isBDEDirector(userId) || isFinancialDirector(userId)) {
                    EmployeeDetails currentEmp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
                    if (currentEmp != null && currentEmp.getDepartmentRef() != null &&
                            currentEmp.getDepartmentRef().getDepartmentId().equals(emp.getDepartmentRef().getDepartmentId())) {
                        addManagerInfo(sb, emp);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void addManagerInfo(StringBuilder sb, EmployeeDetails emp) {
        Double avgRating = consultantReviewRepository.getAverageRatingByUserId(emp.getUser().getUserId());
        sb.append("- ID:").append(emp.getUser().getUserId())
                .append(", Name:").append(emp.getUser().getName())
                .append(", Dept:").append(emp.getDepartmentRef().getDepartmentName())
                .append(", Rating:").append(avgRating != null ? avgRating : 0)
                .append("\n");
    }

    // ==================== CONSULTANTS LIST METHODS ====================

    public String getAllConsultantsInfo(Long userId) {
        StringBuilder sb = new StringBuilder();
        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();

        for (ConsultantDetails c : consultants) {
            boolean include = false;

            if (isHeadDirector(userId)) {
                include = true;
            } else if (isCoreDirector(userId) || isHRDirector(userId) || isBDEDirector(userId) || isFinancialDirector(userId)) {
                EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
                if (emp != null && emp.getDepartmentRef() != null && c.getDepartment() != null &&
                        emp.getDepartmentRef().getDepartmentId().equals(c.getDepartment().getDepartmentId())) {
                    include = true;
                }
            }

            if (include) {
                Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
                int taskCount = taskDetailsRepository.findByUserUserId(c.getUser().getUserId()).size();
                sb.append("- ID:").append(c.getConsultantId())
                        .append(", Name:").append(c.getUser().getName())
                        .append(", Skills:").append(c.getSkill())
                        .append(", Rating:").append(avgRating != null ? avgRating : 0)
                        .append(", TasksDone:").append(taskCount)
                        .append(", Dept:").append(c.getDepartment() != null ? c.getDepartment().getDepartmentName() : "Not Assigned")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== PROJECT METHODS ====================

    public String getCompletedProjectsInfo(Long userId) {
        StringBuilder sb = new StringBuilder();
        List<ProjectDetails> projects = projectRepository.findAll().stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .collect(Collectors.toList());

        for (ProjectDetails p : projects) {
            boolean include = false;

            if (isHeadDirector(userId)) {
                include = true;
            } else if (isCoreDirector(userId) || isBDEDirector(userId)) {
                EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
                if (emp != null && emp.getDepartmentRef() != null && p.getDepartment() != null &&
                        emp.getDepartmentRef().getDepartmentId().equals(p.getDepartment().getDepartmentId())) {
                    include = true;
                }
            } else if (isFinancialDirector(userId)) {
                include = true;
            }

            if (include) {
                sb.append("- Project:").append(p.getProjectName())
                        .append(", Type:").append(p.getProjectType())
                        .append(", Dept:").append(p.getDepartment() != null ? p.getDepartment().getDepartmentName() : "N/A")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== PERFORMANCE METHODS ====================

    public String getConsultantPerformance(Long userId) {
        StringBuilder sb = new StringBuilder();
        List<ConsultantDetails> consultants = consultantDetailsRepository.findAll();

        for (ConsultantDetails c : consultants) {
            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
            int taskCount = taskDetailsRepository.findByUserUserId(c.getUser().getUserId()).size();
            sb.append("- ").append(c.getUser().getName())
                    .append(" | Rating: ").append(avgRating != null ? avgRating : 0)
                    .append(" | Tasks: ").append(taskCount)
                    .append(" | Skills: ").append(c.getSkill())
                    .append("\n");
        }
        return sb.toString();
    }

    // ==================== ROLE CHECK PRIVATE METHODS ====================

    private boolean canAccessConsultant(Long loggedInUserId, ConsultantDetails consultant) {
        if (isHeadDirector(loggedInUserId)) return true;

        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(loggedInUserId).orElse(null);
        if (emp != null && emp.getDepartmentRef() != null && consultant.getDepartment() != null) {
            return emp.getDepartmentRef().getDepartmentId().equals(consultant.getDepartment().getDepartmentId());
        }
        return false;
    }

    private boolean isHeadDirector(Long userId) {
        List<UserType> types = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = types.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 1L;
    }

    private boolean isHRDirector(Long userId) {
        List<UserType> types = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = types.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 2L;
    }

    private boolean isBDEDirector(Long userId) {
        List<UserType> types = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = types.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 4L;
    }

    private boolean isFinancialDirector(Long userId) {
        List<UserType> types = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = types.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 3L;
    }

    private boolean isCoreDirector(Long userId) {
        List<UserType> types = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = types.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = types.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
        if (!isEmp || !isDirector) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (emp == null || emp.getDepartmentRef() == null) return false;
        Long deptId = emp.getDepartmentRef().getDepartmentId();
        return deptId >= 5;
    }

    private boolean isManager(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "MANAGER".equals(ut.getStype()))
                .isPresent();
    }
}