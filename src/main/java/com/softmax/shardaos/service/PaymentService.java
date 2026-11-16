package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
import com.softmax.shardaos.dto.input.CreatePaymentRequest;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    @Autowired
    private PaymentDetailsRepository paymentRepository;

    @Autowired
    private ProjectPaymentRepository projectPaymentRepository;

    @Autowired
    private SalaryPaymentRepository salaryPaymentRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private EmailService emailService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final Long HEAD_DEPARTMENT_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Helper method to print debug response
    private void printDebugResponse(String methodName, Object response) {
        try {
            System.out.println("\n========== DEBUG RESPONSE ==========");
            System.out.println("Method: " + methodName);
            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            System.out.println("Response JSON:");
            System.out.println(jsonResponse);
            System.out.println("=====================================\n");
        } catch (Exception e) {
            System.out.println("Error printing debug response: " + e.getMessage());
        }
    }

    private double safeParseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String cleaned = value.toString().replaceAll("[^\\d.]", "");
            if (cleaned.isEmpty() || cleaned.equals(".")) {
                return 0.0;
            }
            int firstDot = cleaned.indexOf('.');
            if (firstDot != -1) {
                cleaned = cleaned.substring(0, firstDot + 1) +
                        cleaned.substring(firstDot + 1).replaceAll("\\.", "");
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            System.out.println("Error parsing double: " + value);
            return 0.0;
        }
    }

    private String formatAmount(double amount) {
        return String.format("%.2f", amount);
    }

    // ==================== VALIDATION ====================

    public boolean isHeadDirector(Long userId) {
        if (userId == null) return false;
        try {
            List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
            boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
            boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
            if (!isEmp || !isDirector) return false;
            EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
            return emp != null && emp.getDepartmentRef() != null &&
                    HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
        } catch (Exception e) {
            System.out.println("Error checking Head Director: " + e.getMessage());
            return false;
        }
    }

    private void validateHeadDirector(Long userId) {
        if (userId == null) {
            throw new RuntimeException("Authentication required: User ID is null");
        }
        if (!isHeadDirector(userId)) {
            throw new RuntimeException("Access denied: Only Head Director can manage payments");
        }
    }

    private void validateProjectOwner(Long userId, Long projectId) {
        if (userId == null) {
            throw new RuntimeException("Authentication required: User ID is null");
        }
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        if (project.getUploader() == null || !project.getUploader().getUserId().equals(userId)) {
            throw new RuntimeException("Access denied: You can only access your own projects");
        }
    }

    // ==================== CLIENT APIs ====================

    public List<PaymentResponseDTO> getMyProjectPayments(Long userId, Long projectId) {
        if (userId == null) throw new RuntimeException("User ID is required");
        validateProjectOwner(userId, projectId);
        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
        List<PaymentResponseDTO> response = payments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        printDebugResponse("getMyProjectPayments", response);
        return response;
    }

    public ProjectPaymentSummaryDTO getMyProjectPaymentSummary(Long userId, Long projectId) {
        if (userId == null) throw new RuntimeException("User ID is required");
        validateProjectOwner(userId, projectId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);

        double totalProjectAmount = safeParseDouble(project.getCost());
        double paidAmount = totalApproved != null ? totalApproved : 0.0;
        double remainingAmount = totalProjectAmount - paidAmount;
        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;

        List<PaymentResponseDTO> approvedPayments = payments.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .totalProjectAmount(formatAmount(totalProjectAmount))
                .paidAmount(formatAmount(paidAmount))
                .remainingAmount(formatAmount(remainingAmount))
                .paidPercentage(paidPercentage)
                .isFullyPaid(remainingAmount <= 0)
                .payments(approvedPayments)
                .build();

        printDebugResponse("getMyProjectPaymentSummary", response);
        return response;
    }

    public List<ProjectPaymentSummaryDTO> getAllMyProjectsPayments(Long userId) {
        if (userId == null) throw new RuntimeException("User ID is required");
        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
        List<ProjectPaymentSummaryDTO> response = new ArrayList<>();

        for (ProjectDetails project : myProjects) {
            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double totalProjectAmount = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = totalProjectAmount - paidAmount;
            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;

            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName())
                    .totalProjectAmount(formatAmount(totalProjectAmount))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(paidPercentage)
                    .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
                    .payments(new ArrayList<>())
                    .build();

            response.add(summary);
        }

        response.sort((a, b) -> a.getProjectName().compareTo(b.getProjectName()));
        printDebugResponse("getAllMyProjectsPayments", response);
        return response;
    }

    public PaymentResponseDTO getMyPaymentByTransactionId(Long userId, String transactionId) {
        if (userId == null) throw new RuntimeException("User ID is required");
        if (transactionId == null) throw new RuntimeException("Transaction ID is required");

        PaymentDetails payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for transaction: " + transactionId));

        if ("PROJECT_PAYMENT".equals(payment.getType())) {
            Optional<ProjectPayment> projectPaymentOpt = projectPaymentRepository.findByPayment(payment);
            if (projectPaymentOpt.isPresent()) {
                ProjectPayment projectPayment = projectPaymentOpt.get();
                validateProjectOwner(userId, projectPayment.getProject().getProjectId());
                return convertToDTO(payment);
            } else {
                throw new RuntimeException("Project payment mapping not found");
            }
        } else {
            throw new RuntimeException("Invalid payment type for client access");
        }
    }

    public ClientPaymentDashboardDTO getMyPaymentDashboard(Long userId) {
        if (userId == null) throw new RuntimeException("User ID is required");
        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);

        double totalProjectCost = 0.0;
        double totalPaidAmount = 0.0;
        int totalProjects = myProjects.size();
        int completedProjects = 0;
        int pendingProjects = 0;

        List<ProjectPaymentSummaryDTO> projectSummaries = new ArrayList<>();

        for (ProjectDetails project : myProjects) {
            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double projectCost = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = projectCost - paidAmount;

            totalProjectCost += projectCost;
            totalPaidAmount += paidAmount;

            if (remainingAmount <= 0 && projectCost > 0) {
                completedProjects++;
            } else if (projectCost > 0) {
                pendingProjects++;
            }

            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName())
                    .totalProjectAmount(formatAmount(projectCost))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(projectCost > 0 ? (paidAmount / projectCost) * 100 : 0)
                    .isFullyPaid(remainingAmount <= 0 && projectCost > 0)
                    .build();

            projectSummaries.add(summary);
        }

        double totalRemaining = totalProjectCost - totalPaidAmount;
        double overallPaidPercentage = totalProjectCost > 0 ? (totalPaidAmount / totalProjectCost) * 100 : 0;

        ClientPaymentDashboardDTO response = ClientPaymentDashboardDTO.builder()
                .totalProjects(totalProjects)
                .totalProjectCost(formatAmount(totalProjectCost))
                .totalPaidAmount(formatAmount(totalPaidAmount))
                .totalRemainingAmount(formatAmount(totalRemaining))
                .overallPaidPercentage(overallPaidPercentage)
                .completedProjects(completedProjects)
                .pendingProjects(pendingProjects)
                .projects(projectSummaries)
                .build();

        printDebugResponse("getMyPaymentDashboard", response);
        return response;
    }

    // ==================== CREATE PAYMENT ====================

    @Transactional
    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
        if (userId == null) throw new RuntimeException("User ID is required");
        if (request == null) throw new RuntimeException("Payment request is required");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            throw new RuntimeException("Transaction ID is required");
        }
        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
            throw new RuntimeException("Amount is required");
        }
        if (request.getProjectId() == null) {
            throw new RuntimeException("Project ID is required");
        }

        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
            throw new RuntimeException("Transaction ID already exists: " + request.getTransactionId());
        }

        ProjectDetails project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));

        PaymentDetails payment = new PaymentDetails();
        payment.setTransactionId(request.getTransactionId());
        payment.setAmount(request.getAmount());
        payment.setType("PROJECT_PAYMENT");
        payment.setStatus("PENDING");
        payment.setUser(user);
        payment.setPaymentNote(request.getPaymentNote());
        payment.setDateTime(LocalDateTime.now());

        PaymentDetails savedPayment = paymentRepository.save(payment);

        ProjectPayment projectPayment = new ProjectPayment();
        projectPayment.setProject(project);
        projectPayment.setPayment(savedPayment);
        projectPaymentRepository.save(projectPayment);

        sendPaymentNotificationEmail(savedPayment, user);

        return convertToDTO(savedPayment);
    }

    @Transactional
    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
        validateHeadDirector(userId);

        if (request == null) throw new RuntimeException("Payment request is required");
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            throw new RuntimeException("Transaction ID is required");
        }
        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
            throw new RuntimeException("Amount is required");
        }
        if (request.getUserId() == null) {
            throw new RuntimeException("Employee User ID is required");
        }

        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
            throw new RuntimeException("Transaction ID already exists");
        }

        User employee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + request.getUserId()));

        PaymentDetails payment = new PaymentDetails();
        payment.setTransactionId(request.getTransactionId());
        payment.setAmount(request.getAmount());
        payment.setType("SALARY");
        payment.setStatus("PENDING");
        payment.setUser(employee);
        payment.setPaymentNote(request.getPaymentNote());
        payment.setDateTime(LocalDateTime.now());

        PaymentDetails savedPayment = paymentRepository.save(payment);

        SalaryPayment salaryPayment = new SalaryPayment();
        salaryPayment.setUser(employee);
        salaryPayment.setPayment(savedPayment);
        salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        salaryPayment.setSalaryAmount(request.getAmount());
        salaryPaymentRepository.save(salaryPayment);

        User director = userRepository.findById(userId).orElseThrow();
        sendSalaryPaymentNotificationEmail(savedPayment, employee, director);

        return convertToDTO(savedPayment);
    }

    // ==================== HEAD DIRECTOR APIs ====================

    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
        validateHeadDirector(headDirectorId);
        List<PaymentResponseDTO> response = paymentRepository.findByStatus("PENDING").stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        printDebugResponse("getPendingPayments", response);
        return response;
    }

    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
        validateHeadDirector(headDirectorId);
        List<PaymentResponseDTO> response = paymentRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        printDebugResponse("getAllPayments", response);
        return response;
    }

    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
        validateHeadDirector(headDirectorId);
        PaymentDetails payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        return convertToDTO(payment);
    }

    @Transactional
    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
        validateHeadDirector(headDirectorId);

        if (request == null) throw new RuntimeException("Approval request is required");
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new RuntimeException("Status is required (APPROVED/REJECTED)");
        }

        User director = userRepository.findById(headDirectorId)
                .orElseThrow(() -> new RuntimeException("Director not found"));
        PaymentDetails payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));

        if (!"PENDING".equals(payment.getStatus())) {
            throw new RuntimeException("Payment is already " + payment.getStatus());
        }

        payment.setStatus(request.getStatus());
        payment.setApprovedBy(director);
        payment.setApprovedAt(LocalDateTime.now());
        payment.setDirectorNote(request.getDirectorNote());

        PaymentDetails updated = paymentRepository.save(payment);
        sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());

        return convertToDTO(updated);
    }

    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
        validateHeadDirector(headDirectorId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));

        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);

        double totalProjectAmount = safeParseDouble(project.getCost());
        double paidAmount = totalApproved != null ? totalApproved : 0.0;
        double remainingAmount = totalProjectAmount - paidAmount;
        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;

        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .totalProjectAmount(formatAmount(totalProjectAmount))
                .paidAmount(formatAmount(paidAmount))
                .remainingAmount(formatAmount(remainingAmount))
                .paidPercentage(paidPercentage)
                .isFullyPaid(remainingAmount <= 0)
                .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
                .build();

        printDebugResponse("getProjectPayments", response);
        return response;
    }

    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
        validateHeadDirector(headDirectorId);

        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
        List<PaymentResponseDTO> response = salaryPayments.stream()
                .map(sp -> convertToDTO(sp.getPayment()))
                .collect(Collectors.toList());

        printDebugResponse("getSalaryPaymentsByUser", response);
        return response;
    }

    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
        validateHeadDirector(headDirectorId);

        List<PaymentDetails> allPayments = paymentRepository.findAll();
        List<PaymentDetails> approvedPayments = allPayments.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()))
                .collect(Collectors.toList());

        double totalRevenue = 0.0;
        double totalExpenses = 0.0;

        for (PaymentDetails p : approvedPayments) {
            double amount = safeParseDouble(p.getAmount());
            if ("PROJECT_PAYMENT".equals(p.getType())) {
                totalRevenue += amount;
            } else if ("SALARY".equals(p.getType())) {
                totalExpenses += amount;
            }
        }

        long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

        PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netProfit(totalRevenue - totalExpenses)
                .pendingApprovals((int) pendingApprovals)
                .totalTransactions(allPayments.size())
                .approvedTransactions(approvedPayments.size())
                .build();

        List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);

        List<PaymentResponseDTO> recentTransactions = allPayments.stream()
                .sorted((a, b) -> {
                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(10)
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();

        Map<String, Double> revenueByType = new HashMap<>();
        revenueByType.put("PROJECT_PAYMENT", totalRevenue);
        revenueByType.put("SALARY", totalExpenses);

        DashboardPaymentStatsDTO response = DashboardPaymentStatsDTO.builder()
                .summary(summary)
                .monthlyRevenue(monthlyRevenue)
                .recentTransactions(recentTransactions)
                .topProjects(topProjects)
                .revenueByType(revenueByType)
                .build();

        printDebugResponse("getDashboardStats", response);
        return response;
    }

    // ==================== HEAD DIRECTOR - PROJECT WISE PAYMENT SUMMARY APIs ====================

    public List<ProjectPaymentSummaryDTO> getAllProjectsWithPaymentSummary(Long headDirectorId) {
        validateHeadDirector(headDirectorId);

        List<ProjectDetails> allProjects = projectRepository.findAll();

        if (allProjects == null || allProjects.isEmpty()) {
            System.out.println("No projects found in database");
            return new ArrayList<>();
        }

        System.out.println("Total projects found: " + allProjects.size());

        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();

        for (ProjectDetails project : allProjects) {
            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double totalProjectAmount = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = totalProjectAmount - paidAmount;
            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;

            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
            List<PaymentResponseDTO> paymentDTOs = new ArrayList<>();
            if (projectPayments != null) {
                paymentDTOs = projectPayments.stream()
                        .map(this::convertToDTO)
                        .collect(Collectors.toList());
            }

            System.out.println("Project: " + project.getProjectName() +
                    " | Total: " + totalProjectAmount +
                    " | Paid: " + paidAmount +
                    " | Remaining: " + remainingAmount +
                    " | Payments: " + paymentDTOs.size());

            ProjectPaymentSummaryDTO.ProjectPaymentSummaryDTOBuilder builder = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
                    .totalProjectAmount(formatAmount(totalProjectAmount))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)
                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
                    .payments(paymentDTOs);

            if (project.getProjectDescription() != null) {
                builder.projectDescription(project.getProjectDescription());
            }
            if (project.getProjectType() != null) {
                builder.projectType(project.getProjectType());
            }
            if (project.getStatus() != null) {
                builder.status(project.getStatus());
            }
            if (project.getDepartment() != null) {
                builder.departmentId(project.getDepartment().getDepartmentId());
                builder.departmentName(project.getDepartment().getDepartmentName() != null ?
                        project.getDepartment().getDepartmentName() : "N/A");
            } else {
                builder.departmentId(null);
                builder.departmentName("N/A");
            }
            if (project.getUploader() != null) {
                builder.clientId(project.getUploader().getUserId());
                builder.clientName(project.getUploader().getName() != null ? project.getUploader().getName() : "N/A");
                builder.clientEmail(project.getUploader().getEmail() != null ? project.getUploader().getEmail() : "N/A");
            } else {
                builder.clientId(null);
                builder.clientName("N/A");
                builder.clientEmail("N/A");
            }
            if (project.getAssignedManager() != null) {
                builder.assignedManagerName(project.getAssignedManager().getName());
            }
            if (project.getDeadline() != null) {
                builder.deadline(project.getDeadline().toString());
            }

            result.add(builder.build());
        }

        result.sort((a, b) -> {
            try {
                double aRemaining = Double.parseDouble(a.getRemainingAmount());
                double bRemaining = Double.parseDouble(b.getRemainingAmount());
                return Double.compare(bRemaining, aRemaining);
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        printDebugResponse("getAllProjectsWithPaymentSummary", result);
        return result;
    }

    public List<ProjectPaymentSummaryDTO> getProjectsByDepartmentWithPaymentSummary(Long headDirectorId, Long departmentId) {
        validateHeadDirector(headDirectorId);

        if (departmentId == null) {
            throw new RuntimeException("Department ID is required");
        }

        List<ProjectDetails> departmentProjects = projectRepository.findByDepartmentDepartmentId(departmentId);

        if (departmentProjects == null || departmentProjects.isEmpty()) {
            System.out.println("No projects found for department: " + departmentId);
            return new ArrayList<>();
        }

        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();

        for (ProjectDetails project : departmentProjects) {
            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double totalProjectAmount = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = totalProjectAmount - paidAmount;
            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;

            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
            List<PaymentResponseDTO> paymentDTOs = projectPayments != null ?
                    projectPayments.stream().map(this::convertToDTO).collect(Collectors.toList()) :
                    new ArrayList<>();

            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
                    .totalProjectAmount(formatAmount(totalProjectAmount))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)
                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
                    .payments(paymentDTOs)
                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
                    .build();

            result.add(summary);
        }

        result.sort((a, b) -> {
            try {
                double aRemaining = Double.parseDouble(a.getRemainingAmount());
                double bRemaining = Double.parseDouble(b.getRemainingAmount());
                return Double.compare(bRemaining, aRemaining);
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        printDebugResponse("getProjectsByDepartmentWithPaymentSummary", result);
        return result;
    }

    public PaymentApprovalCountDTO getPaymentApprovalCount(Long headDirectorId) {
        validateHeadDirector(headDirectorId);

        long pendingCount = paymentRepository.countByStatus("PENDING");
        long approvedCount = paymentRepository.countByStatus("APPROVED");
        long rejectedCount = paymentRepository.countByStatus("REJECTED");

        PaymentApprovalCountDTO result = PaymentApprovalCountDTO.builder()
                .pendingCount(pendingCount)
                .approvedCount(approvedCount)
                .rejectedCount(rejectedCount)
                .build();

        printDebugResponse("getPaymentApprovalCount", result);
        return result;
    }

    public List<DepartmentPaymentSummaryDTO> getDepartmentWisePaymentSummary(Long headDirectorId) {
        validateHeadDirector(headDirectorId);

        List<ProjectDetails> allProjects = projectRepository.findAll();
        Map<Long, DepartmentPaymentSummaryDTO> departmentMap = new HashMap<>();

        for (ProjectDetails project : allProjects) {
            Long deptId = project.getDepartment() != null ? project.getDepartment().getDepartmentId() : -1L;
            String deptName = project.getDepartment() != null && project.getDepartment().getDepartmentName() != null ?
                    project.getDepartment().getDepartmentName() : "Unassigned";

            DepartmentPaymentSummaryDTO deptSummary = departmentMap.get(deptId);
            if (deptSummary == null) {
                deptSummary = DepartmentPaymentSummaryDTO.builder()
                        .departmentId(deptId)
                        .departmentName(deptName)
                        .totalProjects(0)
                        .totalBudget(0.0)
                        .totalPaid(0.0)
                        .totalRemaining(0.0)
                        .projects(new ArrayList<>())
                        .build();
                departmentMap.put(deptId, deptSummary);
            }

            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double totalProjectAmount = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = totalProjectAmount - paidAmount;

            deptSummary.setTotalProjects(deptSummary.getTotalProjects() + 1);
            deptSummary.setTotalBudget(deptSummary.getTotalBudget() + totalProjectAmount);
            deptSummary.setTotalPaid(deptSummary.getTotalPaid() + paidAmount);
            deptSummary.setTotalRemaining(deptSummary.getTotalRemaining() + remainingAmount);

            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
                    .totalProjectAmount(formatAmount(totalProjectAmount))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
                    .build();

            deptSummary.getProjects().add(projectSummary);
        }

        List<DepartmentPaymentSummaryDTO> result = new ArrayList<>(departmentMap.values());
        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));

        printDebugResponse("getDepartmentWisePaymentSummary", result);
        return result;
    }

    public List<ClientPaymentSummaryDTO> getClientWisePaymentSummary(Long headDirectorId) {
        validateHeadDirector(headDirectorId);

        List<ProjectDetails> allProjects = projectRepository.findAll();
        Map<Long, ClientPaymentSummaryDTO> clientMap = new HashMap<>();

        for (ProjectDetails project : allProjects) {
            Long clientId = project.getUploader() != null ? project.getUploader().getUserId() : -1L;
            String clientName = project.getUploader() != null && project.getUploader().getName() != null ?
                    project.getUploader().getName() : "Unknown Client";
            String clientEmail = project.getUploader() != null && project.getUploader().getEmail() != null ?
                    project.getUploader().getEmail() : "unknown@example.com";

            ClientPaymentSummaryDTO clientSummary = clientMap.get(clientId);
            if (clientSummary == null) {
                clientSummary = ClientPaymentSummaryDTO.builder()
                        .clientId(clientId)
                        .clientName(clientName)
                        .clientEmail(clientEmail)
                        .totalProjects(0)
                        .totalBudget(0.0)
                        .totalPaid(0.0)
                        .totalRemaining(0.0)
                        .projects(new ArrayList<>())
                        .build();
                clientMap.put(clientId, clientSummary);
            }

            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            double totalProjectAmount = safeParseDouble(project.getCost());
            double paidAmount = totalApproved != null ? totalApproved : 0.0;
            double remainingAmount = totalProjectAmount - paidAmount;

            clientSummary.setTotalProjects(clientSummary.getTotalProjects() + 1);
            clientSummary.setTotalBudget(clientSummary.getTotalBudget() + totalProjectAmount);
            clientSummary.setTotalPaid(clientSummary.getTotalPaid() + paidAmount);
            clientSummary.setTotalRemaining(clientSummary.getTotalRemaining() + remainingAmount);

            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
                    .totalProjectAmount(formatAmount(totalProjectAmount))
                    .paidAmount(formatAmount(paidAmount))
                    .remainingAmount(formatAmount(remainingAmount))
                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
                    .build();

            clientSummary.getProjects().add(projectSummary);
        }

        List<ClientPaymentSummaryDTO> result = new ArrayList<>(clientMap.values());
        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));

        printDebugResponse("getClientWisePaymentSummary", result);
        return result;
    }

    // ==================== HELPER METHODS ====================

    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 5; i >= 0; i--) {
            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
            dto.setMonth(month);
            dto.setTotalRevenue(0.0);
            dto.setTotalExpenses(0.0);
            dto.setNetProfit(0.0);
            dto.setProjectPaymentsCount(0);
            dto.setSalaryPaymentsCount(0);
            monthlyMap.put(month, dto);
        }

        for (PaymentDetails p : payments) {
            if (p.getCreatedAt() == null) continue;
            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
            MonthlyRevenueDTO dto = monthlyMap.get(month);
            if (dto != null) {
                double amount = safeParseDouble(p.getAmount());
                if ("PROJECT_PAYMENT".equals(p.getType())) {
                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
                } else if ("SALARY".equals(p.getType())) {
                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
                }
                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
            }
        }

        return new ArrayList<>(monthlyMap.values());
    }

    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
        List<ProjectDetails> projects = projectRepository.findAll();
        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();

        for (ProjectDetails project : projects) {
            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
            if (totalPaid != null && totalPaid > 0) {
                double totalAmount = safeParseDouble(project.getCost());
                ProjectPaymentSummaryDTO dto = ProjectPaymentSummaryDTO.builder()
                        .projectId(project.getProjectId())
                        .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
                        .totalProjectAmount(formatAmount(totalAmount))
                        .paidAmount(formatAmount(totalPaid))
                        .remainingAmount(formatAmount(totalAmount - totalPaid))
                        .paidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0)
                        .isFullyPaid(totalPaid >= totalAmount)
                        .build();
                result.add(dto);
            }
        }

        result.sort((a, b) -> {
            try {
                double aPaid = Double.parseDouble(a.getPaidAmount());
                double bPaid = Double.parseDouble(b.getPaidAmount());
                return Double.compare(bPaid, aPaid);
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        return result.stream().limit(5).collect(Collectors.toList());
    }

    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
        if (payment == null) return null;

        PaymentResponseDTO dto = new PaymentResponseDTO();
        dto.setPaymentId(payment.getPaymentId());
        dto.setTransactionId(payment.getTransactionId());
        dto.setAmount(formatAmount(safeParseDouble(payment.getAmount())));
        dto.setType(payment.getType());
        dto.setStatus(payment.getStatus());

        if (payment.getUser() != null) {
            dto.setUserName(payment.getUser().getName() != null ? payment.getUser().getName() : "N/A");
            dto.setUserEmail(payment.getUser().getEmail() != null ? payment.getUser().getEmail() : "N/A");
        } else {
            dto.setUserName("N/A");
            dto.setUserEmail("N/A");
        }

        dto.setPaymentNote(payment.getPaymentNote());
        dto.setDirectorNote(payment.getDirectorNote());

        if (payment.getApprovedBy() != null) {
            dto.setApprovedByName(payment.getApprovedBy().getName());
        }

        dto.setApprovedAt(payment.getApprovedAt());
        dto.setDateTime(payment.getDateTime());

        return dto;
    }

    // ==================== EMAIL METHODS ====================

    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
        try {
            List<UserType> userTypes = userTypeRepository.findAll();
            User headDirector = userTypes.stream()
                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
                    .map(UserType::getUser)
                    .findFirst()
                    .orElse(null);

            if (headDirector != null && headDirector.getEmail() != null) {
                String subject = "💰 New Payment Request - " + payment.getType() + " - Softmax Technologies and IT Services";
                String safeNote = EmailService.makeLinksClickable(payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note");

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
                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                            .content { padding: 40px 35px; }
                            .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
                            .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                            .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>💰 New Payment Request</h1>
                                <p>Softmax Technologies and IT Services</p>
                            </div>
                            <div class="content">
                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                                <p style="color:#4a5568;font-size:15px;">A new payment request has been submitted for your approval.</p>
                                <div class="info-box">
                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
                                    <p>📝 <strong>Type:</strong> %s</p>
                                    <p>👤 <strong>Submitted By:</strong> %s</p>
                                    <p>📅 <strong>Date:</strong> %s</p>
                                    <p>📝 <strong>Note:</strong></p>
                                    <p>%s</p>
                                </div>
                                <p style="color:#4a5568;">Please login to approve or reject this payment.</p>
                                <div style="text-align:center;">
                                    <a href="https://nexus.softmax.in" class="btn">🚀 Review Payment</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                        headDirector.getName() != null ? headDirector.getName() : "Director",
                        payment.getTransactionId(),
                        payment.getAmount(),
                        payment.getType(),
                        user.getName() != null ? user.getName() : "User",
                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A",
                        safeNote
                );
                emailService.sendEmail(headDirector.getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error (non-critical): " + e.getMessage());
        }
    }

    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
        try {
            if (director != null && director.getEmail() != null) {
                String subject = "💰 Salary Payment Request - " + (employee.getName() != null ? employee.getName() : "Employee") + " - Softmax Technologies and IT Services";

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
                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                            .content { padding: 40px 35px; }
                            .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #667eea; }
                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                            .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                            .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>💰 Salary Payment Request</h1>
                                <p>Softmax Technologies and IT Services</p>
                            </div>
                            <div class="content">
                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                                <p style="color:#4a5568;font-size:15px;">A salary payment request has been submitted.</p>
                                <div class="info-box">
                                    <p>👤 <strong>Employee:</strong> %s</p>
                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
                                    <p>📅 <strong>Date:</strong> %s</p>
                                </div>
                                <p style="color:#4a5568;">Please login to approve or reject this payment.</p>
                                <div style="text-align:center;">
                                    <a href="https://nexus.softmax.in" class="btn">🚀 Review Payment</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                        director.getName() != null ? director.getName() : "Director",
                        employee.getName() != null ? employee.getName() : "Employee",
                        payment.getAmount(),
                        payment.getTransactionId(),
                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A"
                );
                emailService.sendEmail(director.getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error (non-critical): " + e.getMessage());
        }
    }

    // ⚠️ THIS IS THE ONLY EMAIL THAT GOES TO CLIENT — Client URL fixed here
    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
        try {
            if (payment.getUser() != null && payment.getUser().getEmail() != null) {
                String subject = (status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected") + " - Softmax Technologies and IT Services";
                String color = status.equals("APPROVED") ? "#11998e" : "#ee5a24";
                String icon = status.equals("APPROVED") ? "✅" : "❌";
                String heading = status.equals("APPROVED") ? "Payment Approved" : "Payment Rejected";

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
                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                            .content { padding: 40px 35px; }
                            .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
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
                                <h1>%s %s</h1>
                                <p>Softmax Technologies and IT Services</p>
                            </div>
                            <div class="content">
                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                                <p style="color:#4a5568;font-size:15px;">Your payment request has been <strong style="color:%s;">%s</strong>.</p>
                                <div class="info-box">
                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
                                    <div class="note-box">
                                        <p><strong>📝 Director Note:</strong></p>
                                        <p>%s</p>
                                    </div>
                                </div>
                                <div style="text-align:center;">
                                    <a href="https://clients.softmax.in/dashboard" class="btn">🚀 View Dashboard</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://clients.softmax.in">clients.softmax.in</a></p>
                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                        color, color,
                        icon, heading,
                        payment.getUser().getName() != null ? payment.getUser().getName() : "User",
                        color, status,
                        payment.getTransactionId(),
                        payment.getAmount(),
                        safeNote
                );
                emailService.sendEmail(payment.getUser().getEmail(), subject, html);
            }
        } catch (Exception e) {
            System.out.println("Email error (non-critical): " + e.getMessage());
        }
    }
}














//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
//import com.softmax.shardaos.dto.input.CreatePaymentRequest;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import com.fasterxml.jackson.databind.ObjectMapper;
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
//public class PaymentService {
//
//    @Autowired
//    private PaymentDetailsRepository paymentRepository;
//
//    @Autowired
//    private ProjectPaymentRepository projectPaymentRepository;
//
//    @Autowired
//    private SalaryPaymentRepository salaryPaymentRepository;
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
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    // Helper method to print debug response
//    private void printDebugResponse(String methodName, Object response) {
//        try {
//            System.out.println("\n========== DEBUG RESPONSE ==========");
//            System.out.println("Method: " + methodName);
//            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
//            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//            System.out.println("Response JSON:");
//            System.out.println(jsonResponse);
//            System.out.println("=====================================\n");
//        } catch (Exception e) {
//            System.out.println("Error printing debug response: " + e.getMessage());
//        }
//    }
//
//    // IMPROVED: Safe parse double with better handling of Indian currency format
//    private double safeParseDouble(String value) {
//        if (value == null || value.trim().isEmpty()) {
//            return 0.0;
//        }
//        try {
//            String cleaned = value.toString().replaceAll("[^\\d.]", "");
//            if (cleaned.isEmpty() || cleaned.equals(".")) {
//                return 0.0;
//            }
//            int firstDot = cleaned.indexOf('.');
//            if (firstDot != -1) {
//                cleaned = cleaned.substring(0, firstDot + 1) +
//                        cleaned.substring(firstDot + 1).replaceAll("\\.", "");
//            }
//            return Double.parseDouble(cleaned);
//        } catch (NumberFormatException e) {
//            System.out.println("Error parsing double: " + value);
//            return 0.0;
//        }
//    }
//
//    private String formatAmount(double amount) {
//        return String.format("%.2f", amount);
//    }
//
//    // ==================== VALIDATION ====================
//
//    public boolean isHeadDirector(Long userId) {
//        if (userId == null) return false;
//        try {
//            List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//            boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//            boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//            if (!isEmp || !isDirector) return false;
//            EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//            return emp != null && emp.getDepartmentRef() != null &&
//                    HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
//        } catch (Exception e) {
//            System.out.println("Error checking Head Director: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private void validateHeadDirector(Long userId) {
//        if (userId == null) {
//            throw new RuntimeException("Authentication required: User ID is null");
//        }
//        if (!isHeadDirector(userId)) {
//            throw new RuntimeException("Access denied: Only Head Director can manage payments");
//        }
//    }
//
//    private void validateProjectOwner(Long userId, Long projectId) {
//        if (userId == null) {
//            throw new RuntimeException("Authentication required: User ID is null");
//        }
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
//        if (project.getUploader() == null || !project.getUploader().getUserId().equals(userId)) {
//            throw new RuntimeException("Access denied: You can only access your own projects");
//        }
//    }
//
//    // ==================== CLIENT APIs ====================
//
//    public List<PaymentResponseDTO> getMyProjectPayments(Long userId, Long projectId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        validateProjectOwner(userId, projectId);
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        List<PaymentResponseDTO> response = payments.stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getMyProjectPayments", response);
//        return response;
//    }
//
//    public ProjectPaymentSummaryDTO getMyProjectPaymentSummary(Long userId, Long projectId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        validateProjectOwner(userId, projectId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//
//        double totalProjectAmount = safeParseDouble(project.getCost());
//        double paidAmount = totalApproved != null ? totalApproved : 0.0;
//        double remainingAmount = totalProjectAmount - paidAmount;
//        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//        List<PaymentResponseDTO> approvedPayments = payments.stream()
//                .filter(p -> "APPROVED".equals(p.getStatus()))
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//
//        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//                .projectId(project.getProjectId())
//                .projectName(project.getProjectName())
//                .totalProjectAmount(formatAmount(totalProjectAmount))
//                .paidAmount(formatAmount(paidAmount))
//                .remainingAmount(formatAmount(remainingAmount))
//                .paidPercentage(paidPercentage)
//                .isFullyPaid(remainingAmount <= 0)
//                .payments(approvedPayments)
//                .build();
//
//        printDebugResponse("getMyProjectPaymentSummary", response);
//        return response;
//    }
//
//    public List<ProjectPaymentSummaryDTO> getAllMyProjectsPayments(Long userId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//        List<ProjectPaymentSummaryDTO> response = new ArrayList<>();
//
//        for (ProjectDetails project : myProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(paidPercentage)
//                    .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
//                    .payments(new ArrayList<>())
//                    .build();
//
//            response.add(summary);
//        }
//
//        response.sort((a, b) -> a.getProjectName().compareTo(b.getProjectName()));
//        printDebugResponse("getAllMyProjectsPayments", response);
//        return response;
//    }
//
//    public PaymentResponseDTO getMyPaymentByTransactionId(Long userId, String transactionId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        if (transactionId == null) throw new RuntimeException("Transaction ID is required");
//
//        PaymentDetails payment = paymentRepository.findByTransactionId(transactionId)
//                .orElseThrow(() -> new RuntimeException("Payment not found for transaction: " + transactionId));
//
//        if ("PROJECT_PAYMENT".equals(payment.getType())) {
//            Optional<ProjectPayment> projectPaymentOpt = projectPaymentRepository.findByPayment(payment);
//            if (projectPaymentOpt.isPresent()) {
//                ProjectPayment projectPayment = projectPaymentOpt.get();
//                validateProjectOwner(userId, projectPayment.getProject().getProjectId());
//                return convertToDTO(payment);
//            } else {
//                throw new RuntimeException("Project payment mapping not found");
//            }
//        } else {
//            throw new RuntimeException("Invalid payment type for client access");
//        }
//    }
//
//    public ClientPaymentDashboardDTO getMyPaymentDashboard(Long userId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//
//        double totalProjectCost = 0.0;
//        double totalPaidAmount = 0.0;
//        int totalProjects = myProjects.size();
//        int completedProjects = 0;
//        int pendingProjects = 0;
//
//        List<ProjectPaymentSummaryDTO> projectSummaries = new ArrayList<>();
//
//        for (ProjectDetails project : myProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double projectCost = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = projectCost - paidAmount;
//
//            totalProjectCost += projectCost;
//            totalPaidAmount += paidAmount;
//
//            if (remainingAmount <= 0 && projectCost > 0) {
//                completedProjects++;
//            } else if (projectCost > 0) {
//                pendingProjects++;
//            }
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .totalProjectAmount(formatAmount(projectCost))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(projectCost > 0 ? (paidAmount / projectCost) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0 && projectCost > 0)
//                    .build();
//
//            projectSummaries.add(summary);
//        }
//
//        double totalRemaining = totalProjectCost - totalPaidAmount;
//        double overallPaidPercentage = totalProjectCost > 0 ? (totalPaidAmount / totalProjectCost) * 100 : 0;
//
//        ClientPaymentDashboardDTO response = ClientPaymentDashboardDTO.builder()
//                .totalProjects(totalProjects)
//                .totalProjectCost(formatAmount(totalProjectCost))
//                .totalPaidAmount(formatAmount(totalPaidAmount))
//                .totalRemainingAmount(formatAmount(totalRemaining))
//                .overallPaidPercentage(overallPaidPercentage)
//                .completedProjects(completedProjects)
//                .pendingProjects(pendingProjects)
//                .projects(projectSummaries)
//                .build();
//
//        printDebugResponse("getMyPaymentDashboard", response);
//        return response;
//    }
//
//    // ==================== CREATE PAYMENT ====================
//
//    @Transactional
//    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        if (request == null) throw new RuntimeException("Payment request is required");
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
//
//        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
//            throw new RuntimeException("Transaction ID is required");
//        }
//        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
//            throw new RuntimeException("Amount is required");
//        }
//        if (request.getProjectId() == null) {
//            throw new RuntimeException("Project ID is required");
//        }
//
//        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//            throw new RuntimeException("Transaction ID already exists: " + request.getTransactionId());
//        }
//
//        ProjectDetails project = projectRepository.findById(request.getProjectId())
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
//
//        PaymentDetails payment = new PaymentDetails();
//        payment.setTransactionId(request.getTransactionId());
//        payment.setAmount(request.getAmount());
//        payment.setType("PROJECT_PAYMENT");
//        payment.setStatus("PENDING");
//        payment.setUser(user);
//        payment.setPaymentNote(request.getPaymentNote());
//        payment.setDateTime(LocalDateTime.now());
//
//        PaymentDetails savedPayment = paymentRepository.save(payment);
//
//        ProjectPayment projectPayment = new ProjectPayment();
//        projectPayment.setProject(project);
//        projectPayment.setPayment(savedPayment);
//        projectPaymentRepository.save(projectPayment);
//
//        sendPaymentNotificationEmail(savedPayment, user);
//
//        return convertToDTO(savedPayment);
//    }
//
//    @Transactional
//    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
//        validateHeadDirector(userId);
//
//        if (request == null) throw new RuntimeException("Payment request is required");
//        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
//            throw new RuntimeException("Transaction ID is required");
//        }
//        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
//            throw new RuntimeException("Amount is required");
//        }
//        if (request.getUserId() == null) {
//            throw new RuntimeException("Employee User ID is required");
//        }
//
//        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//            throw new RuntimeException("Transaction ID already exists");
//        }
//
//        User employee = userRepository.findById(request.getUserId())
//                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + request.getUserId()));
//
//        PaymentDetails payment = new PaymentDetails();
//        payment.setTransactionId(request.getTransactionId());
//        payment.setAmount(request.getAmount());
//        payment.setType("SALARY");
//        payment.setStatus("PENDING");
//        payment.setUser(employee);
//        payment.setPaymentNote(request.getPaymentNote());
//        payment.setDateTime(LocalDateTime.now());
//
//        PaymentDetails savedPayment = paymentRepository.save(payment);
//
//        SalaryPayment salaryPayment = new SalaryPayment();
//        salaryPayment.setUser(employee);
//        salaryPayment.setPayment(savedPayment);
//        salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
//        salaryPayment.setSalaryAmount(request.getAmount());
//        salaryPaymentRepository.save(salaryPayment);
//
//        User director = userRepository.findById(userId).orElseThrow();
//        sendSalaryPaymentNotificationEmail(savedPayment, employee, director);
//
//        return convertToDTO(savedPayment);
//    }
//
//    // ==================== HEAD DIRECTOR APIs ====================
//
//    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//        List<PaymentResponseDTO> response = paymentRepository.findByStatus("PENDING").stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getPendingPayments", response);
//        return response;
//    }
//
//    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//        List<PaymentResponseDTO> response = paymentRepository.findAll().stream()
//                .sorted((a, b) -> {
//                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
//                    return b.getCreatedAt().compareTo(a.getCreatedAt());
//                })
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getAllPayments", response);
//        return response;
//    }
//
//    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
//        validateHeadDirector(headDirectorId);
//        PaymentDetails payment = paymentRepository.findById(paymentId)
//                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
//        return convertToDTO(payment);
//    }
//
//    @Transactional
//    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
//        validateHeadDirector(headDirectorId);
//
//        if (request == null) throw new RuntimeException("Approval request is required");
//        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
//            throw new RuntimeException("Status is required (APPROVED/REJECTED)");
//        }
//
//        User director = userRepository.findById(headDirectorId)
//                .orElseThrow(() -> new RuntimeException("Director not found"));
//        PaymentDetails payment = paymentRepository.findById(paymentId)
//                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
//
//        if (!"PENDING".equals(payment.getStatus())) {
//            throw new RuntimeException("Payment is already " + payment.getStatus());
//        }
//
//        payment.setStatus(request.getStatus());
//        payment.setApprovedBy(director);
//        payment.setApprovedAt(LocalDateTime.now());
//        payment.setDirectorNote(request.getDirectorNote());
//
//        PaymentDetails updated = paymentRepository.save(payment);
//        sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());
//
//        return convertToDTO(updated);
//    }
//
//    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
//        validateHeadDirector(headDirectorId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
//
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//
//        double totalProjectAmount = safeParseDouble(project.getCost());
//        double paidAmount = totalApproved != null ? totalApproved : 0.0;
//        double remainingAmount = totalProjectAmount - paidAmount;
//        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//                .projectId(project.getProjectId())
//                .projectName(project.getProjectName())
//                .totalProjectAmount(formatAmount(totalProjectAmount))
//                .paidAmount(formatAmount(paidAmount))
//                .remainingAmount(formatAmount(remainingAmount))
//                .paidPercentage(paidPercentage)
//                .isFullyPaid(remainingAmount <= 0)
//                .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
//                .build();
//
//        printDebugResponse("getProjectPayments", response);
//        return response;
//    }
//
//    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
//        validateHeadDirector(headDirectorId);
//
//        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
//        List<PaymentResponseDTO> response = salaryPayments.stream()
//                .map(sp -> convertToDTO(sp.getPayment()))
//                .collect(Collectors.toList());
//
//        printDebugResponse("getSalaryPaymentsByUser", response);
//        return response;
//    }
//
//    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<PaymentDetails> allPayments = paymentRepository.findAll();
//        List<PaymentDetails> approvedPayments = allPayments.stream()
//                .filter(p -> "APPROVED".equals(p.getStatus()))
//                .collect(Collectors.toList());
//
//        double totalRevenue = 0.0;
//        double totalExpenses = 0.0;
//
//        for (PaymentDetails p : approvedPayments) {
//            double amount = safeParseDouble(p.getAmount());
//            if ("PROJECT_PAYMENT".equals(p.getType())) {
//                totalRevenue += amount;
//            } else if ("SALARY".equals(p.getType())) {
//                totalExpenses += amount;
//            }
//        }
//
//        long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
//
//        PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
//                .totalRevenue(totalRevenue)
//                .totalExpenses(totalExpenses)
//                .netProfit(totalRevenue - totalExpenses)
//                .pendingApprovals((int) pendingApprovals)
//                .totalTransactions(allPayments.size())
//                .approvedTransactions(approvedPayments.size())
//                .build();
//
//        List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);
//
//        List<PaymentResponseDTO> recentTransactions = allPayments.stream()
//                .sorted((a, b) -> {
//                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
//                    return b.getCreatedAt().compareTo(a.getCreatedAt());
//                })
//                .limit(10)
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//
//        List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();
//
//        Map<String, Double> revenueByType = new HashMap<>();
//        revenueByType.put("PROJECT_PAYMENT", totalRevenue);
//        revenueByType.put("SALARY", totalExpenses);
//
//        DashboardPaymentStatsDTO response = DashboardPaymentStatsDTO.builder()
//                .summary(summary)
//                .monthlyRevenue(monthlyRevenue)
//                .recentTransactions(recentTransactions)
//                .topProjects(topProjects)
//                .revenueByType(revenueByType)
//                .build();
//
//        printDebugResponse("getDashboardStats", response);
//        return response;
//    }
//
//    // ==================== HEAD DIRECTOR - PROJECT WISE PAYMENT SUMMARY APIs ====================
//
//    public List<ProjectPaymentSummaryDTO> getAllProjectsWithPaymentSummary(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//
//        if (allProjects == null || allProjects.isEmpty()) {
//            System.out.println("No projects found in database");
//            return new ArrayList<>();
//        }
//
//        System.out.println("Total projects found: " + allProjects.size());
//
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : allProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
//            List<PaymentResponseDTO> paymentDTOs = new ArrayList<>();
//            if (projectPayments != null) {
//                paymentDTOs = projectPayments.stream()
//                        .map(this::convertToDTO)
//                        .collect(Collectors.toList());
//            }
//
//            System.out.println("Project: " + project.getProjectName() +
//                    " | Total: " + totalProjectAmount +
//                    " | Paid: " + paidAmount +
//                    " | Remaining: " + remainingAmount +
//                    " | Payments: " + paymentDTOs.size());
//
//            ProjectPaymentSummaryDTO.ProjectPaymentSummaryDTOBuilder builder = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .payments(paymentDTOs);
//
//            if (project.getProjectDescription() != null) {
//                builder.projectDescription(project.getProjectDescription());
//            }
//            if (project.getProjectType() != null) {
//                builder.projectType(project.getProjectType());
//            }
//            if (project.getStatus() != null) {
//                builder.status(project.getStatus());
//            }
//            if (project.getDepartment() != null) {
//                builder.departmentId(project.getDepartment().getDepartmentId());
//                builder.departmentName(project.getDepartment().getDepartmentName() != null ?
//                        project.getDepartment().getDepartmentName() : "N/A");
//            } else {
//                builder.departmentId(null);
//                builder.departmentName("N/A");
//            }
//            if (project.getUploader() != null) {
//                builder.clientId(project.getUploader().getUserId());
//                builder.clientName(project.getUploader().getName() != null ? project.getUploader().getName() : "N/A");
//                builder.clientEmail(project.getUploader().getEmail() != null ? project.getUploader().getEmail() : "N/A");
//            } else {
//                builder.clientId(null);
//                builder.clientName("N/A");
//                builder.clientEmail("N/A");
//            }
//            if (project.getAssignedManager() != null) {
//                builder.assignedManagerName(project.getAssignedManager().getName());
//            }
//            if (project.getDeadline() != null) {
//                builder.deadline(project.getDeadline().toString());
//            }
//
//            result.add(builder.build());
//        }
//
//        result.sort((a, b) -> {
//            try {
//                double aRemaining = Double.parseDouble(a.getRemainingAmount());
//                double bRemaining = Double.parseDouble(b.getRemainingAmount());
//                return Double.compare(bRemaining, aRemaining);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        printDebugResponse("getAllProjectsWithPaymentSummary", result);
//        return result;
//    }
//
//    public List<ProjectPaymentSummaryDTO> getProjectsByDepartmentWithPaymentSummary(Long headDirectorId, Long departmentId) {
//        validateHeadDirector(headDirectorId);
//
//        if (departmentId == null) {
//            throw new RuntimeException("Department ID is required");
//        }
//
//        List<ProjectDetails> departmentProjects = projectRepository.findByDepartmentDepartmentId(departmentId);
//
//        if (departmentProjects == null || departmentProjects.isEmpty()) {
//            System.out.println("No projects found for department: " + departmentId);
//            return new ArrayList<>();
//        }
//
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : departmentProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
//            List<PaymentResponseDTO> paymentDTOs = projectPayments != null ?
//                    projectPayments.stream().map(this::convertToDTO).collect(Collectors.toList()) :
//                    new ArrayList<>();
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .payments(paymentDTOs)
//                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
//                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
//                    .build();
//
//            result.add(summary);
//        }
//
//        result.sort((a, b) -> {
//            try {
//                double aRemaining = Double.parseDouble(a.getRemainingAmount());
//                double bRemaining = Double.parseDouble(b.getRemainingAmount());
//                return Double.compare(bRemaining, aRemaining);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        printDebugResponse("getProjectsByDepartmentWithPaymentSummary", result);
//        return result;
//    }
//
//    public PaymentApprovalCountDTO getPaymentApprovalCount(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        long pendingCount = paymentRepository.countByStatus("PENDING");
//        long approvedCount = paymentRepository.countByStatus("APPROVED");
//        long rejectedCount = paymentRepository.countByStatus("REJECTED");
//
//        PaymentApprovalCountDTO result = PaymentApprovalCountDTO.builder()
//                .pendingCount(pendingCount)
//                .approvedCount(approvedCount)
//                .rejectedCount(rejectedCount)
//                .build();
//
//        printDebugResponse("getPaymentApprovalCount", result);
//        return result;
//    }
//
//    public List<DepartmentPaymentSummaryDTO> getDepartmentWisePaymentSummary(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        Map<Long, DepartmentPaymentSummaryDTO> departmentMap = new HashMap<>();
//
//        for (ProjectDetails project : allProjects) {
//            Long deptId = project.getDepartment() != null ? project.getDepartment().getDepartmentId() : -1L;
//            String deptName = project.getDepartment() != null && project.getDepartment().getDepartmentName() != null ?
//                    project.getDepartment().getDepartmentName() : "Unassigned";
//
//            DepartmentPaymentSummaryDTO deptSummary = departmentMap.get(deptId);
//            if (deptSummary == null) {
//                deptSummary = DepartmentPaymentSummaryDTO.builder()
//                        .departmentId(deptId)
//                        .departmentName(deptName)
//                        .totalProjects(0)
//                        .totalBudget(0.0)
//                        .totalPaid(0.0)
//                        .totalRemaining(0.0)
//                        .projects(new ArrayList<>())
//                        .build();
//                departmentMap.put(deptId, deptSummary);
//            }
//
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//
//            deptSummary.setTotalProjects(deptSummary.getTotalProjects() + 1);
//            deptSummary.setTotalBudget(deptSummary.getTotalBudget() + totalProjectAmount);
//            deptSummary.setTotalPaid(deptSummary.getTotalPaid() + paidAmount);
//            deptSummary.setTotalRemaining(deptSummary.getTotalRemaining() + remainingAmount);
//
//            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .build();
//
//            deptSummary.getProjects().add(projectSummary);
//        }
//
//        List<DepartmentPaymentSummaryDTO> result = new ArrayList<>(departmentMap.values());
//        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
//
//        printDebugResponse("getDepartmentWisePaymentSummary", result);
//        return result;
//    }
//
//    public List<ClientPaymentSummaryDTO> getClientWisePaymentSummary(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        Map<Long, ClientPaymentSummaryDTO> clientMap = new HashMap<>();
//
//        for (ProjectDetails project : allProjects) {
//            Long clientId = project.getUploader() != null ? project.getUploader().getUserId() : -1L;
//            String clientName = project.getUploader() != null && project.getUploader().getName() != null ?
//                    project.getUploader().getName() : "Unknown Client";
//            String clientEmail = project.getUploader() != null && project.getUploader().getEmail() != null ?
//                    project.getUploader().getEmail() : "unknown@example.com";
//
//            ClientPaymentSummaryDTO clientSummary = clientMap.get(clientId);
//            if (clientSummary == null) {
//                clientSummary = ClientPaymentSummaryDTO.builder()
//                        .clientId(clientId)
//                        .clientName(clientName)
//                        .clientEmail(clientEmail)
//                        .totalProjects(0)
//                        .totalBudget(0.0)
//                        .totalPaid(0.0)
//                        .totalRemaining(0.0)
//                        .projects(new ArrayList<>())
//                        .build();
//                clientMap.put(clientId, clientSummary);
//            }
//
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//
//            clientSummary.setTotalProjects(clientSummary.getTotalProjects() + 1);
//            clientSummary.setTotalBudget(clientSummary.getTotalBudget() + totalProjectAmount);
//            clientSummary.setTotalPaid(clientSummary.getTotalPaid() + paidAmount);
//            clientSummary.setTotalRemaining(clientSummary.getTotalRemaining() + remainingAmount);
//
//            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .build();
//
//            clientSummary.getProjects().add(projectSummary);
//        }
//
//        List<ClientPaymentSummaryDTO> result = new ArrayList<>(clientMap.values());
//        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
//
//        printDebugResponse("getClientWisePaymentSummary", result);
//        return result;
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
//        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
//        LocalDateTime now = LocalDateTime.now();
//
//        for (int i = 5; i >= 0; i--) {
//            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
//            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
//            dto.setMonth(month);
//            dto.setTotalRevenue(0.0);
//            dto.setTotalExpenses(0.0);
//            dto.setNetProfit(0.0);
//            dto.setProjectPaymentsCount(0);
//            dto.setSalaryPaymentsCount(0);
//            monthlyMap.put(month, dto);
//        }
//
//        for (PaymentDetails p : payments) {
//            if (p.getCreatedAt() == null) continue;
//            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
//            MonthlyRevenueDTO dto = monthlyMap.get(month);
//            if (dto != null) {
//                double amount = safeParseDouble(p.getAmount());
//                if ("PROJECT_PAYMENT".equals(p.getType())) {
//                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
//                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
//                } else if ("SALARY".equals(p.getType())) {
//                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
//                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
//                }
//                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
//            }
//        }
//
//        return new ArrayList<>(monthlyMap.values());
//    }
//
//    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
//        List<ProjectDetails> projects = projectRepository.findAll();
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : projects) {
//            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            if (totalPaid != null && totalPaid > 0) {
//                double totalAmount = safeParseDouble(project.getCost());
//                ProjectPaymentSummaryDTO dto = ProjectPaymentSummaryDTO.builder()
//                        .projectId(project.getProjectId())
//                        .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                        .totalProjectAmount(formatAmount(totalAmount))
//                        .paidAmount(formatAmount(totalPaid))
//                        .remainingAmount(formatAmount(totalAmount - totalPaid))
//                        .paidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0)
//                        .isFullyPaid(totalPaid >= totalAmount)
//                        .build();
//                result.add(dto);
//            }
//        }
//
//        result.sort((a, b) -> {
//            try {
//                double aPaid = Double.parseDouble(a.getPaidAmount());
//                double bPaid = Double.parseDouble(b.getPaidAmount());
//                return Double.compare(bPaid, aPaid);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        return result.stream().limit(5).collect(Collectors.toList());
//    }
//
//    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
//        if (payment == null) return null;
//
//        PaymentResponseDTO dto = new PaymentResponseDTO();
//        dto.setPaymentId(payment.getPaymentId());
//        dto.setTransactionId(payment.getTransactionId());
//        dto.setAmount(formatAmount(safeParseDouble(payment.getAmount())));
//        dto.setType(payment.getType());
//        dto.setStatus(payment.getStatus());
//
//        if (payment.getUser() != null) {
//            dto.setUserName(payment.getUser().getName() != null ? payment.getUser().getName() : "N/A");
//            dto.setUserEmail(payment.getUser().getEmail() != null ? payment.getUser().getEmail() : "N/A");
//        } else {
//            dto.setUserName("N/A");
//            dto.setUserEmail("N/A");
//        }
//
//        dto.setPaymentNote(payment.getPaymentNote());
//        dto.setDirectorNote(payment.getDirectorNote());
//
//        if (payment.getApprovedBy() != null) {
//            dto.setApprovedByName(payment.getApprovedBy().getName());
//        }
//
//        dto.setApprovedAt(payment.getApprovedAt());
//        dto.setDateTime(payment.getDateTime());
//
//        return dto;
//    }
//
//    // ==================== EMAIL METHODS ====================
//
//    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
//        try {
//            List<UserType> userTypes = userTypeRepository.findAll();
//            User headDirector = userTypes.stream()
//                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
//                    .map(UserType::getUser)
//                    .findFirst()
//                    .orElse(null);
//
//            if (headDirector != null && headDirector.getEmail() != null) {
//                String subject = "💰 New Payment Request - " + payment.getType() + " - Softmax Technologies and IT Services";
//                String safeNote = EmailService.makeLinksClickable(payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note");
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
//                            .header h1 { color: white; margin: 0; font-size: 26px; }
//                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                            .content { padding: 40px 35px; }
//                            .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
//                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                            .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
//                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                            .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>💰 New Payment Request</h1>
//                                <p>Softmax Technologies and IT Services</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                                <p style="color:#4a5568;font-size:15px;">A new payment request has been submitted for your approval.</p>
//                                <div class="info-box">
//                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
//                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
//                                    <p>📝 <strong>Type:</strong> %s</p>
//                                    <p>👤 <strong>Submitted By:</strong> %s</p>
//                                    <p>📅 <strong>Date:</strong> %s</p>
//                                    <p>📝 <strong>Note:</strong></p>
//                                    <p>%s</p>
//                                </div>
//                                <p style="color:#4a5568;">Please login to approve or reject this payment.</p>
//                                <div style="text-align:center;">
//                                    <a href="https://nexus.softmax.in/head-director/payments" class="btn">🚀 Review Payment</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        headDirector.getName() != null ? headDirector.getName() : "Director",
//                        payment.getTransactionId(),
//                        payment.getAmount(),
//                        payment.getType(),
//                        user.getName() != null ? user.getName() : "User",
//                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A",
//                        safeNote
//                );
//                emailService.sendEmail(headDirector.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
//        }
//    }
//
//    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
//        try {
//            if (director != null && director.getEmail() != null) {
//                String subject = "💰 Salary Payment Request - " + (employee.getName() != null ? employee.getName() : "Employee") + " - Softmax Technologies and IT Services";
//
//                String html = String.format("""
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta charset="UTF-8">
//                        <style>
//                            body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                            .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
//                            .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 26px; }
//                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                            .content { padding: 40px 35px; }
//                            .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #667eea; }
//                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
//                            .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
//                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                            .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>💰 Salary Payment Request</h1>
//                                <p>Softmax Technologies and IT Services</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                                <p style="color:#4a5568;font-size:15px;">A salary payment request has been submitted.</p>
//                                <div class="info-box">
//                                    <p>👤 <strong>Employee:</strong> %s</p>
//                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
//                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
//                                    <p>📅 <strong>Date:</strong> %s</p>
//                                </div>
//                                <p style="color:#4a5568;">Please login to approve or reject this payment.</p>
//                                <div style="text-align:center;">
//                                    <a href="https://nexus.softmax.in/head-director/payments" class="btn">🚀 Review Payment</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        director.getName() != null ? director.getName() : "Director",
//                        employee.getName() != null ? employee.getName() : "Employee",
//                        payment.getAmount(),
//                        payment.getTransactionId(),
//                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A"
//                );
//                emailService.sendEmail(director.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
//        }
//    }
//
//    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
//        try {
//            if (payment.getUser() != null && payment.getUser().getEmail() != null) {
//                String subject = (status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected") + " - Softmax Technologies and IT Services";
//                String color = status.equals("APPROVED") ? "#11998e" : "#ee5a24";
//                String icon = status.equals("APPROVED") ? "✅" : "❌";
//                String heading = status.equals("APPROVED") ? "Payment Approved" : "Payment Rejected";
//
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
//                            .header { background: linear-gradient(135deg, %s 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center; }
//                            .header h1 { color: white; margin: 0; font-size: 26px; }
//                            .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
//                            .content { padding: 40px 35px; }
//                            .info-box { background: #f0f4ff; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid %s; }
//                            .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; line-height: 1.6; }
//                            .info-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                            .note-box { background: #fff9e6; padding: 16px 20px; border-radius: 10px; margin-top: 15px; border-left: 5px solid #f6c23e; }
//                            .note-box p { margin: 6px 0; color: #5c4a00; font-size: 14px; line-height: 1.6; }
//                            .note-box a { color: #2196F3; text-decoration: underline; word-break: break-all; }
//                            .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%%,#764ba2 100%%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
//                            .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
//                            .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
//                        </style>
//                    </head>
//                    <body>
//                        <div class="container">
//                            <div class="header">
//                                <h1>%s %s</h1>
//                                <p>Softmax Technologies and IT Services</p>
//                            </div>
//                            <div class="content">
//                                <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                                <p style="color:#4a5568;font-size:15px;">Your payment request has been <strong style="color:%s;">%s</strong>.</p>
//                                <div class="info-box">
//                                    <p>📋 <strong>Transaction ID:</strong> %s</p>
//                                    <p>💰 <strong>Amount:</strong> ₹%s</p>
//                                    <div class="note-box">
//                                        <p><strong>📝 Director Note:</strong></p>
//                                        <p>%s</p>
//                                    </div>
//                                </div>
//                                <div style="text-align:center;">
//                                    <a href="https://nexus.softmax.in/dashboard" class="btn">🚀 View Dashboard</a>
//                                </div>
//                            </div>
//                            <div class="footer">
//                                <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                                <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
//                                <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
//                                <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - Payment System</p>
//                            </div>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        color, color,
//                        icon, heading,
//                        payment.getUser().getName() != null ? payment.getUser().getName() : "User",
//                        color, status,
//                        payment.getTransactionId(),
//                        payment.getAmount(),
//                        safeNote
//                );
//                emailService.sendEmail(payment.getUser().getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
//        }
//    }
//}









//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
//import com.softmax.shardaos.dto.input.CreatePaymentRequest;
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import com.fasterxml.jackson.databind.ObjectMapper;
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
//public class PaymentService {
//
//    @Autowired
//    private PaymentDetailsRepository paymentRepository;
//
//    @Autowired
//    private ProjectPaymentRepository projectPaymentRepository;
//
//    @Autowired
//    private SalaryPaymentRepository salaryPaymentRepository;
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
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    // Helper method to print debug response
//    private void printDebugResponse(String methodName, Object response) {
//        try {
//            System.out.println("\n========== DEBUG RESPONSE ==========");
//            System.out.println("Method: " + methodName);
//            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
//            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//            System.out.println("Response JSON:");
//            System.out.println(jsonResponse);
//            System.out.println("=====================================\n");
//        } catch (Exception e) {
//            System.out.println("Error printing debug response: " + e.getMessage());
//        }
//    }
//
//    // IMPROVED: Safe parse double with better handling of Indian currency format
//    private double safeParseDouble(String value) {
//        if (value == null || value.trim().isEmpty()) {
//            return 0.0;
//        }
//        try {
//            // Remove ₹ symbol, commas, spaces, and any non-numeric except decimal
//            String cleaned = value.toString().replaceAll("[^\\d.]", "");
//            if (cleaned.isEmpty() || cleaned.equals(".")) {
//                return 0.0;
//            }
//            // Handle multiple decimal points - keep only first one
//            int firstDot = cleaned.indexOf('.');
//            if (firstDot != -1) {
//                cleaned = cleaned.substring(0, firstDot + 1) +
//                        cleaned.substring(firstDot + 1).replaceAll("\\.", "");
//            }
//            return Double.parseDouble(cleaned);
//        } catch (NumberFormatException e) {
//            System.out.println("Error parsing double: " + value);
//            return 0.0;
//        }
//    }
//
//    // IMPROVED: Format amount for display
//    private String formatAmount(double amount) {
//        return String.format("%.2f", amount);
//    }
//
//    // ==================== VALIDATION ====================
//
//    public boolean isHeadDirector(Long userId) {
//        if (userId == null) return false;
//        try {
//            List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//            boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//            boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//            if (!isEmp || !isDirector) return false;
//            EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//            return emp != null && emp.getDepartmentRef() != null &&
//                    HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
//        } catch (Exception e) {
//            System.out.println("Error checking Head Director: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private void validateHeadDirector(Long userId) {
//        if (userId == null) {
//            throw new RuntimeException("Authentication required: User ID is null");
//        }
//        if (!isHeadDirector(userId)) {
//            throw new RuntimeException("Access denied: Only Head Director can manage payments");
//        }
//    }
//
//    private void validateProjectOwner(Long userId, Long projectId) {
//        if (userId == null) {
//            throw new RuntimeException("Authentication required: User ID is null");
//        }
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
//        if (project.getUploader() == null || !project.getUploader().getUserId().equals(userId)) {
//            throw new RuntimeException("Access denied: You can only access your own projects");
//        }
//    }
//
//    // ==================== CLIENT APIs ====================
//
//    public List<PaymentResponseDTO> getMyProjectPayments(Long userId, Long projectId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        validateProjectOwner(userId, projectId);
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        List<PaymentResponseDTO> response = payments.stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getMyProjectPayments", response);
//        return response;
//    }
//
//    public ProjectPaymentSummaryDTO getMyProjectPaymentSummary(Long userId, Long projectId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        validateProjectOwner(userId, projectId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//
//        double totalProjectAmount = safeParseDouble(project.getCost());
//        double paidAmount = totalApproved != null ? totalApproved : 0.0;
//        double remainingAmount = totalProjectAmount - paidAmount;
//        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//        List<PaymentResponseDTO> approvedPayments = payments.stream()
//                .filter(p -> "APPROVED".equals(p.getStatus()))
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//
//        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//                .projectId(project.getProjectId())
//                .projectName(project.getProjectName())
//                .totalProjectAmount(formatAmount(totalProjectAmount))
//                .paidAmount(formatAmount(paidAmount))
//                .remainingAmount(formatAmount(remainingAmount))
//                .paidPercentage(paidPercentage)
//                .isFullyPaid(remainingAmount <= 0)
//                .payments(approvedPayments)
//                .build();
//
//        printDebugResponse("getMyProjectPaymentSummary", response);
//        return response;
//    }
//
//    public List<ProjectPaymentSummaryDTO> getAllMyProjectsPayments(Long userId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//        List<ProjectPaymentSummaryDTO> response = new ArrayList<>();
//
//        for (ProjectDetails project : myProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(paidPercentage)
//                    .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
//                    .payments(new ArrayList<>())
//                    .build();
//
//            response.add(summary);
//        }
//
//        response.sort((a, b) -> a.getProjectName().compareTo(b.getProjectName()));
//        printDebugResponse("getAllMyProjectsPayments", response);
//        return response;
//    }
//
//    public PaymentResponseDTO getMyPaymentByTransactionId(Long userId, String transactionId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        if (transactionId == null) throw new RuntimeException("Transaction ID is required");
//
//        PaymentDetails payment = paymentRepository.findByTransactionId(transactionId)
//                .orElseThrow(() -> new RuntimeException("Payment not found for transaction: " + transactionId));
//
//        if ("PROJECT_PAYMENT".equals(payment.getType())) {
//            Optional<ProjectPayment> projectPaymentOpt = projectPaymentRepository.findByPayment(payment);
//            if (projectPaymentOpt.isPresent()) {
//                ProjectPayment projectPayment = projectPaymentOpt.get();
//                validateProjectOwner(userId, projectPayment.getProject().getProjectId());
//                return convertToDTO(payment);
//            } else {
//                throw new RuntimeException("Project payment mapping not found");
//            }
//        } else {
//            throw new RuntimeException("Invalid payment type for client access");
//        }
//    }
//
//    public ClientPaymentDashboardDTO getMyPaymentDashboard(Long userId) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//
//        double totalProjectCost = 0.0;
//        double totalPaidAmount = 0.0;
//        int totalProjects = myProjects.size();
//        int completedProjects = 0;
//        int pendingProjects = 0;
//
//        List<ProjectPaymentSummaryDTO> projectSummaries = new ArrayList<>();
//
//        for (ProjectDetails project : myProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double projectCost = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = projectCost - paidAmount;
//
//            totalProjectCost += projectCost;
//            totalPaidAmount += paidAmount;
//
//            if (remainingAmount <= 0 && projectCost > 0) {
//                completedProjects++;
//            } else if (projectCost > 0) {
//                pendingProjects++;
//            }
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .totalProjectAmount(formatAmount(projectCost))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(projectCost > 0 ? (paidAmount / projectCost) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0 && projectCost > 0)
//                    .build();
//
//            projectSummaries.add(summary);
//        }
//
//        double totalRemaining = totalProjectCost - totalPaidAmount;
//        double overallPaidPercentage = totalProjectCost > 0 ? (totalPaidAmount / totalProjectCost) * 100 : 0;
//
//        ClientPaymentDashboardDTO response = ClientPaymentDashboardDTO.builder()
//                .totalProjects(totalProjects)
//                .totalProjectCost(formatAmount(totalProjectCost))
//                .totalPaidAmount(formatAmount(totalPaidAmount))
//                .totalRemainingAmount(formatAmount(totalRemaining))
//                .overallPaidPercentage(overallPaidPercentage)
//                .completedProjects(completedProjects)
//                .pendingProjects(pendingProjects)
//                .projects(projectSummaries)
//                .build();
//
//        printDebugResponse("getMyPaymentDashboard", response);
//        return response;
//    }
//
//    // ==================== CREATE PAYMENT ====================
//
//    @Transactional
//    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
//        if (userId == null) throw new RuntimeException("User ID is required");
//        if (request == null) throw new RuntimeException("Payment request is required");
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
//
//        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
//            throw new RuntimeException("Transaction ID is required");
//        }
//        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
//            throw new RuntimeException("Amount is required");
//        }
//        if (request.getProjectId() == null) {
//            throw new RuntimeException("Project ID is required");
//        }
//
//        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//            throw new RuntimeException("Transaction ID already exists: " + request.getTransactionId());
//        }
//
//        ProjectDetails project = projectRepository.findById(request.getProjectId())
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
//
//        PaymentDetails payment = new PaymentDetails();
//        payment.setTransactionId(request.getTransactionId());
//        payment.setAmount(request.getAmount());
//        payment.setType("PROJECT_PAYMENT");
//        payment.setStatus("PENDING");
//        payment.setUser(user);
//        payment.setPaymentNote(request.getPaymentNote());
//        payment.setDateTime(LocalDateTime.now());
//
//        PaymentDetails savedPayment = paymentRepository.save(payment);
//
//        ProjectPayment projectPayment = new ProjectPayment();
//        projectPayment.setProject(project);
//        projectPayment.setPayment(savedPayment);
//        projectPaymentRepository.save(projectPayment);
//
//        sendPaymentNotificationEmail(savedPayment, user);
//
//        return convertToDTO(savedPayment);
//    }
//
//    @Transactional
//    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
//        validateHeadDirector(userId);
//
//        if (request == null) throw new RuntimeException("Payment request is required");
//        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
//            throw new RuntimeException("Transaction ID is required");
//        }
//        if (request.getAmount() == null || request.getAmount().trim().isEmpty()) {
//            throw new RuntimeException("Amount is required");
//        }
//        if (request.getUserId() == null) {
//            throw new RuntimeException("Employee User ID is required");
//        }
//
//        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//            throw new RuntimeException("Transaction ID already exists");
//        }
//
//        User employee = userRepository.findById(request.getUserId())
//                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + request.getUserId()));
//
//        PaymentDetails payment = new PaymentDetails();
//        payment.setTransactionId(request.getTransactionId());
//        payment.setAmount(request.getAmount());
//        payment.setType("SALARY");
//        payment.setStatus("PENDING");
//        payment.setUser(employee);
//        payment.setPaymentNote(request.getPaymentNote());
//        payment.setDateTime(LocalDateTime.now());
//
//        PaymentDetails savedPayment = paymentRepository.save(payment);
//
//        SalaryPayment salaryPayment = new SalaryPayment();
//        salaryPayment.setUser(employee);
//        salaryPayment.setPayment(savedPayment);
//        salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
//        salaryPayment.setSalaryAmount(request.getAmount());
//        salaryPaymentRepository.save(salaryPayment);
//
//        User director = userRepository.findById(userId).orElseThrow();
//        sendSalaryPaymentNotificationEmail(savedPayment, employee, director);
//
//        return convertToDTO(savedPayment);
//    }
//
//    // ==================== HEAD DIRECTOR APIs ====================
//
//    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//        List<PaymentResponseDTO> response = paymentRepository.findByStatus("PENDING").stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getPendingPayments", response);
//        return response;
//    }
//
//    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//        List<PaymentResponseDTO> response = paymentRepository.findAll().stream()
//                .sorted((a, b) -> {
//                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
//                    return b.getCreatedAt().compareTo(a.getCreatedAt());
//                })
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//        printDebugResponse("getAllPayments", response);
//        return response;
//    }
//
//    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
//        validateHeadDirector(headDirectorId);
//        PaymentDetails payment = paymentRepository.findById(paymentId)
//                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
//        return convertToDTO(payment);
//    }
//
//    @Transactional
//    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
//        validateHeadDirector(headDirectorId);
//
//        if (request == null) throw new RuntimeException("Approval request is required");
//        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
//            throw new RuntimeException("Status is required (APPROVED/REJECTED)");
//        }
//
//        User director = userRepository.findById(headDirectorId)
//                .orElseThrow(() -> new RuntimeException("Director not found"));
//        PaymentDetails payment = paymentRepository.findById(paymentId)
//                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
//
//        if (!"PENDING".equals(payment.getStatus())) {
//            throw new RuntimeException("Payment is already " + payment.getStatus());
//        }
//
//        payment.setStatus(request.getStatus());
//        payment.setApprovedBy(director);
//        payment.setApprovedAt(LocalDateTime.now());
//        payment.setDirectorNote(request.getDirectorNote());
//
//        PaymentDetails updated = paymentRepository.save(payment);
//        sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());
//
//        return convertToDTO(updated);
//    }
//
//    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
//        validateHeadDirector(headDirectorId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
//
//        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//
//        double totalProjectAmount = safeParseDouble(project.getCost());
//        double paidAmount = totalApproved != null ? totalApproved : 0.0;
//        double remainingAmount = totalProjectAmount - paidAmount;
//        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//        ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//                .projectId(project.getProjectId())
//                .projectName(project.getProjectName())
//                .totalProjectAmount(formatAmount(totalProjectAmount))
//                .paidAmount(formatAmount(paidAmount))
//                .remainingAmount(formatAmount(remainingAmount))
//                .paidPercentage(paidPercentage)
//                .isFullyPaid(remainingAmount <= 0)
//                .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
//                .build();
//
//        printDebugResponse("getProjectPayments", response);
//        return response;
//    }
//
//    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
//        validateHeadDirector(headDirectorId);
//
//        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
//        List<PaymentResponseDTO> response = salaryPayments.stream()
//                .map(sp -> convertToDTO(sp.getPayment()))
//                .collect(Collectors.toList());
//
//        printDebugResponse("getSalaryPaymentsByUser", response);
//        return response;
//    }
//
//    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<PaymentDetails> allPayments = paymentRepository.findAll();
//        List<PaymentDetails> approvedPayments = allPayments.stream()
//                .filter(p -> "APPROVED".equals(p.getStatus()))
//                .collect(Collectors.toList());
//
//        double totalRevenue = 0.0;
//        double totalExpenses = 0.0;
//
//        for (PaymentDetails p : approvedPayments) {
//            double amount = safeParseDouble(p.getAmount());
//            if ("PROJECT_PAYMENT".equals(p.getType())) {
//                totalRevenue += amount;
//            } else if ("SALARY".equals(p.getType())) {
//                totalExpenses += amount;
//            }
//        }
//
//        long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
//
//        PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
//                .totalRevenue(totalRevenue)
//                .totalExpenses(totalExpenses)
//                .netProfit(totalRevenue - totalExpenses)
//                .pendingApprovals((int) pendingApprovals)
//                .totalTransactions(allPayments.size())
//                .approvedTransactions(approvedPayments.size())
//                .build();
//
//        List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);
//
//        List<PaymentResponseDTO> recentTransactions = allPayments.stream()
//                .sorted((a, b) -> {
//                    if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
//                    return b.getCreatedAt().compareTo(a.getCreatedAt());
//                })
//                .limit(10)
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//
//        List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();
//
//        Map<String, Double> revenueByType = new HashMap<>();
//        revenueByType.put("PROJECT_PAYMENT", totalRevenue);
//        revenueByType.put("SALARY", totalExpenses);
//
//        DashboardPaymentStatsDTO response = DashboardPaymentStatsDTO.builder()
//                .summary(summary)
//                .monthlyRevenue(monthlyRevenue)
//                .recentTransactions(recentTransactions)
//                .topProjects(topProjects)
//                .revenueByType(revenueByType)
//                .build();
//
//        printDebugResponse("getDashboardStats", response);
//        return response;
//    }
//
//    // ==================== HEAD DIRECTOR - PROJECT WISE PAYMENT SUMMARY APIs ====================
//
//    /**
//     * Get all projects with complete payment summaries for Head Director
//     * Shows project-wise: total amount, paid amount, remaining amount, payment percentage
//     * Also shows department info, client info, and all payment transactions
//     *
//     * FIXED: Now properly shows ALL projects with their payment details
//     * No 401/403 errors - proper validation
//     * No compilation errors - all DTO fields properly handled
//     */
//    public List<ProjectPaymentSummaryDTO> getAllProjectsWithPaymentSummary(Long headDirectorId) {
//        // Validate Head Director - returns 403 if fails
//        validateHeadDirector(headDirectorId);
//
//        // Get ALL projects from database
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//
//        if (allProjects == null || allProjects.isEmpty()) {
//            System.out.println("No projects found in database");
//            return new ArrayList<>();
//        }
//
//        System.out.println("Total projects found: " + allProjects.size());
//
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : allProjects) {
//            // Calculate payment details for each project
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            // Get all payments (APPROVED + PENDING + REJECTED) for this project
//            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
//            List<PaymentResponseDTO> paymentDTOs = new ArrayList<>();
//            if (projectPayments != null) {
//                paymentDTOs = projectPayments.stream()
//                        .map(this::convertToDTO)
//                        .collect(Collectors.toList());
//            }
//
//            System.out.println("Project: " + project.getProjectName() +
//                    " | Total: " + totalProjectAmount +
//                    " | Paid: " + paidAmount +
//                    " | Remaining: " + remainingAmount +
//                    " | Payments: " + paymentDTOs.size());
//
//            // Build DTO with all fields - null safe
//            ProjectPaymentSummaryDTO.ProjectPaymentSummaryDTOBuilder builder = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)  // Round to 2 decimals
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .payments(paymentDTOs);
//
//            // Add optional fields safely
//            if (project.getProjectDescription() != null) {
//                builder.projectDescription(project.getProjectDescription());
//            }
//            if (project.getProjectType() != null) {
//                builder.projectType(project.getProjectType());
//            }
//            if (project.getStatus() != null) {
//                builder.status(project.getStatus());
//            }
//            if (project.getDepartment() != null) {
//                builder.departmentId(project.getDepartment().getDepartmentId());
//                builder.departmentName(project.getDepartment().getDepartmentName() != null ?
//                        project.getDepartment().getDepartmentName() : "N/A");
//            } else {
//                builder.departmentId(null);
//                builder.departmentName("N/A");
//            }
//            if (project.getUploader() != null) {
//                builder.clientId(project.getUploader().getUserId());
//                builder.clientName(project.getUploader().getName() != null ? project.getUploader().getName() : "N/A");
//                builder.clientEmail(project.getUploader().getEmail() != null ? project.getUploader().getEmail() : "N/A");
//            } else {
//                builder.clientId(null);
//                builder.clientName("N/A");
//                builder.clientEmail("N/A");
//            }
//            if (project.getAssignedManager() != null) {
//                builder.assignedManagerName(project.getAssignedManager().getName());
//            }
//            if (project.getDeadline() != null) {
//                builder.deadline(project.getDeadline().toString());
//            }
//
//            result.add(builder.build());
//        }
//
//        // Sort by remaining amount (highest first) - projects with most pending payment come first
//        result.sort((a, b) -> {
//            try {
//                double aRemaining = Double.parseDouble(a.getRemainingAmount());
//                double bRemaining = Double.parseDouble(b.getRemainingAmount());
//                return Double.compare(bRemaining, aRemaining);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        printDebugResponse("getAllProjectsWithPaymentSummary", result);
//        return result;
//    }
//
//    /**
//     * Get projects filtered by department with payment summaries
//     */
//    public List<ProjectPaymentSummaryDTO> getProjectsByDepartmentWithPaymentSummary(Long headDirectorId, Long departmentId) {
//        validateHeadDirector(headDirectorId);
//
//        if (departmentId == null) {
//            throw new RuntimeException("Department ID is required");
//        }
//
//        List<ProjectDetails> departmentProjects = projectRepository.findByDepartmentDepartmentId(departmentId);
//
//        if (departmentProjects == null || departmentProjects.isEmpty()) {
//            System.out.println("No projects found for department: " + departmentId);
//            return new ArrayList<>();
//        }
//
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : departmentProjects) {
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//
//            List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
//            List<PaymentResponseDTO> paymentDTOs = projectPayments != null ?
//                    projectPayments.stream().map(this::convertToDTO).collect(Collectors.toList()) :
//                    new ArrayList<>();
//
//            ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(Math.round(paidPercentage * 100.0) / 100.0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .payments(paymentDTOs)
//                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
//                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
//                    .build();
//
//            result.add(summary);
//        }
//
//        result.sort((a, b) -> {
//            try {
//                double aRemaining = Double.parseDouble(a.getRemainingAmount());
//                double bRemaining = Double.parseDouble(b.getRemainingAmount());
//                return Double.compare(bRemaining, aRemaining);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        printDebugResponse("getProjectsByDepartmentWithPaymentSummary", result);
//        return result;
//    }
//
//    /**
//     * Get payment approval counts for Head Director dashboard
//     */
//    public PaymentApprovalCountDTO getPaymentApprovalCount(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        long pendingCount = paymentRepository.countByStatus("PENDING");
//        long approvedCount = paymentRepository.countByStatus("APPROVED");
//        long rejectedCount = paymentRepository.countByStatus("REJECTED");
//
//        PaymentApprovalCountDTO result = PaymentApprovalCountDTO.builder()
//                .pendingCount(pendingCount)
//                .approvedCount(approvedCount)
//                .rejectedCount(rejectedCount)
//                .build();
//
//        printDebugResponse("getPaymentApprovalCount", result);
//        return result;
//    }
//
//    /**
//     * Get department-wise payment summary for Head Director
//     */
//    public List<DepartmentPaymentSummaryDTO> getDepartmentWisePaymentSummary(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        Map<Long, DepartmentPaymentSummaryDTO> departmentMap = new HashMap<>();
//
//        for (ProjectDetails project : allProjects) {
//            Long deptId = project.getDepartment() != null ? project.getDepartment().getDepartmentId() : -1L;
//            String deptName = project.getDepartment() != null && project.getDepartment().getDepartmentName() != null ?
//                    project.getDepartment().getDepartmentName() : "Unassigned";
//
//            DepartmentPaymentSummaryDTO deptSummary = departmentMap.get(deptId);
//            if (deptSummary == null) {
//                deptSummary = DepartmentPaymentSummaryDTO.builder()
//                        .departmentId(deptId)
//                        .departmentName(deptName)
//                        .totalProjects(0)
//                        .totalBudget(0.0)
//                        .totalPaid(0.0)
//                        .totalRemaining(0.0)
//                        .projects(new ArrayList<>())
//                        .build();
//                departmentMap.put(deptId, deptSummary);
//            }
//
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//
//            deptSummary.setTotalProjects(deptSummary.getTotalProjects() + 1);
//            deptSummary.setTotalBudget(deptSummary.getTotalBudget() + totalProjectAmount);
//            deptSummary.setTotalPaid(deptSummary.getTotalPaid() + paidAmount);
//            deptSummary.setTotalRemaining(deptSummary.getTotalRemaining() + remainingAmount);
//
//            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .build();
//
//            deptSummary.getProjects().add(projectSummary);
//        }
//
//        List<DepartmentPaymentSummaryDTO> result = new ArrayList<>(departmentMap.values());
//        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
//
//        printDebugResponse("getDepartmentWisePaymentSummary", result);
//        return result;
//    }
//
//    /**
//     * Get client-wise payment summary for Head Director
//     */
//    public List<ClientPaymentSummaryDTO> getClientWisePaymentSummary(Long headDirectorId) {
//        validateHeadDirector(headDirectorId);
//
//        List<ProjectDetails> allProjects = projectRepository.findAll();
//        Map<Long, ClientPaymentSummaryDTO> clientMap = new HashMap<>();
//
//        for (ProjectDetails project : allProjects) {
//            Long clientId = project.getUploader() != null ? project.getUploader().getUserId() : -1L;
//            String clientName = project.getUploader() != null && project.getUploader().getName() != null ?
//                    project.getUploader().getName() : "Unknown Client";
//            String clientEmail = project.getUploader() != null && project.getUploader().getEmail() != null ?
//                    project.getUploader().getEmail() : "unknown@example.com";
//
//            ClientPaymentSummaryDTO clientSummary = clientMap.get(clientId);
//            if (clientSummary == null) {
//                clientSummary = ClientPaymentSummaryDTO.builder()
//                        .clientId(clientId)
//                        .clientName(clientName)
//                        .clientEmail(clientEmail)
//                        .totalProjects(0)
//                        .totalBudget(0.0)
//                        .totalPaid(0.0)
//                        .totalRemaining(0.0)
//                        .projects(new ArrayList<>())
//                        .build();
//                clientMap.put(clientId, clientSummary);
//            }
//
//            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            double totalProjectAmount = safeParseDouble(project.getCost());
//            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//            double remainingAmount = totalProjectAmount - paidAmount;
//
//            clientSummary.setTotalProjects(clientSummary.getTotalProjects() + 1);
//            clientSummary.setTotalBudget(clientSummary.getTotalBudget() + totalProjectAmount);
//            clientSummary.setTotalPaid(clientSummary.getTotalPaid() + paidAmount);
//            clientSummary.setTotalRemaining(clientSummary.getTotalRemaining() + remainingAmount);
//
//            ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
//                    .totalProjectAmount(formatAmount(totalProjectAmount))
//                    .paidAmount(formatAmount(paidAmount))
//                    .remainingAmount(formatAmount(remainingAmount))
//                    .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
//                    .isFullyPaid(remainingAmount <= 0.01 && totalProjectAmount > 0)
//                    .build();
//
//            clientSummary.getProjects().add(projectSummary);
//        }
//
//        List<ClientPaymentSummaryDTO> result = new ArrayList<>(clientMap.values());
//        result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
//
//        printDebugResponse("getClientWisePaymentSummary", result);
//        return result;
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
//        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
//        LocalDateTime now = LocalDateTime.now();
//
//        for (int i = 5; i >= 0; i--) {
//            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
//            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
//            dto.setMonth(month);
//            dto.setTotalRevenue(0.0);
//            dto.setTotalExpenses(0.0);
//            dto.setNetProfit(0.0);
//            dto.setProjectPaymentsCount(0);
//            dto.setSalaryPaymentsCount(0);
//            monthlyMap.put(month, dto);
//        }
//
//        for (PaymentDetails p : payments) {
//            if (p.getCreatedAt() == null) continue;
//            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
//            MonthlyRevenueDTO dto = monthlyMap.get(month);
//            if (dto != null) {
//                double amount = safeParseDouble(p.getAmount());
//                if ("PROJECT_PAYMENT".equals(p.getType())) {
//                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
//                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
//                } else if ("SALARY".equals(p.getType())) {
//                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
//                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
//                }
//                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
//            }
//        }
//
//        return new ArrayList<>(monthlyMap.values());
//    }
//
//    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
//        List<ProjectDetails> projects = projectRepository.findAll();
//        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//
//        for (ProjectDetails project : projects) {
//            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//            if (totalPaid != null && totalPaid > 0) {
//                double totalAmount = safeParseDouble(project.getCost());
//                ProjectPaymentSummaryDTO dto = ProjectPaymentSummaryDTO.builder()
//                        .projectId(project.getProjectId())
//                        .projectName(project.getProjectName() != null ? project.getProjectName() : "N/A")
//                        .totalProjectAmount(formatAmount(totalAmount))
//                        .paidAmount(formatAmount(totalPaid))
//                        .remainingAmount(formatAmount(totalAmount - totalPaid))
//                        .paidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0)
//                        .isFullyPaid(totalPaid >= totalAmount)
//                        .build();
//                result.add(dto);
//            }
//        }
//
//        result.sort((a, b) -> {
//            try {
//                double aPaid = Double.parseDouble(a.getPaidAmount());
//                double bPaid = Double.parseDouble(b.getPaidAmount());
//                return Double.compare(bPaid, aPaid);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        });
//
//        return result.stream().limit(5).collect(Collectors.toList());
//    }
//
//    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
//        if (payment == null) return null;
//
//        PaymentResponseDTO dto = new PaymentResponseDTO();
//        dto.setPaymentId(payment.getPaymentId());
//        dto.setTransactionId(payment.getTransactionId());
//        dto.setAmount(formatAmount(safeParseDouble(payment.getAmount())));
//        dto.setType(payment.getType());
//        dto.setStatus(payment.getStatus());
//
//        if (payment.getUser() != null) {
//            dto.setUserName(payment.getUser().getName() != null ? payment.getUser().getName() : "N/A");
//            dto.setUserEmail(payment.getUser().getEmail() != null ? payment.getUser().getEmail() : "N/A");
//        } else {
//            dto.setUserName("N/A");
//            dto.setUserEmail("N/A");
//        }
//
//        dto.setPaymentNote(payment.getPaymentNote());
//        dto.setDirectorNote(payment.getDirectorNote());
//
//        if (payment.getApprovedBy() != null) {
//            dto.setApprovedByName(payment.getApprovedBy().getName());
//        }
//
//        dto.setApprovedAt(payment.getApprovedAt());
//        dto.setDateTime(payment.getDateTime());
//
//        return dto;
//    }
//
//    // ==================== EMAIL METHODS ====================
//
//    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
//        try {
//            List<UserType> userTypes = userTypeRepository.findAll();
//            User headDirector = userTypes.stream()
//                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
//                    .map(UserType::getUser)
//                    .findFirst()
//                    .orElse(null);
//
//            if (headDirector != null && headDirector.getEmail() != null) {
//                String subject = "💰 New Payment Request - " + payment.getType();
//                String html = String.format("""
//                    <html>
//                    <body style='font-family: Arial, sans-serif;'>
//                        <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
//                            <h2 style='color: #4CAF50;'>💰 New Payment Request</h2>
//                            <p>Dear <strong>%s</strong>,</p>
//                            <p>A new payment request has been submitted for your approval.</p>
//                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                                <p><strong>📋 Transaction ID:</strong> %s</p>
//                                <p><strong>💰 Amount:</strong> ₹%s</p>
//                                <p><strong>📝 Type:</strong> %s</p>
//                                <p><strong>👤 Submitted By:</strong> %s</p>
//                                <p><strong>📅 Date:</strong> %s</p>
//                                <p><strong>📝 Note:</strong> %s</p>
//                            </div>
//                            <p>Please login to approve or reject this payment.</p>
//                            <hr>
//                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        headDirector.getName() != null ? headDirector.getName() : "Director",
//                        payment.getTransactionId(),
//                        payment.getAmount(),
//                        payment.getType(),
//                        user.getName() != null ? user.getName() : "User",
//                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A",
//                        payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note"
//                );
//                emailService.sendEmail(headDirector.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
//        }
//    }
//
//    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
//        try {
//            if (director != null && director.getEmail() != null) {
//                String subject = "💰 Salary Payment Request - " + (employee.getName() != null ? employee.getName() : "Employee");
//                String html = String.format("""
//                    <html>
//                    <body style='font-family: Arial, sans-serif;'>
//                        <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
//                            <h2 style='color: #4CAF50;'>💰 Salary Payment Request</h2>
//                            <p>Dear <strong>%s</strong>,</p>
//                            <p>A salary payment request has been submitted.</p>
//                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                                <p><strong>👤 Employee:</strong> %s</p>
//                                <p><strong>💰 Amount:</strong> ₹%s</p>
//                                <p><strong>📋 Transaction ID:</strong> %s</p>
//                                <p><strong>📅 Date:</strong> %s</p>
//                            </div>
//                            <p>Please login to approve or reject this payment.</p>
//                            <hr>
//                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        director.getName() != null ? director.getName() : "Director",
//                        employee.getName() != null ? employee.getName() : "Employee",
//                        payment.getAmount(),
//                        payment.getTransactionId(),
//                        payment.getDateTime() != null ? payment.getDateTime().format(formatter) : "N/A"
//                );
//                emailService.sendEmail(director.getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
//        }
//    }
//
//    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
//        try {
//            if (payment.getUser() != null && payment.getUser().getEmail() != null) {
//                String subject = status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected";
//                String color = status.equals("APPROVED") ? "#4CAF50" : "#f44336";
//                String icon = status.equals("APPROVED") ? "✅" : "❌";
//
//                String html = String.format("""
//                    <html>
//                    <body style='font-family: Arial, sans-serif;'>
//                        <div style='max-width: 600px; margin: 0 auto; background: %s10; border-radius: 10px; padding: 20px;'>
//                            <h2 style='color: %s;'>%s Payment %s</h2>
//                            <p>Dear <strong>%s</strong>,</p>
//                            <p>Your payment request has been %s.</p>
//                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                                <p><strong>📋 Transaction ID:</strong> %s</p>
//                                <p><strong>💰 Amount:</strong> ₹%s</p>
//                                <p><strong>📝 Director Note:</strong> %s</p>
//                            </div>
//                            <hr>
//                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//                        </div>
//                    </body>
//                    </html>
//                    """,
//                        color, color, icon, status,
//                        payment.getUser().getName() != null ? payment.getUser().getName() : "User",
//                        status.toLowerCase(),
//                        payment.getTransactionId(),
//                        payment.getAmount(),
//                        note != null ? note : "No additional notes"
//                );
//                emailService.sendEmail(payment.getUser().getEmail(), subject, html);
//            }
//        } catch (Exception e) {
//            System.out.println("Email error (non-critical): " + e.getMessage());
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
////import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
////import com.softmax.shardaos.dto.input.CreatePaymentRequest;
////import com.softmax.shardaos.dto.output.*;
////import com.softmax.shardaos.models.*;
////import com.softmax.shardaos.repository.*;
////import com.fasterxml.jackson.databind.ObjectMapper;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import org.springframework.transaction.annotation.Transactional;
////
////import java.time.LocalDateTime;
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
////import java.time.format.DateTimeFormatter;
////import java.util.*;
////import java.util.stream.Collectors;
////
////@Service
////public class PaymentService {
////
////    @Autowired
////    private PaymentDetailsRepository paymentRepository;
////
////    @Autowired
////    private ProjectPaymentRepository projectPaymentRepository;
////
////    @Autowired
////    private SalaryPaymentRepository salaryPaymentRepository;
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
////    private EmployeeDetailsRepository employeeDetailsRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////    private static final Long HEAD_DEPARTMENT_ID = 1L;
////
////    private final ObjectMapper objectMapper = new ObjectMapper();
////
////    // Helper method to print debug response
////    private void printDebugResponse(String methodName, Object response) {
////        try {
////            System.out.println("\n========== DEBUG RESPONSE ==========");
////            System.out.println("Method: " + methodName);
////            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
////            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
////            System.out.println("Response JSON:");
////            System.out.println(jsonResponse);
////            System.out.println("=====================================\n");
////        } catch (Exception e) {
////            System.out.println("Error printing debug response: " + e.getMessage());
////            System.out.println("Response: " + response);
////        }
////    }
////
////    // Helper method to safely parse double from string
////    private double safeParseDouble(String value) {
////        if (value == null || value.trim().isEmpty()) {
////            return 0.0;
////        }
////        try {
////            return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
////        } catch (NumberFormatException e) {
////            System.out.println("Error parsing double: " + value);
////            return 0.0;
////        }
////    }
////
////    // ==================== VALIDATION ====================
////
////    public boolean isHeadDirector(Long userId) {
////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////        if (!isEmp || !isDirector) return false;
////        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
////        return emp != null && emp.getDepartmentRef() != null &&
////                HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
////    }
////
////    private void validateHeadDirector(Long userId) {
////        if (!isHeadDirector(userId)) {
////            RuntimeException error = new RuntimeException("Access denied: Only Head Director can manage payments");
////            printDebugResponse("validateHeadDirector_ERROR", error.getMessage());
////            throw error;
////        }
////    }
////
////    private void validateProjectOwner(Long userId, Long projectId) {
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////        if (project.getUploader() == null || !project.getUploader().getUserId().equals(userId)) {
////            throw new RuntimeException("Access denied: You can only access your own projects");
////        }
////    }
////
////    // ==================== CLIENT APIs (Payment Status View) ====================
////
////    // Client: Get all payments for their specific project
////    public List<PaymentResponseDTO> getMyProjectPayments(Long userId, Long projectId) {
////        try {
////            validateProjectOwner(userId, projectId);
////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
////            List<PaymentResponseDTO> response = payments.stream()
////                    .map(this::convertToDTO)
////                    .collect(Collectors.toList());
////            printDebugResponse("getMyProjectPayments", response);
////            return response;
////        } catch (RuntimeException e) {
////            printDebugResponse("getMyProjectPayments_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // Client: Get payment summary for their project
////    public ProjectPaymentSummaryDTO getMyProjectPaymentSummary(Long userId, Long projectId) {
////        try {
////            validateProjectOwner(userId, projectId);
////
////            ProjectDetails project = projectRepository.findById(projectId)
////                    .orElseThrow(() -> new RuntimeException("Project not found"));
////
////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
////            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
////
////            Double totalProjectAmount = safeParseDouble(project.getCost());
////            double paidAmount = totalApproved != null ? totalApproved : 0.0;
////            double remainingAmount = totalProjectAmount - paidAmount;
////            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////
////            List<PaymentResponseDTO> approvedPayments = payments.stream()
////                    .filter(p -> "APPROVED".equals(p.getStatus()))
////                    .map(this::convertToDTO)
////                    .collect(Collectors.toList());
////
////            ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
////                    .projectId(project.getProjectId())
////                    .projectName(project.getProjectName())
////                    .totalProjectAmount(String.valueOf(totalProjectAmount))
////                    .paidAmount(String.valueOf(paidAmount))
////                    .remainingAmount(String.valueOf(remainingAmount))
////                    .paidPercentage(paidPercentage)
////                    .isFullyPaid(remainingAmount <= 0)
////                    .payments(approvedPayments)
////                    .build();
////
////            printDebugResponse("getMyProjectPaymentSummary", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getMyProjectPaymentSummary_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // Client: Get all their projects with payment summaries
////    public List<ProjectPaymentSummaryDTO> getAllMyProjectsPayments(Long userId) {
////        try {
////            List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
////            List<ProjectPaymentSummaryDTO> response = new ArrayList<>();
////
////            for (ProjectDetails project : myProjects) {
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double totalProjectAmount = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = totalProjectAmount - paidAmount;
////                double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////
////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(paidPercentage)
////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
////                        .payments(new ArrayList<>())
////                        .build();
////
////                response.add(summary);
////            }
////
////            response.sort((a, b) -> a.getProjectName().compareTo(b.getProjectName()));
////            printDebugResponse("getAllMyProjectsPayments", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getAllMyProjectsPayments_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // Client: Get payment status for a specific transaction
////    public PaymentResponseDTO getMyPaymentByTransactionId(Long userId, String transactionId) {
////        try {
////            PaymentDetails payment = paymentRepository.findByTransactionId(transactionId)
////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
////
////            if ("PROJECT_PAYMENT".equals(payment.getType())) {
////                Optional<ProjectPayment> projectPaymentOpt = projectPaymentRepository.findByPayment(payment);
////                if (projectPaymentOpt.isPresent()) {
////                    ProjectPayment projectPayment = projectPaymentOpt.get();
////                    validateProjectOwner(userId, projectPayment.getProject().getProjectId());
////                    PaymentResponseDTO response = convertToDTO(payment);
////                    printDebugResponse("getMyPaymentByTransactionId", response);
////                    return response;
////                } else {
////                    throw new RuntimeException("Project payment mapping not found");
////                }
////            } else {
////                throw new RuntimeException("Invalid payment type for client access");
////            }
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getMyPaymentByTransactionId_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // Client: Get dashboard stats for client (only their projects)
////    public ClientPaymentDashboardDTO getMyPaymentDashboard(Long userId) {
////        try {
////            List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
////
////            double totalProjectCost = 0.0;
////            double totalPaidAmount = 0.0;
////            int totalProjects = myProjects.size();
////            int completedProjects = 0;
////            int pendingProjects = 0;
////
////            List<ProjectPaymentSummaryDTO> projectSummaries = new ArrayList<>();
////
////            for (ProjectDetails project : myProjects) {
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double projectCost = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = projectCost - paidAmount;
////
////                totalProjectCost += projectCost;
////                totalPaidAmount += paidAmount;
////
////                if (remainingAmount <= 0 && projectCost > 0) {
////                    completedProjects++;
////                } else if (projectCost > 0) {
////                    pendingProjects++;
////                }
////
////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .totalProjectAmount(String.valueOf(projectCost))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(projectCost > 0 ? (paidAmount / projectCost) * 100 : 0)
////                        .isFullyPaid(remainingAmount <= 0 && projectCost > 0)
////                        .build();
////
////                projectSummaries.add(summary);
////            }
////
////            double totalRemaining = totalProjectCost - totalPaidAmount;
////            double overallPaidPercentage = totalProjectCost > 0 ? (totalPaidAmount / totalProjectCost) * 100 : 0;
////
////            ClientPaymentDashboardDTO response = ClientPaymentDashboardDTO.builder()
////                    .totalProjects(totalProjects)
////                    .totalProjectCost(String.valueOf(totalProjectCost))
////                    .totalPaidAmount(String.valueOf(totalPaidAmount))
////                    .totalRemainingAmount(String.valueOf(totalRemaining))
////                    .overallPaidPercentage(overallPaidPercentage)
////                    .completedProjects(completedProjects)
////                    .pendingProjects(pendingProjects)
////                    .projects(projectSummaries)
////                    .build();
////
////            printDebugResponse("getMyPaymentDashboard", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getMyPaymentDashboard_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // ==================== CREATE PAYMENT ====================
////
////    @Transactional
////    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
////        try {
////            User user = userRepository.findById(userId)
////                    .orElseThrow(() -> new RuntimeException("User not found"));
////
////            if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
////                throw new RuntimeException("Transaction ID is required");
////            }
////            if (request.getAmount() == null || request.getAmount().isEmpty()) {
////                throw new RuntimeException("Amount is required");
////            }
////            if (request.getProjectId() == null) {
////                throw new RuntimeException("Project ID is required");
////            }
////
////            if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
////                throw new RuntimeException("Transaction ID already exists");
////            }
////
////            ProjectDetails project = projectRepository.findById(request.getProjectId())
////                    .orElseThrow(() -> new RuntimeException("Project not found"));
////
////            PaymentDetails payment = new PaymentDetails();
////            payment.setTransactionId(request.getTransactionId());
////            payment.setAmount(request.getAmount());
////            payment.setType("PROJECT_PAYMENT");
////            payment.setStatus("PENDING");
////            payment.setUser(user);
////            payment.setPaymentNote(request.getPaymentNote());
////            payment.setDateTime(LocalDateTime.now());
////
////            PaymentDetails savedPayment = paymentRepository.save(payment);
////
////            ProjectPayment projectPayment = new ProjectPayment();
////            projectPayment.setProject(project);
////            projectPayment.setPayment(savedPayment);
////            projectPaymentRepository.save(projectPayment);
////
////            sendPaymentNotificationEmail(savedPayment, user);
////
////            PaymentResponseDTO response = convertToDTO(savedPayment);
////            printDebugResponse("createProjectPayment", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("createProjectPayment_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    @Transactional
////    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
////        try {
////            validateHeadDirector(userId);
////
////            if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
////                throw new RuntimeException("Transaction ID is required");
////            }
////            if (request.getAmount() == null || request.getAmount().isEmpty()) {
////                throw new RuntimeException("Amount is required");
////            }
////            if (request.getUserId() == null) {
////                throw new RuntimeException("Employee User ID is required");
////            }
////
////            if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
////                throw new RuntimeException("Transaction ID already exists");
////            }
////
////            User employee = userRepository.findById(request.getUserId())
////                    .orElseThrow(() -> new RuntimeException("Employee not found"));
////
////            PaymentDetails payment = new PaymentDetails();
////            payment.setTransactionId(request.getTransactionId());
////            payment.setAmount(request.getAmount());
////            payment.setType("SALARY");
////            payment.setStatus("PENDING");
////            payment.setUser(employee);
////            payment.setPaymentNote(request.getPaymentNote());
////            payment.setDateTime(LocalDateTime.now());
////
////            PaymentDetails savedPayment = paymentRepository.save(payment);
////
////            SalaryPayment salaryPayment = new SalaryPayment();
////            salaryPayment.setUser(employee);
////            salaryPayment.setPayment(savedPayment);
////            salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
////                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
////            salaryPayment.setSalaryAmount(request.getAmount());
////            salaryPaymentRepository.save(salaryPayment);
////
////            User director = userRepository.findById(userId).get();
////            sendSalaryPaymentNotificationEmail(savedPayment, employee, director);
////
////            PaymentResponseDTO response = convertToDTO(savedPayment);
////            printDebugResponse("createSalaryPayment", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("createSalaryPayment_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // ==================== HEAD DIRECTOR APIs ====================
////
////    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////            List<PaymentResponseDTO> response = paymentRepository.findByStatus("PENDING").stream()
////                    .map(this::convertToDTO)
////                    .collect(Collectors.toList());
////            printDebugResponse("getPendingPayments", response);
////            return response;
////        } catch (RuntimeException e) {
////            printDebugResponse("getPendingPayments_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////            List<PaymentResponseDTO> response = paymentRepository.findAll().stream()
////                    .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
////                    .map(this::convertToDTO)
////                    .collect(Collectors.toList());
////            printDebugResponse("getAllPayments", response);
////            return response;
////        } catch (RuntimeException e) {
////            printDebugResponse("getAllPayments_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
////        try {
////            validateHeadDirector(headDirectorId);
////            PaymentDetails payment = paymentRepository.findById(paymentId)
////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
////            PaymentResponseDTO response = convertToDTO(payment);
////            printDebugResponse("getPaymentById", response);
////            return response;
////        } catch (RuntimeException e) {
////            printDebugResponse("getPaymentById_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    @Transactional
////    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            User director = userRepository.findById(headDirectorId).get();
////            PaymentDetails payment = paymentRepository.findById(paymentId)
////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
////
////            if (!"PENDING".equals(payment.getStatus())) {
////                throw new RuntimeException("Payment is already " + payment.getStatus());
////            }
////
////            payment.setStatus(request.getStatus());
////            payment.setApprovedBy(director);
////            payment.setApprovedAt(LocalDateTime.now());
////            payment.setDirectorNote(request.getDirectorNote());
////
////            PaymentDetails updated = paymentRepository.save(payment);
////
////            sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());
////
////            PaymentResponseDTO response = convertToDTO(updated);
////            printDebugResponse("approvePayment", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("approvePayment_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            ProjectDetails project = projectRepository.findById(projectId)
////                    .orElseThrow(() -> new RuntimeException("Project not found"));
////
////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
////            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
////
////            Double totalProjectAmount = safeParseDouble(project.getCost());
////            double paidAmount = totalApproved != null ? totalApproved : 0.0;
////            double remainingAmount = totalProjectAmount - paidAmount;
////            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////
////            ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
////                    .projectId(project.getProjectId())
////                    .projectName(project.getProjectName())
////                    .totalProjectAmount(String.valueOf(totalProjectAmount))
////                    .paidAmount(String.valueOf(paidAmount))
////                    .remainingAmount(String.valueOf(remainingAmount))
////                    .paidPercentage(paidPercentage)
////                    .isFullyPaid(remainingAmount <= 0)
////                    .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
////                    .build();
////
////            printDebugResponse("getProjectPayments", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getProjectPayments_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
////            List<PaymentResponseDTO> response = salaryPayments.stream()
////                    .map(sp -> convertToDTO(sp.getPayment()))
////                    .collect(Collectors.toList());
////
////            printDebugResponse("getSalaryPaymentsByUser", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getSalaryPaymentsByUser_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<PaymentDetails> allPayments = paymentRepository.findAll();
////            List<PaymentDetails> approvedPayments = allPayments.stream()
////                    .filter(p -> "APPROVED".equals(p.getStatus()))
////                    .collect(Collectors.toList());
////
////            double totalRevenue = 0.0;
////            double totalExpenses = 0.0;
////
////            for (PaymentDetails p : approvedPayments) {
////                double amount = safeParseDouble(p.getAmount());
////                if ("PROJECT_PAYMENT".equals(p.getType())) {
////                    totalRevenue += amount;
////                } else if ("SALARY".equals(p.getType())) {
////                    totalExpenses += amount;
////                }
////            }
////
////            long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
////
////            PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
////                    .totalRevenue(totalRevenue)
////                    .totalExpenses(totalExpenses)
////                    .netProfit(totalRevenue - totalExpenses)
////                    .pendingApprovals((int) pendingApprovals)
////                    .totalTransactions(allPayments.size())
////                    .approvedTransactions(approvedPayments.size())
////                    .build();
////
////            List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);
////
////            List<PaymentResponseDTO> recentTransactions = allPayments.stream()
////                    .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
////                    .limit(10)
////                    .map(this::convertToDTO)
////                    .collect(Collectors.toList());
////
////            List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();
////
////            Map<String, Double> revenueByType = new HashMap<>();
////            revenueByType.put("PROJECT_PAYMENT", totalRevenue);
////            revenueByType.put("SALARY", totalExpenses);
////
////            DashboardPaymentStatsDTO response = DashboardPaymentStatsDTO.builder()
////                    .summary(summary)
////                    .monthlyRevenue(monthlyRevenue)
////                    .recentTransactions(recentTransactions)
////                    .topProjects(topProjects)
////                    .revenueByType(revenueByType)
////                    .build();
////
////            printDebugResponse("getDashboardStats", response);
////            return response;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getDashboardStats_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // ==================== NEW HEAD DIRECTOR APIs - Project Wise Payment Summary ====================
////
////    /**
////     * Get all projects with complete payment summaries for Head Director
////     * Shows project-wise remaining amounts, department info, client info
////     */
////    public List<ProjectPaymentSummaryDTO> getAllProjectsWithPaymentSummary(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<ProjectDetails> allProjects = projectRepository.findAll();
////            List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
////
////            for (ProjectDetails project : allProjects) {
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double totalProjectAmount = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = totalProjectAmount - paidAmount;
////                double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////
////                // Get all payments for this project
////                List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
////                List<PaymentResponseDTO> paymentDTOs = projectPayments.stream()
////                        .map(this::convertToDTO)
////                        .collect(Collectors.toList());
////
////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .projectDescription(project.getProjectDescription())
////                        .projectType(project.getProjectType())
////                        .status(project.getStatus())
////                        .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
////                        .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
////                        .clientId(project.getUploader() != null ? project.getUploader().getUserId() : null)
////                        .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
////                        .clientEmail(project.getUploader() != null ? project.getUploader().getEmail() : "N/A")
////                        .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
////                        .deadline(project.getDeadline() != null ? project.getDeadline().toString() : null)
////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(paidPercentage)
////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
////                        .payments(paymentDTOs)
////                        .build();
////
////                result.add(summary);
////            }
////
////            // Sort by remaining amount (highest first) to show pending payments prominently
////            result.sort((a, b) -> {
////                double aRemaining = safeParseDouble(a.getRemainingAmount());
////                double bRemaining = safeParseDouble(b.getRemainingAmount());
////                return Double.compare(bRemaining, aRemaining);
////            });
////
////            printDebugResponse("getAllProjectsWithPaymentSummary", result);
////            return result;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getAllProjectsWithPaymentSummary_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    /**
////     * Get projects filtered by department with payment summaries for Head Director
////     */
////    public List<ProjectPaymentSummaryDTO> getProjectsByDepartmentWithPaymentSummary(Long headDirectorId, Long departmentId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<ProjectDetails> departmentProjects = projectRepository.findByDepartmentDepartmentId(departmentId);
////            List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
////
////            for (ProjectDetails project : departmentProjects) {
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double totalProjectAmount = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = totalProjectAmount - paidAmount;
////                double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////
////                List<PaymentDetails> projectPayments = paymentRepository.findPaymentsByProjectId(project.getProjectId());
////                List<PaymentResponseDTO> paymentDTOs = projectPayments.stream()
////                        .map(this::convertToDTO)
////                        .collect(Collectors.toList());
////
////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .projectDescription(project.getProjectDescription())
////                        .projectType(project.getProjectType())
////                        .status(project.getStatus())
////                        .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
////                        .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
////                        .clientId(project.getUploader() != null ? project.getUploader().getUserId() : null)
////                        .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
////                        .clientEmail(project.getUploader() != null ? project.getUploader().getEmail() : "N/A")
////                        .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
////                        .deadline(project.getDeadline() != null ? project.getDeadline().toString() : null)
////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(paidPercentage)
////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
////                        .payments(paymentDTOs)
////                        .build();
////
////                result.add(summary);
////            }
////
////            result.sort((a, b) -> {
////                double aRemaining = safeParseDouble(a.getRemainingAmount());
////                double bRemaining = safeParseDouble(b.getRemainingAmount());
////                return Double.compare(bRemaining, aRemaining);
////            });
////
////            printDebugResponse("getProjectsByDepartmentWithPaymentSummary", result);
////            return result;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getProjectsByDepartmentWithPaymentSummary_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    /**
////     * Get payment approval counts for Head Director dashboard
////     */
////    public PaymentApprovalCountDTO getPaymentApprovalCount(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            long pendingCount = paymentRepository.countByStatus("PENDING");
////            long approvedCount = paymentRepository.countByStatus("APPROVED");
////            long rejectedCount = paymentRepository.countByStatus("REJECTED");
////
////            PaymentApprovalCountDTO result = PaymentApprovalCountDTO.builder()
////                    .pendingCount(pendingCount)
////                    .approvedCount(approvedCount)
////                    .rejectedCount(rejectedCount)
////                    .build();
////
////            printDebugResponse("getPaymentApprovalCount", result);
////            return result;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getPaymentApprovalCount_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    /**
////     * Get department-wise payment summary for Head Director
////     */
////    public List<DepartmentPaymentSummaryDTO> getDepartmentWisePaymentSummary(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<ProjectDetails> allProjects = projectRepository.findAll();
////            Map<Long, DepartmentPaymentSummaryDTO> departmentMap = new HashMap<>();
////
////            for (ProjectDetails project : allProjects) {
////                Long deptId = project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null;
////                String deptName = project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "Unknown";
////
////                if (deptId == null) {
////                    deptId = -1L;
////                    deptName = "Unassigned";
////                }
////
////                DepartmentPaymentSummaryDTO deptSummary = departmentMap.get(deptId);
////                if (deptSummary == null) {
////                    deptSummary = DepartmentPaymentSummaryDTO.builder()
////                            .departmentId(deptId)
////                            .departmentName(deptName)
////                            .totalProjects(0)
////                            .totalBudget(0.0)
////                            .totalPaid(0.0)
////                            .totalRemaining(0.0)
////                            .projects(new ArrayList<>())
////                            .build();
////                    departmentMap.put(deptId, deptSummary);
////                }
////
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double totalProjectAmount = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = totalProjectAmount - paidAmount;
////
////                deptSummary.setTotalProjects(deptSummary.getTotalProjects() + 1);
////                deptSummary.setTotalBudget(deptSummary.getTotalBudget() + totalProjectAmount);
////                deptSummary.setTotalPaid(deptSummary.getTotalPaid() + paidAmount);
////                deptSummary.setTotalRemaining(deptSummary.getTotalRemaining() + remainingAmount);
////
////                // Add project to department summary
////                ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .clientName(project.getUploader() != null ? project.getUploader().getName() : "N/A")
////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
////                        .build();
////
////                deptSummary.getProjects().add(projectSummary);
////            }
////
////            List<DepartmentPaymentSummaryDTO> result = new ArrayList<>(departmentMap.values());
////            result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
////
////            printDebugResponse("getDepartmentWisePaymentSummary", result);
////            return result;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getDepartmentWisePaymentSummary_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    /**
////     * Get client-wise payment summary for Head Director
////     */
////    public List<ClientPaymentSummaryDTO> getClientWisePaymentSummary(Long headDirectorId) {
////        try {
////            validateHeadDirector(headDirectorId);
////
////            List<ProjectDetails> allProjects = projectRepository.findAll();
////            Map<Long, ClientPaymentSummaryDTO> clientMap = new HashMap<>();
////
////            for (ProjectDetails project : allProjects) {
////                Long clientId = project.getUploader() != null ? project.getUploader().getUserId() : null;
////                String clientName = project.getUploader() != null ? project.getUploader().getName() : "Unknown";
////                String clientEmail = project.getUploader() != null ? project.getUploader().getEmail() : "Unknown";
////
////                if (clientId == null) {
////                    clientId = -1L;
////                    clientName = "Unknown Client";
////                    clientEmail = "unknown@example.com";
////                }
////
////                ClientPaymentSummaryDTO clientSummary = clientMap.get(clientId);
////                if (clientSummary == null) {
////                    clientSummary = ClientPaymentSummaryDTO.builder()
////                            .clientId(clientId)
////                            .clientName(clientName)
////                            .clientEmail(clientEmail)
////                            .totalProjects(0)
////                            .totalBudget(0.0)
////                            .totalPaid(0.0)
////                            .totalRemaining(0.0)
////                            .projects(new ArrayList<>())
////                            .build();
////                    clientMap.put(clientId, clientSummary);
////                }
////
////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////                Double totalProjectAmount = safeParseDouble(project.getCost());
////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
////                double remainingAmount = totalProjectAmount - paidAmount;
////
////                clientSummary.setTotalProjects(clientSummary.getTotalProjects() + 1);
////                clientSummary.setTotalBudget(clientSummary.getTotalBudget() + totalProjectAmount);
////                clientSummary.setTotalPaid(clientSummary.getTotalPaid() + paidAmount);
////                clientSummary.setTotalRemaining(clientSummary.getTotalRemaining() + remainingAmount);
////
////                // Add project to client summary
////                ProjectPaymentSummaryDTO projectSummary = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : "N/A")
////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
////                        .paidAmount(String.valueOf(paidAmount))
////                        .remainingAmount(String.valueOf(remainingAmount))
////                        .paidPercentage(totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0)
////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
////                        .build();
////
////                clientSummary.getProjects().add(projectSummary);
////            }
////
////            List<ClientPaymentSummaryDTO> result = new ArrayList<>(clientMap.values());
////            result.sort((a, b) -> Double.compare(b.getTotalRemaining(), a.getTotalRemaining()));
////
////            printDebugResponse("getClientWisePaymentSummary", result);
////            return result;
////
////        } catch (RuntimeException e) {
////            printDebugResponse("getClientWisePaymentSummary_ERROR", e.getMessage());
////            throw e;
////        }
////    }
////
////    // ==================== HELPER METHODS ====================
////
////    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
////        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
////        LocalDateTime now = LocalDateTime.now();
////
////        for (int i = 5; i >= 0; i--) {
////            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
////            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
////            dto.setMonth(month);
////            dto.setTotalRevenue(0.0);
////            dto.setTotalExpenses(0.0);
////            dto.setNetProfit(0.0);
////            dto.setProjectPaymentsCount(0);
////            dto.setSalaryPaymentsCount(0);
////            monthlyMap.put(month, dto);
////        }
////
////        for (PaymentDetails p : payments) {
////            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
////            MonthlyRevenueDTO dto = monthlyMap.get(month);
////            if (dto != null) {
////                double amount = safeParseDouble(p.getAmount());
////                if ("PROJECT_PAYMENT".equals(p.getType())) {
////                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
////                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
////                } else {
////                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
////                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
////                }
////                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
////                monthlyMap.put(month, dto);
////            }
////        }
////
////        return new ArrayList<>(monthlyMap.values());
////    }
////
////    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
////        List<ProjectDetails> projects = projectRepository.findAll();
////        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
////
////        for (ProjectDetails project : projects) {
////            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////            if (totalPaid != null && totalPaid > 0) {
////                Double totalAmount = safeParseDouble(project.getCost());
////                ProjectPaymentSummaryDTO dto = ProjectPaymentSummaryDTO.builder()
////                        .projectId(project.getProjectId())
////                        .projectName(project.getProjectName())
////                        .totalProjectAmount(String.valueOf(totalAmount))
////                        .paidAmount(String.valueOf(totalPaid))
////                        .remainingAmount(String.valueOf(totalAmount - totalPaid))
////                        .paidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0)
////                        .isFullyPaid(totalPaid >= totalAmount)
////                        .build();
////                result.add(dto);
////            }
////        }
////
////        result.sort((a,b) -> {
////            double aPaid = safeParseDouble(a.getPaidAmount());
////            double bPaid = safeParseDouble(b.getPaidAmount());
////            return Double.compare(bPaid, aPaid);
////        });
////
////        return result.stream().limit(5).collect(Collectors.toList());
////    }
////
////    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
////        PaymentResponseDTO dto = new PaymentResponseDTO();
////        dto.setPaymentId(payment.getPaymentId());
////        dto.setTransactionId(payment.getTransactionId());
////        dto.setAmount(payment.getAmount());
////        dto.setType(payment.getType());
////        dto.setStatus(payment.getStatus());
////        dto.setUserName(payment.getUser().getName());
////        dto.setUserEmail(payment.getUser().getEmail());
////        dto.setPaymentNote(payment.getPaymentNote());
////        dto.setDirectorNote(payment.getDirectorNote());
////        dto.setApprovedByName(payment.getApprovedBy() != null ? payment.getApprovedBy().getName() : null);
////        dto.setApprovedAt(payment.getApprovedAt());
////        dto.setDateTime(payment.getDateTime());
////        return dto;
////    }
////
////    // ==================== EMAIL METHODS ====================
////
////    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
////        try {
////            List<UserType> userTypes = userTypeRepository.findAll();
////            User headDirector = userTypes.stream()
////                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
////                    .map(UserType::getUser)
////                    .findFirst()
////                    .orElse(null);
////
////            if (headDirector != null) {
////                String subject = "💰 New Payment Request - " + payment.getType();
////                String html = String.format("""
////                    <html>
////                    <body style='font-family: Arial, sans-serif;'>
////                        <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
////                            <h2 style='color: #4CAF50;'>💰 New Payment Request</h2>
////                            <p>Dear <strong>%s</strong>,</p>
////                            <p>A new payment request has been submitted for your approval.</p>
////                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                                <p><strong>📋 Transaction ID:</strong> %s</p>
////                                <p><strong>💰 Amount:</strong> ₹%s</p>
////                                <p><strong>📝 Type:</strong> %s</p>
////                                <p><strong>👤 Submitted By:</strong> %s</p>
////                                <p><strong>📅 Date:</strong> %s</p>
////                                <p><strong>📝 Note:</strong> %s</p>
////                            </div>
////                            <p>Please login to approve or reject this payment.</p>
////                            <hr>
////                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////                        </div>
////                    </body>
////                    </html>
////                    """,
////                        headDirector.getName(),
////                        payment.getTransactionId(),
////                        payment.getAmount(),
////                        payment.getType(),
////                        user.getName(),
////                        payment.getDateTime().format(formatter),
////                        payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note"
////                );
////                emailService.sendEmail(headDirector.getEmail(), subject, html);
////            }
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
////        try {
////            String subject = "💰 Salary Payment Request - " + employee.getName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #4CAF50;'>💰 Salary Payment Request</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>A salary payment request has been submitted.</p>
////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>👤 Employee:</strong> %s</p>
////                            <p><strong>💰 Amount:</strong> ₹%s</p>
////                            <p><strong>📋 Transaction ID:</strong> %s</p>
////                            <p><strong>📅 Date:</strong> %s</p>
////                        </div>
////                        <p>Please login to approve or reject this payment.</p>
////                        <hr>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    director.getName(),
////                    employee.getName(),
////                    payment.getAmount(),
////                    payment.getTransactionId(),
////                    payment.getDateTime().format(formatter)
////            );
////            emailService.sendEmail(director.getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
////        try {
////            String subject = status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected";
////            String color = status.equals("APPROVED") ? "#4CAF50" : "#f44336";
////            String icon = status.equals("APPROVED") ? "✅" : "❌";
////
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: %s10; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: %s;'>%s Payment %s</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Your payment request has been %s.</p>
////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📋 Transaction ID:</strong> %s</p>
////                            <p><strong>💰 Amount:</strong> ₹%s</p>
////                            <p><strong>📝 Director Note:</strong> %s</p>
////                        </div>
////                        <hr>
////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    color, color, icon, status,
////                    payment.getUser().getName(),
////                    status.toLowerCase(),
////                    payment.getTransactionId(),
////                    payment.getAmount(),
////                    note != null ? note : "No additional notes"
////            );
////            emailService.sendEmail(payment.getUser().getEmail(), subject, html);
////        } catch (Exception e) {
////            System.out.println("Email error: " + e.getMessage());
////        }
////    }
////}
////
////
////
////
////
////
////
////
////
////
////
////
////
////
////
////
////
//////package com.softmax.shardaos.service;
//////
//////import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
//////import com.softmax.shardaos.dto.input.CreatePaymentRequest;
//////import com.softmax.shardaos.dto.output.*;
//////import com.softmax.shardaos.models.*;
//////import com.softmax.shardaos.repository.*;
//////import com.fasterxml.jackson.databind.ObjectMapper;
//////import org.springframework.beans.factory.annotation.Autowired;
//////import org.springframework.stereotype.Service;
//////import org.springframework.transaction.annotation.Transactional;
//////
//////import java.time.LocalDateTime;
//////import java.time.format.DateTimeFormatter;
//////import java.util.*;
//////import java.util.stream.Collectors;
//////
//////@Service
//////public class PaymentService {
//////
//////    @Autowired
//////    private PaymentDetailsRepository paymentRepository;
//////
//////    @Autowired
//////    private ProjectPaymentRepository projectPaymentRepository;
//////
//////    @Autowired
//////    private SalaryPaymentRepository salaryPaymentRepository;
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
//////    private EmployeeDetailsRepository employeeDetailsRepository;
//////
//////    @Autowired
//////    private EmailService emailService;
//////
//////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//////    private static final Long HEAD_DEPARTMENT_ID = 1L;
//////
//////    private final ObjectMapper objectMapper = new ObjectMapper();
//////
//////    // Helper method to print debug response
//////    private void printDebugResponse(String methodName, Object response) {
//////        try {
//////            System.out.println("\n========== DEBUG RESPONSE ==========");
//////            System.out.println("Method: " + methodName);
//////            System.out.println("Timestamp: " + LocalDateTime.now().format(formatter));
//////            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//////            System.out.println("Response JSON:");
//////            System.out.println(jsonResponse);
//////            System.out.println("=====================================\n");
//////        } catch (Exception e) {
//////            System.out.println("Error printing debug response: " + e.getMessage());
//////            System.out.println("Response: " + response);
//////        }
//////    }
//////
//////    // Helper method to safely parse double from string
//////    private double safeParseDouble(String value) {
//////        if (value == null || value.trim().isEmpty()) {
//////            return 0.0;
//////        }
//////        try {
//////            return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
//////        } catch (NumberFormatException e) {
//////            System.out.println("Error parsing double: " + value);
//////            return 0.0;
//////        }
//////    }
//////
//////    // ==================== VALIDATION ====================
//////
//////    public boolean isHeadDirector(Long userId) {
//////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
//////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
//////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
//////        if (!isEmp || !isDirector) return false;
//////        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
//////        return emp != null && emp.getDepartmentRef() != null &&
//////                HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
//////    }
//////
//////    private void validateHeadDirector(Long userId) {
//////        if (!isHeadDirector(userId)) {
//////            RuntimeException error = new RuntimeException("Access denied: Only Head Director can manage payments");
//////            printDebugResponse("validateHeadDirector_ERROR", error.getMessage());
//////            throw error;
//////        }
//////    }
//////
//////    private void validateProjectOwner(Long userId, Long projectId) {
//////        ProjectDetails project = projectRepository.findById(projectId)
//////                .orElseThrow(() -> new RuntimeException("Project not found"));
//////        if (project.getUploader() == null || !project.getUploader().getUserId().equals(userId)) {
//////            throw new RuntimeException("Access denied: You can only access your own projects");
//////        }
//////    }
//////
//////    // ==================== CLIENT APIs (Payment Status View) ====================
//////
//////    // Client: Get all payments for their specific project
//////    public List<PaymentResponseDTO> getMyProjectPayments(Long userId, Long projectId) {
//////        try {
//////            validateProjectOwner(userId, projectId);
//////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//////            List<PaymentResponseDTO> response = payments.stream()
//////                    .map(this::convertToDTO)
//////                    .collect(Collectors.toList());
//////            printDebugResponse("getMyProjectPayments", response);
//////            return response;
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getMyProjectPayments_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // Client: Get payment summary for their project
//////    public ProjectPaymentSummaryDTO getMyProjectPaymentSummary(Long userId, Long projectId) {
//////        try {
//////            validateProjectOwner(userId, projectId);
//////
//////            ProjectDetails project = projectRepository.findById(projectId)
//////                    .orElseThrow(() -> new RuntimeException("Project not found"));
//////
//////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//////            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//////
//////            Double totalProjectAmount = safeParseDouble(project.getCost());
//////            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//////            double remainingAmount = totalProjectAmount - paidAmount;
//////            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//////
//////            List<PaymentResponseDTO> approvedPayments = payments.stream()
//////                    .filter(p -> "APPROVED".equals(p.getStatus()))
//////                    .map(this::convertToDTO)
//////                    .collect(Collectors.toList());
//////
//////            ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//////                    .projectId(project.getProjectId())
//////                    .projectName(project.getProjectName())
//////                    .totalProjectAmount(String.valueOf(totalProjectAmount))
//////                    .paidAmount(String.valueOf(paidAmount))
//////                    .remainingAmount(String.valueOf(remainingAmount))
//////                    .paidPercentage(paidPercentage)
//////                    .isFullyPaid(remainingAmount <= 0)
//////                    .payments(approvedPayments)
//////                    .build();
//////
//////            printDebugResponse("getMyProjectPaymentSummary", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getMyProjectPaymentSummary_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // Client: Get all their projects with payment summaries
//////    public List<ProjectPaymentSummaryDTO> getAllMyProjectsPayments(Long userId) {
//////        try {
//////            List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//////            List<ProjectPaymentSummaryDTO> response = new ArrayList<>();
//////
//////            for (ProjectDetails project : myProjects) {
//////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//////                Double totalProjectAmount = safeParseDouble(project.getCost());
//////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
//////                double remainingAmount = totalProjectAmount - paidAmount;
//////                double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//////
//////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//////                        .projectId(project.getProjectId())
//////                        .projectName(project.getProjectName())
//////                        .totalProjectAmount(String.valueOf(totalProjectAmount))
//////                        .paidAmount(String.valueOf(paidAmount))
//////                        .remainingAmount(String.valueOf(remainingAmount))
//////                        .paidPercentage(paidPercentage)
//////                        .isFullyPaid(remainingAmount <= 0 && totalProjectAmount > 0)
//////                        .payments(new ArrayList<>())
//////                        .build();
//////
//////                response.add(summary);
//////            }
//////
//////            response.sort((a, b) -> a.getProjectName().compareTo(b.getProjectName()));
//////            printDebugResponse("getAllMyProjectsPayments", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getAllMyProjectsPayments_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // Client: Get payment status for a specific transaction
//////    public PaymentResponseDTO getMyPaymentByTransactionId(Long userId, String transactionId) {
//////        try {
//////            PaymentDetails payment = paymentRepository.findByTransactionId(transactionId)
//////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
//////
//////            if ("PROJECT_PAYMENT".equals(payment.getType())) {
//////                Optional<ProjectPayment> projectPaymentOpt = projectPaymentRepository.findByPayment(payment);
//////                if (projectPaymentOpt.isPresent()) {
//////                    ProjectPayment projectPayment = projectPaymentOpt.get();
//////                    validateProjectOwner(userId, projectPayment.getProject().getProjectId());
//////                    PaymentResponseDTO response = convertToDTO(payment);
//////                    printDebugResponse("getMyPaymentByTransactionId", response);
//////                    return response;
//////                } else {
//////                    throw new RuntimeException("Project payment mapping not found");
//////                }
//////            } else {
//////                throw new RuntimeException("Invalid payment type for client access");
//////            }
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getMyPaymentByTransactionId_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // Client: Get dashboard stats for client (only their projects)
//////    public ClientPaymentDashboardDTO getMyPaymentDashboard(Long userId) {
//////        try {
//////            List<ProjectDetails> myProjects = projectRepository.findByUploaderUserId(userId);
//////
//////            double totalProjectCost = 0.0;
//////            double totalPaidAmount = 0.0;
//////            int totalProjects = myProjects.size();
//////            int completedProjects = 0;
//////            int pendingProjects = 0;
//////
//////            List<ProjectPaymentSummaryDTO> projectSummaries = new ArrayList<>();
//////
//////            for (ProjectDetails project : myProjects) {
//////                Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//////                Double projectCost = safeParseDouble(project.getCost());
//////                double paidAmount = totalApproved != null ? totalApproved : 0.0;
//////                double remainingAmount = projectCost - paidAmount;
//////
//////                totalProjectCost += projectCost;
//////                totalPaidAmount += paidAmount;
//////
//////                if (remainingAmount <= 0 && projectCost > 0) {
//////                    completedProjects++;
//////                } else if (projectCost > 0) {
//////                    pendingProjects++;
//////                }
//////
//////                ProjectPaymentSummaryDTO summary = ProjectPaymentSummaryDTO.builder()
//////                        .projectId(project.getProjectId())
//////                        .projectName(project.getProjectName())
//////                        .totalProjectAmount(String.valueOf(projectCost))
//////                        .paidAmount(String.valueOf(paidAmount))
//////                        .remainingAmount(String.valueOf(remainingAmount))
//////                        .paidPercentage(projectCost > 0 ? (paidAmount / projectCost) * 100 : 0)
//////                        .isFullyPaid(remainingAmount <= 0 && projectCost > 0)
//////                        .build();
//////
//////                projectSummaries.add(summary);
//////            }
//////
//////            double totalRemaining = totalProjectCost - totalPaidAmount;
//////            double overallPaidPercentage = totalProjectCost > 0 ? (totalPaidAmount / totalProjectCost) * 100 : 0;
//////
//////            ClientPaymentDashboardDTO response = ClientPaymentDashboardDTO.builder()
//////                    .totalProjects(totalProjects)
//////                    .totalProjectCost(String.valueOf(totalProjectCost))
//////                    .totalPaidAmount(String.valueOf(totalPaidAmount))
//////                    .totalRemainingAmount(String.valueOf(totalRemaining))
//////                    .overallPaidPercentage(overallPaidPercentage)
//////                    .completedProjects(completedProjects)
//////                    .pendingProjects(pendingProjects)
//////                    .projects(projectSummaries)
//////                    .build();
//////
//////            printDebugResponse("getMyPaymentDashboard", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getMyPaymentDashboard_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // ==================== CREATE PAYMENT ====================
//////
//////    @Transactional
//////    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
//////        try {
//////            User user = userRepository.findById(userId)
//////                    .orElseThrow(() -> new RuntimeException("User not found"));
//////
//////            if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
//////                throw new RuntimeException("Transaction ID is required");
//////            }
//////            if (request.getAmount() == null || request.getAmount().isEmpty()) {
//////                throw new RuntimeException("Amount is required");
//////            }
//////            if (request.getProjectId() == null) {
//////                throw new RuntimeException("Project ID is required");
//////            }
//////
//////            if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//////                throw new RuntimeException("Transaction ID already exists");
//////            }
//////
//////            ProjectDetails project = projectRepository.findById(request.getProjectId())
//////                    .orElseThrow(() -> new RuntimeException("Project not found"));
//////
//////            PaymentDetails payment = new PaymentDetails();
//////            payment.setTransactionId(request.getTransactionId());
//////            payment.setAmount(request.getAmount());
//////            payment.setType("PROJECT_PAYMENT");
//////            payment.setStatus("PENDING");
//////            payment.setUser(user);
//////            payment.setPaymentNote(request.getPaymentNote());
//////            payment.setDateTime(LocalDateTime.now());
//////
//////            PaymentDetails savedPayment = paymentRepository.save(payment);
//////
//////            ProjectPayment projectPayment = new ProjectPayment();
//////            projectPayment.setProject(project);
//////            projectPayment.setPayment(savedPayment);
//////            projectPaymentRepository.save(projectPayment);
//////
//////            sendPaymentNotificationEmail(savedPayment, user);
//////
//////            PaymentResponseDTO response = convertToDTO(savedPayment);
//////            printDebugResponse("createProjectPayment", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("createProjectPayment_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    @Transactional
//////    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
//////        try {
//////            validateHeadDirector(userId);
//////
//////            if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
//////                throw new RuntimeException("Transaction ID is required");
//////            }
//////            if (request.getAmount() == null || request.getAmount().isEmpty()) {
//////                throw new RuntimeException("Amount is required");
//////            }
//////            if (request.getUserId() == null) {
//////                throw new RuntimeException("Employee User ID is required");
//////            }
//////
//////            if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
//////                throw new RuntimeException("Transaction ID already exists");
//////            }
//////
//////            User employee = userRepository.findById(request.getUserId())
//////                    .orElseThrow(() -> new RuntimeException("Employee not found"));
//////
//////            PaymentDetails payment = new PaymentDetails();
//////            payment.setTransactionId(request.getTransactionId());
//////            payment.setAmount(request.getAmount());
//////            payment.setType("SALARY");
//////            payment.setStatus("PENDING");
//////            payment.setUser(employee);
//////            payment.setPaymentNote(request.getPaymentNote());
//////            payment.setDateTime(LocalDateTime.now());
//////
//////            PaymentDetails savedPayment = paymentRepository.save(payment);
//////
//////            SalaryPayment salaryPayment = new SalaryPayment();
//////            salaryPayment.setUser(employee);
//////            salaryPayment.setPayment(savedPayment);
//////            salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
//////                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
//////            salaryPayment.setSalaryAmount(request.getAmount());
//////            salaryPaymentRepository.save(salaryPayment);
//////
//////            User director = userRepository.findById(userId).get();
//////            sendSalaryPaymentNotificationEmail(savedPayment, employee, director);
//////
//////            PaymentResponseDTO response = convertToDTO(savedPayment);
//////            printDebugResponse("createSalaryPayment", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("createSalaryPayment_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // ==================== HEAD DIRECTOR APIs ====================
//////
//////    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////            List<PaymentResponseDTO> response = paymentRepository.findByStatus("PENDING").stream()
//////                    .map(this::convertToDTO)
//////                    .collect(Collectors.toList());
//////            printDebugResponse("getPendingPayments", response);
//////            return response;
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getPendingPayments_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////            List<PaymentResponseDTO> response = paymentRepository.findAll().stream()
//////                    .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
//////                    .map(this::convertToDTO)
//////                    .collect(Collectors.toList());
//////            printDebugResponse("getAllPayments", response);
//////            return response;
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getAllPayments_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////            PaymentDetails payment = paymentRepository.findById(paymentId)
//////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
//////            PaymentResponseDTO response = convertToDTO(payment);
//////            printDebugResponse("getPaymentById", response);
//////            return response;
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getPaymentById_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    @Transactional
//////    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////
//////            User director = userRepository.findById(headDirectorId).get();
//////            PaymentDetails payment = paymentRepository.findById(paymentId)
//////                    .orElseThrow(() -> new RuntimeException("Payment not found"));
//////
//////            if (!"PENDING".equals(payment.getStatus())) {
//////                throw new RuntimeException("Payment is already " + payment.getStatus());
//////            }
//////
//////            payment.setStatus(request.getStatus());
//////            payment.setApprovedBy(director);
//////            payment.setApprovedAt(LocalDateTime.now());
//////            payment.setDirectorNote(request.getDirectorNote());
//////
//////            PaymentDetails updated = paymentRepository.save(payment);
//////
//////            sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());
//////
//////            PaymentResponseDTO response = convertToDTO(updated);
//////            printDebugResponse("approvePayment", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("approvePayment_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////
//////            ProjectDetails project = projectRepository.findById(projectId)
//////                    .orElseThrow(() -> new RuntimeException("Project not found"));
//////
//////            List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
//////            Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
//////
//////            Double totalProjectAmount = safeParseDouble(project.getCost());
//////            double paidAmount = totalApproved != null ? totalApproved : 0.0;
//////            double remainingAmount = totalProjectAmount - paidAmount;
//////            double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
//////
//////            ProjectPaymentSummaryDTO response = ProjectPaymentSummaryDTO.builder()
//////                    .projectId(project.getProjectId())
//////                    .projectName(project.getProjectName())
//////                    .totalProjectAmount(String.valueOf(totalProjectAmount))
//////                    .paidAmount(String.valueOf(paidAmount))
//////                    .remainingAmount(String.valueOf(remainingAmount))
//////                    .paidPercentage(paidPercentage)
//////                    .isFullyPaid(remainingAmount <= 0)
//////                    .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
//////                    .build();
//////
//////            printDebugResponse("getProjectPayments", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getProjectPayments_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////
//////            List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
//////            List<PaymentResponseDTO> response = salaryPayments.stream()
//////                    .map(sp -> convertToDTO(sp.getPayment()))
//////                    .collect(Collectors.toList());
//////
//////            printDebugResponse("getSalaryPaymentsByUser", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getSalaryPaymentsByUser_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
//////        try {
//////            validateHeadDirector(headDirectorId);
//////
//////            List<PaymentDetails> allPayments = paymentRepository.findAll();
//////            List<PaymentDetails> approvedPayments = allPayments.stream()
//////                    .filter(p -> "APPROVED".equals(p.getStatus()))
//////                    .collect(Collectors.toList());
//////
//////            double totalRevenue = 0.0;
//////            double totalExpenses = 0.0;
//////
//////            for (PaymentDetails p : approvedPayments) {
//////                double amount = safeParseDouble(p.getAmount());
//////                if ("PROJECT_PAYMENT".equals(p.getType())) {
//////                    totalRevenue += amount;
//////                } else if ("SALARY".equals(p.getType())) {
//////                    totalExpenses += amount;
//////                }
//////            }
//////
//////            long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
//////
//////            PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
//////                    .totalRevenue(totalRevenue)
//////                    .totalExpenses(totalExpenses)
//////                    .netProfit(totalRevenue - totalExpenses)
//////                    .pendingApprovals((int) pendingApprovals)
//////                    .totalTransactions(allPayments.size())
//////                    .approvedTransactions(approvedPayments.size())
//////                    .build();
//////
//////            List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);
//////
//////            List<PaymentResponseDTO> recentTransactions = allPayments.stream()
//////                    .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
//////                    .limit(10)
//////                    .map(this::convertToDTO)
//////                    .collect(Collectors.toList());
//////
//////            List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();
//////
//////            Map<String, Double> revenueByType = new HashMap<>();
//////            revenueByType.put("PROJECT_PAYMENT", totalRevenue);
//////            revenueByType.put("SALARY", totalExpenses);
//////
//////            DashboardPaymentStatsDTO response = DashboardPaymentStatsDTO.builder()
//////                    .summary(summary)
//////                    .monthlyRevenue(monthlyRevenue)
//////                    .recentTransactions(recentTransactions)
//////                    .topProjects(topProjects)
//////                    .revenueByType(revenueByType)
//////                    .build();
//////
//////            printDebugResponse("getDashboardStats", response);
//////            return response;
//////
//////        } catch (RuntimeException e) {
//////            printDebugResponse("getDashboardStats_ERROR", e.getMessage());
//////            throw e;
//////        }
//////    }
//////
//////    // ==================== HELPER METHODS ====================
//////
//////    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
//////        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
//////        LocalDateTime now = LocalDateTime.now();
//////
//////        for (int i = 5; i >= 0; i--) {
//////            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
//////            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
//////            dto.setMonth(month);
//////            dto.setTotalRevenue(0.0);
//////            dto.setTotalExpenses(0.0);
//////            dto.setNetProfit(0.0);
//////            dto.setProjectPaymentsCount(0);
//////            dto.setSalaryPaymentsCount(0);
//////            monthlyMap.put(month, dto);
//////        }
//////
//////        for (PaymentDetails p : payments) {
//////            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
//////            MonthlyRevenueDTO dto = monthlyMap.get(month);
//////            if (dto != null) {
//////                double amount = safeParseDouble(p.getAmount());
//////                if ("PROJECT_PAYMENT".equals(p.getType())) {
//////                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
//////                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
//////                } else {
//////                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
//////                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
//////                }
//////                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
//////                monthlyMap.put(month, dto);
//////            }
//////        }
//////
//////        return new ArrayList<>(monthlyMap.values());
//////    }
//////
//////    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
//////        List<ProjectDetails> projects = projectRepository.findAll();
//////        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
//////
//////        for (ProjectDetails project : projects) {
//////            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
//////            if (totalPaid != null && totalPaid > 0) {
//////                Double totalAmount = safeParseDouble(project.getCost());
//////                ProjectPaymentSummaryDTO dto = new ProjectPaymentSummaryDTO();
//////                dto.setProjectId(project.getProjectId());
//////                dto.setProjectName(project.getProjectName());
//////                dto.setTotalProjectAmount(String.valueOf(totalAmount));
//////                dto.setPaidAmount(String.valueOf(totalPaid));
//////                dto.setRemainingAmount(String.valueOf(totalAmount - totalPaid));
//////                dto.setPaidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0);
//////                dto.setIsFullyPaid(totalPaid >= totalAmount);
//////                result.add(dto);
//////            }
//////        }
//////
//////        result.sort((a,b) -> {
//////            double aPaid = safeParseDouble(a.getPaidAmount());
//////            double bPaid = safeParseDouble(b.getPaidAmount());
//////            return Double.compare(bPaid, aPaid);
//////        });
//////
//////        return result.stream().limit(5).collect(Collectors.toList());
//////    }
//////
//////    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
//////        PaymentResponseDTO dto = new PaymentResponseDTO();
//////        dto.setPaymentId(payment.getPaymentId());
//////        dto.setTransactionId(payment.getTransactionId());
//////        dto.setAmount(payment.getAmount());
//////        dto.setType(payment.getType());
//////        dto.setStatus(payment.getStatus());
//////        dto.setUserName(payment.getUser().getName());
//////        dto.setUserEmail(payment.getUser().getEmail());
//////        dto.setPaymentNote(payment.getPaymentNote());
//////        dto.setDirectorNote(payment.getDirectorNote());
//////        dto.setApprovedByName(payment.getApprovedBy() != null ? payment.getApprovedBy().getName() : null);
//////        dto.setApprovedAt(payment.getApprovedAt());
//////        dto.setDateTime(payment.getDateTime());
//////        return dto;
//////    }
//////
//////    // ==================== EMAIL METHODS ====================
//////
//////    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
//////        try {
//////            List<UserType> userTypes = userTypeRepository.findAll();
//////            User headDirector = userTypes.stream()
//////                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
//////                    .map(UserType::getUser)
//////                    .findFirst()
//////                    .orElse(null);
//////
//////            if (headDirector != null) {
//////                String subject = "💰 New Payment Request - " + payment.getType();
//////                String html = String.format("""
//////                    <html>
//////                    <body style='font-family: Arial, sans-serif;'>
//////                        <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
//////                            <h2 style='color: #4CAF50;'>💰 New Payment Request</h2>
//////                            <p>Dear <strong>%s</strong>,</p>
//////                            <p>A new payment request has been submitted for your approval.</p>
//////                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//////                                <p><strong>📋 Transaction ID:</strong> %s</p>
//////                                <p><strong>💰 Amount:</strong> ₹%s</p>
//////                                <p><strong>📝 Type:</strong> %s</p>
//////                                <p><strong>👤 Submitted By:</strong> %s</p>
//////                                <p><strong>📅 Date:</strong> %s</p>
//////                                <p><strong>📝 Note:</strong> %s</p>
//////                            </div>
//////                            <p>Please login to approve or reject this payment.</p>
//////                            <hr>
//////                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//////                        </div>
//////                    </body>
//////                    </html>
//////                    """,
//////                        headDirector.getName(),
//////                        payment.getTransactionId(),
//////                        payment.getAmount(),
//////                        payment.getType(),
//////                        user.getName(),
//////                        payment.getDateTime().format(formatter),
//////                        payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note"
//////                );
//////                emailService.sendEmail(headDirector.getEmail(), subject, html);
//////            }
//////        } catch (Exception e) {
//////            System.out.println("Email error: " + e.getMessage());
//////        }
//////    }
//////
//////    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
//////        try {
//////            String subject = "💰 Salary Payment Request - " + employee.getName();
//////            String html = String.format("""
//////                <html>
//////                <body style='font-family: Arial, sans-serif;'>
//////                    <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
//////                        <h2 style='color: #4CAF50;'>💰 Salary Payment Request</h2>
//////                        <p>Dear <strong>%s</strong>,</p>
//////                        <p>A salary payment request has been submitted.</p>
//////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//////                            <p><strong>👤 Employee:</strong> %s</p>
//////                            <p><strong>💰 Amount:</strong> ₹%s</p>
//////                            <p><strong>📋 Transaction ID:</strong> %s</p>
//////                            <p><strong>📅 Date:</strong> %s</p>
//////                        </div>
//////                        <p>Please login to approve or reject this payment.</p>
//////                        <hr>
//////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//////                    </div>
//////                </body>
//////                </html>
//////                """,
//////                    director.getName(),
//////                    employee.getName(),
//////                    payment.getAmount(),
//////                    payment.getTransactionId(),
//////                    payment.getDateTime().format(formatter)
//////            );
//////            emailService.sendEmail(director.getEmail(), subject, html);
//////        } catch (Exception e) {
//////            System.out.println("Email error: " + e.getMessage());
//////        }
//////    }
//////
//////    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
//////        try {
//////            String subject = status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected";
//////            String color = status.equals("APPROVED") ? "#4CAF50" : "#f44336";
//////            String icon = status.equals("APPROVED") ? "✅" : "❌";
//////
//////            String html = String.format("""
//////                <html>
//////                <body style='font-family: Arial, sans-serif;'>
//////                    <div style='max-width: 600px; margin: 0 auto; background: %s10; border-radius: 10px; padding: 20px;'>
//////                        <h2 style='color: %s;'>%s Payment %s</h2>
//////                        <p>Dear <strong>%s</strong>,</p>
//////                        <p>Your payment request has been %s.</p>
//////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//////                            <p><strong>📋 Transaction ID:</strong> %s</p>
//////                            <p><strong>💰 Amount:</strong> ₹%s</p>
//////                            <p><strong>📝 Director Note:</strong> %s</p>
//////                        </div>
//////                        <hr>
//////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
//////                    </div>
//////                </body>
//////                </html>
//////                """,
//////                    color, color, icon, status,
//////                    payment.getUser().getName(),
//////                    status.toLowerCase(),
//////                    payment.getTransactionId(),
//////                    payment.getAmount(),
//////                    note != null ? note : "No additional notes"
//////            );
//////            emailService.sendEmail(payment.getUser().getEmail(), subject, html);
//////        } catch (Exception e) {
//////            System.out.println("Email error: " + e.getMessage());
//////        }
//////    }
//////}
//////
//////
//////
//////
//////
//////
//////
//////
//////
//////
//////
////////package com.softmax.shardaos.service;
////////
////////import com.softmax.shardaos.dto.input.ApprovePaymentRequest;
////////import com.softmax.shardaos.dto.input.CreatePaymentRequest;
////////import com.softmax.shardaos.dto.output.*;
////////import com.softmax.shardaos.models.*;
////////import com.softmax.shardaos.repository.*;
////////import org.springframework.beans.factory.annotation.Autowired;
////////import org.springframework.stereotype.Service;
////////import org.springframework.transaction.annotation.Transactional;
////////
////////import java.time.LocalDateTime;
////////import java.time.format.DateTimeFormatter;
////////import java.util.*;
////////import java.util.stream.Collectors;
////////
////////@Service
////////public class PaymentService {
////////
////////    @Autowired
////////    private PaymentDetailsRepository paymentRepository;
////////
////////    @Autowired
////////    private ProjectPaymentRepository projectPaymentRepository;
////////
////////    @Autowired
////////    private SalaryPaymentRepository salaryPaymentRepository;
////////
////////    @Autowired
////////    private ProjectDetailsRepository projectRepository;
////////
////////    @Autowired
////////    private UserRepository userRepository;
////////
////////    @Autowired
////////    private UserTypeRepository userTypeRepository;
////////
////////    @Autowired
////////    private EmployeeDetailsRepository employeeDetailsRepository;
////////
////////    @Autowired
////////    private EmailService emailService;
////////
////////    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
////////    private static final Long HEAD_DEPARTMENT_ID = 1L;
////////
////////    // ==================== VALIDATION ====================
////////
////////    public boolean isHeadDirector(Long userId) {
////////        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
////////        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
////////        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));
////////        if (!isEmp || !isDirector) return false;
////////        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
////////        return emp != null && emp.getDepartmentRef() != null &&
////////                HEAD_DEPARTMENT_ID.equals(emp.getDepartmentRef().getDepartmentId());
////////    }
////////
////////    private void validateHeadDirector(Long userId) {
////////        if (!isHeadDirector(userId)) {
////////            throw new RuntimeException("Access denied: Only Head Director can manage payments");
////////        }
////////    }
////////
////////    // ==================== CREATE PAYMENT ====================
////////
////////    @Transactional
////////    public PaymentResponseDTO createProjectPayment(Long userId, CreatePaymentRequest request) {
////////        User user = userRepository.findById(userId)
////////                .orElseThrow(() -> new RuntimeException("User not found"));
////////
////////        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
////////            throw new RuntimeException("Transaction ID is required");
////////        }
////////        if (request.getAmount() == null || request.getAmount().isEmpty()) {
////////            throw new RuntimeException("Amount is required");
////////        }
////////        if (request.getProjectId() == null) {
////////            throw new RuntimeException("Project ID is required");
////////        }
////////
////////        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
////////            throw new RuntimeException("Transaction ID already exists");
////////        }
////////
////////        ProjectDetails project = projectRepository.findById(request.getProjectId())
////////                .orElseThrow(() -> new RuntimeException("Project not found"));
////////
////////        PaymentDetails payment = new PaymentDetails();
////////        payment.setTransactionId(request.getTransactionId());
////////        payment.setAmount(request.getAmount());
////////        payment.setType("PROJECT_PAYMENT");
////////        payment.setStatus("PENDING");
////////        payment.setUser(user);
////////        payment.setPaymentNote(request.getPaymentNote());
////////        payment.setDateTime(LocalDateTime.now());
////////
////////        PaymentDetails savedPayment = paymentRepository.save(payment);
////////
////////        ProjectPayment projectPayment = new ProjectPayment();
////////        projectPayment.setProject(project);
////////        projectPayment.setPayment(savedPayment);
////////        projectPaymentRepository.save(projectPayment);
////////
////////        sendPaymentNotificationEmail(savedPayment, user);
////////
////////        return convertToDTO(savedPayment);
////////    }
////////
////////    @Transactional
////////    public PaymentResponseDTO createSalaryPayment(Long userId, CreatePaymentRequest request) {
////////        validateHeadDirector(userId);
////////
////////        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
////////            throw new RuntimeException("Transaction ID is required");
////////        }
////////        if (request.getAmount() == null || request.getAmount().isEmpty()) {
////////            throw new RuntimeException("Amount is required");
////////        }
////////        if (request.getUserId() == null) {
////////            throw new RuntimeException("Employee User ID is required");
////////        }
////////
////////        if (paymentRepository.findByTransactionId(request.getTransactionId()).isPresent()) {
////////            throw new RuntimeException("Transaction ID already exists");
////////        }
////////
////////        User employee = userRepository.findById(request.getUserId())
////////                .orElseThrow(() -> new RuntimeException("Employee not found"));
////////
////////        PaymentDetails payment = new PaymentDetails();
////////        payment.setTransactionId(request.getTransactionId());
////////        payment.setAmount(request.getAmount());
////////        payment.setType("SALARY");
////////        payment.setStatus("PENDING");
////////        payment.setUser(employee);
////////        payment.setPaymentNote(request.getPaymentNote());
////////        payment.setDateTime(LocalDateTime.now());
////////
////////        PaymentDetails savedPayment = paymentRepository.save(payment);
////////
////////        SalaryPayment salaryPayment = new SalaryPayment();
////////        salaryPayment.setUser(employee);
////////        salaryPayment.setPayment(savedPayment);
////////        salaryPayment.setMonth(request.getMonth() != null ? request.getMonth() :
////////                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
////////        salaryPayment.setSalaryAmount(request.getAmount());
////////        salaryPaymentRepository.save(salaryPayment);
////////
////////        // Get Head Director info for email
////////        User director = userRepository.findById(userId).get();
////////        sendSalaryPaymentNotificationEmail(savedPayment, employee, director);
////////
////////        return convertToDTO(savedPayment);
////////    }
////////
////////    // ==================== HEAD DIRECTOR APIs ====================
////////
////////    public List<PaymentResponseDTO> getPendingPayments(Long headDirectorId) {
////////        validateHeadDirector(headDirectorId);
////////        return paymentRepository.findByStatus("PENDING").stream()
////////                .map(this::convertToDTO)
////////                .collect(Collectors.toList());
////////    }
////////
////////    public List<PaymentResponseDTO> getAllPayments(Long headDirectorId) {
////////        validateHeadDirector(headDirectorId);
////////        return paymentRepository.findAll().stream()
////////                .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
////////                .map(this::convertToDTO)
////////                .collect(Collectors.toList());
////////    }
////////
////////    public PaymentResponseDTO getPaymentById(Long headDirectorId, Long paymentId) {
////////        validateHeadDirector(headDirectorId);
////////        PaymentDetails payment = paymentRepository.findById(paymentId)
////////                .orElseThrow(() -> new RuntimeException("Payment not found"));
////////        return convertToDTO(payment);
////////    }
////////
////////    @Transactional
////////    public PaymentResponseDTO approvePayment(Long headDirectorId, Long paymentId, ApprovePaymentRequest request) {
////////        validateHeadDirector(headDirectorId);
////////
////////        User director = userRepository.findById(headDirectorId).get();
////////        PaymentDetails payment = paymentRepository.findById(paymentId)
////////                .orElseThrow(() -> new RuntimeException("Payment not found"));
////////
////////        if (!"PENDING".equals(payment.getStatus())) {
////////            throw new RuntimeException("Payment is already " + payment.getStatus());
////////        }
////////
////////        payment.setStatus(request.getStatus());
////////        payment.setApprovedBy(director);
////////        payment.setApprovedAt(LocalDateTime.now());
////////        payment.setDirectorNote(request.getDirectorNote());
////////
////////        PaymentDetails updated = paymentRepository.save(payment);
////////
////////        sendPaymentApprovalEmail(payment, request.getStatus(), request.getDirectorNote());
////////
////////        return convertToDTO(updated);
////////    }
////////
////////    public ProjectPaymentSummaryDTO getProjectPayments(Long headDirectorId, Long projectId) {
////////        validateHeadDirector(headDirectorId);
////////
////////        ProjectDetails project = projectRepository.findById(projectId)
////////                .orElseThrow(() -> new RuntimeException("Project not found"));
////////
////////        List<PaymentDetails> payments = paymentRepository.findPaymentsByProjectId(projectId);
////////        Double totalApproved = paymentRepository.getTotalApprovedAmountByProjectId(projectId);
////////
////////        Double totalProjectAmount = 0.0;
////////        if (project.getCost() != null && !project.getCost().isEmpty()) {
////////            totalProjectAmount = Double.parseDouble(project.getCost().replaceAll("[^0-9.]", ""));
////////        }
////////
////////        double paidAmount = totalApproved != null ? totalApproved : 0.0;
////////        double remainingAmount = totalProjectAmount - paidAmount;
////////        double paidPercentage = totalProjectAmount > 0 ? (paidAmount / totalProjectAmount) * 100 : 0;
////////
////////        return ProjectPaymentSummaryDTO.builder()
////////                .projectId(project.getProjectId())
////////                .projectName(project.getProjectName())
////////                .totalProjectAmount(String.valueOf(totalProjectAmount))
////////                .paidAmount(String.valueOf(paidAmount))
////////                .remainingAmount(String.valueOf(remainingAmount))
////////                .paidPercentage(paidPercentage)
////////                .isFullyPaid(remainingAmount <= 0)
////////                .payments(payments.stream().map(this::convertToDTO).collect(Collectors.toList()))
////////                .build();
////////    }
////////
////////    public List<PaymentResponseDTO> getSalaryPaymentsByUser(Long headDirectorId, Long userId) {
////////        validateHeadDirector(headDirectorId);
////////
////////        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByUserUserId(userId);
////////        return salaryPayments.stream()
////////                .map(sp -> convertToDTO(sp.getPayment()))
////////                .collect(Collectors.toList());
////////    }
////////
////////    public DashboardPaymentStatsDTO getDashboardStats(Long headDirectorId) {
////////        validateHeadDirector(headDirectorId);
////////
////////        List<PaymentDetails> allPayments = paymentRepository.findAll();
////////        List<PaymentDetails> approvedPayments = allPayments.stream()
////////                .filter(p -> "APPROVED".equals(p.getStatus()))
////////                .collect(Collectors.toList());
////////
////////        double totalRevenue = 0.0;
////////        double totalExpenses = 0.0;
////////
////////        for (PaymentDetails p : approvedPayments) {
////////            double amount = Double.parseDouble(p.getAmount().replaceAll("[^0-9.]", ""));
////////            if ("PROJECT_PAYMENT".equals(p.getType())) {
////////                totalRevenue += amount;
////////            } else if ("SALARY".equals(p.getType())) {
////////                totalExpenses += amount;
////////            }
////////        }
////////
////////        long pendingApprovals = allPayments.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
////////
////////        PaymentSummaryDTO summary = PaymentSummaryDTO.builder()
////////                .totalRevenue(totalRevenue)
////////                .totalExpenses(totalExpenses)
////////                .netProfit(totalRevenue - totalExpenses)
////////                .pendingApprovals((int) pendingApprovals)
////////                .totalTransactions(allPayments.size())
////////                .approvedTransactions(approvedPayments.size())
////////                .build();
////////
////////        List<MonthlyRevenueDTO> monthlyRevenue = getMonthlyRevenue(approvedPayments);
////////
////////        List<PaymentResponseDTO> recentTransactions = allPayments.stream()
////////                .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
////////                .limit(10)
////////                .map(this::convertToDTO)
////////                .collect(Collectors.toList());
////////
////////        List<ProjectPaymentSummaryDTO> topProjects = getTopProjectsByPayment();
////////
////////        Map<String, Double> revenueByType = new HashMap<>();
////////        revenueByType.put("PROJECT_PAYMENT", totalRevenue);
////////        revenueByType.put("SALARY", totalExpenses);
////////
////////        return DashboardPaymentStatsDTO.builder()
////////                .summary(summary)
////////                .monthlyRevenue(monthlyRevenue)
////////                .recentTransactions(recentTransactions)
////////                .topProjects(topProjects)
////////                .revenueByType(revenueByType)
////////                .build();
////////    }
////////
////////    // ==================== HELPER METHODS ====================
////////
////////    private List<MonthlyRevenueDTO> getMonthlyRevenue(List<PaymentDetails> payments) {
////////        Map<String, MonthlyRevenueDTO> monthlyMap = new LinkedHashMap<>();
////////        LocalDateTime now = LocalDateTime.now();
////////
////////        for (int i = 5; i >= 0; i--) {
////////            String month = now.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM yyyy"));
////////            MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
////////            dto.setMonth(month);
////////            dto.setTotalRevenue(0.0);
////////            dto.setTotalExpenses(0.0);
////////            dto.setNetProfit(0.0);
////////            dto.setProjectPaymentsCount(0);
////////            dto.setSalaryPaymentsCount(0);
////////            monthlyMap.put(month, dto);
////////        }
////////
////////        for (PaymentDetails p : payments) {
////////            String month = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
////////            MonthlyRevenueDTO dto = monthlyMap.get(month);
////////            if (dto != null) {
////////                double amount = Double.parseDouble(p.getAmount().replaceAll("[^0-9.]", ""));
////////                if ("PROJECT_PAYMENT".equals(p.getType())) {
////////                    dto.setTotalRevenue(dto.getTotalRevenue() + amount);
////////                    dto.setProjectPaymentsCount(dto.getProjectPaymentsCount() + 1);
////////                } else {
////////                    dto.setTotalExpenses(dto.getTotalExpenses() + amount);
////////                    dto.setSalaryPaymentsCount(dto.getSalaryPaymentsCount() + 1);
////////                }
////////                dto.setNetProfit(dto.getTotalRevenue() - dto.getTotalExpenses());
////////                monthlyMap.put(month, dto);
////////            }
////////        }
////////
////////        return new ArrayList<>(monthlyMap.values());
////////    }
////////
////////    private List<ProjectPaymentSummaryDTO> getTopProjectsByPayment() {
////////        List<ProjectDetails> projects = projectRepository.findAll();
////////        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();
////////
////////        for (ProjectDetails project : projects) {
////////            Double totalPaid = paymentRepository.getTotalApprovedAmountByProjectId(project.getProjectId());
////////            if (totalPaid != null && totalPaid > 0) {
////////                Double totalAmount = 0.0;
////////                if (project.getCost() != null && !project.getCost().isEmpty()) {
////////                    totalAmount = Double.parseDouble(project.getCost().replaceAll("[^0-9.]", ""));
////////                }
////////                ProjectPaymentSummaryDTO dto = new ProjectPaymentSummaryDTO();
////////                dto.setProjectId(project.getProjectId());
////////                dto.setProjectName(project.getProjectName());
////////                dto.setTotalProjectAmount(String.valueOf(totalAmount));
////////                dto.setPaidAmount(String.valueOf(totalPaid));
////////                dto.setRemainingAmount(String.valueOf(totalAmount - totalPaid));
////////                dto.setPaidPercentage(totalAmount > 0 ? (totalPaid / totalAmount) * 100 : 0);
////////                dto.setIsFullyPaid(totalPaid >= totalAmount);
////////                result.add(dto);
////////            }
////////        }
////////
////////        result.sort((a,b) -> {
////////            double aPaid = Double.parseDouble(a.getPaidAmount());
////////            double bPaid = Double.parseDouble(b.getPaidAmount());
////////            return Double.compare(bPaid, aPaid);
////////        });
////////
////////        return result.stream().limit(5).collect(Collectors.toList());
////////    }
////////
////////    private PaymentResponseDTO convertToDTO(PaymentDetails payment) {
////////        PaymentResponseDTO dto = new PaymentResponseDTO();
////////        dto.setPaymentId(payment.getPaymentId());
////////        dto.setTransactionId(payment.getTransactionId());
////////        dto.setAmount(payment.getAmount());
////////        dto.setType(payment.getType());
////////        dto.setStatus(payment.getStatus());
////////        dto.setUserName(payment.getUser().getName());
////////        dto.setUserEmail(payment.getUser().getEmail());
////////        dto.setPaymentNote(payment.getPaymentNote());
////////        dto.setDirectorNote(payment.getDirectorNote());
////////        dto.setApprovedByName(payment.getApprovedBy() != null ? payment.getApprovedBy().getName() : null);
////////        dto.setApprovedAt(payment.getApprovedAt());
////////        dto.setDateTime(payment.getDateTime());
////////        return dto;
////////    }
////////
////////    // ==================== EMAIL METHODS ====================
////////
////////    private void sendPaymentNotificationEmail(PaymentDetails payment, User user) {
////////        try {
////////            List<UserType> userTypes = userTypeRepository.findAll();
////////            User headDirector = userTypes.stream()
////////                    .filter(ut -> "EMP".equals(ut.getPtype()) && "DIRECTOR".equals(ut.getStype()))
////////                    .map(UserType::getUser)
////////                    .findFirst()
////////                    .orElse(null);
////////
////////            if (headDirector != null) {
////////                String subject = "💰 New Payment Request - " + payment.getType();
////////                String html = String.format("""
////////                    <html>
////////                    <body style='font-family: Arial, sans-serif;'>
////////                        <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
////////                            <h2 style='color: #4CAF50;'>💰 New Payment Request</h2>
////////                            <p>Dear <strong>%s</strong>,</p>
////////                            <p>A new payment request has been submitted for your approval.</p>
////////                            <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////////                                <p><strong>📋 Transaction ID:</strong> %s</p>
////////                                <p><strong>💰 Amount:</strong> ₹%s</p>
////////                                <p><strong>📝 Type:</strong> %s</p>
////////                                <p><strong>👤 Submitted By:</strong> %s</p>
////////                                <p><strong>📅 Date:</strong> %s</p>
////////                                <p><strong>📝 Note:</strong> %s</p>
////////                            </div>
////////                            <p>Please login to approve or reject this payment.</p>
////////                            <hr>
////////                            <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////////                        </div>
////////                    </body>
////////                    </html>
////////                    """,
////////                        headDirector.getName(),
////////                        payment.getTransactionId(),
////////                        payment.getAmount(),
////////                        payment.getType(),
////////                        user.getName(),
////////                        payment.getDateTime().format(formatter),
////////                        payment.getPaymentNote() != null ? payment.getPaymentNote() : "No note"
////////                );
////////                emailService.sendEmail(headDirector.getEmail(), subject, html);
////////            }
////////        } catch (Exception e) {
////////            System.out.println("Email error: " + e.getMessage());
////////        }
////////    }
////////
////////    private void sendSalaryPaymentNotificationEmail(PaymentDetails payment, User employee, User director) {
////////        try {
////////            String subject = "💰 Salary Payment Request - " + employee.getName();
////////            String html = String.format("""
////////                <html>
////////                <body style='font-family: Arial, sans-serif;'>
////////                    <div style='max-width: 600px; margin: 0 auto; background: #f0f8ff; border-radius: 10px; padding: 20px;'>
////////                        <h2 style='color: #4CAF50;'>💰 Salary Payment Request</h2>
////////                        <p>Dear <strong>%s</strong>,</p>
////////                        <p>A salary payment request has been submitted.</p>
////////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////////                            <p><strong>👤 Employee:</strong> %s</p>
////////                            <p><strong>💰 Amount:</strong> ₹%s</p>
////////                            <p><strong>📋 Transaction ID:</strong> %s</p>
////////                            <p><strong>📅 Date:</strong> %s</p>
////////                        </div>
////////                        <p>Please login to approve or reject this payment.</p>
////////                        <hr>
////////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////////                    </div>
////////                </body>
////////                </html>
////////                """,
////////                    director.getName(),
////////                    employee.getName(),
////////                    payment.getAmount(),
////////                    payment.getTransactionId(),
////////                    payment.getDateTime().format(formatter)
////////            );
////////            emailService.sendEmail(director.getEmail(), subject, html);
////////        } catch (Exception e) {
////////            System.out.println("Email error: " + e.getMessage());
////////        }
////////    }
////////
////////    private void sendPaymentApprovalEmail(PaymentDetails payment, String status, String note) {
////////        try {
////////            String subject = status.equals("APPROVED") ? "✅ Payment Approved" : "❌ Payment Rejected";
////////            String color = status.equals("APPROVED") ? "#4CAF50" : "#f44336";
////////            String icon = status.equals("APPROVED") ? "✅" : "❌";
////////
////////            String html = String.format("""
////////                <html>
////////                <body style='font-family: Arial, sans-serif;'>
////////                    <div style='max-width: 600px; margin: 0 auto; background: %s10; border-radius: 10px; padding: 20px;'>
////////                        <h2 style='color: %s;'>%s Payment %s</h2>
////////                        <p>Dear <strong>%s</strong>,</p>
////////                        <p>Your payment request has been %s.</p>
////////                        <div style='background: white; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////////                            <p><strong>📋 Transaction ID:</strong> %s</p>
////////                            <p><strong>💰 Amount:</strong> ₹%s</p>
////////                            <p><strong>📝 Director Note:</strong> %s</p>
////////                        </div>
////////                        <hr>
////////                        <p style='color: #777; font-size: 12px;'>ShardaOS - Payment System</p>
////////                    </div>
////////                </body>
////////                </html>
////////                """,
////////                    color, color, icon, status,
////////                    payment.getUser().getName(),
////////                    status.toLowerCase(),
////////                    payment.getTransactionId(),
////////                    payment.getAmount(),
////////                    note != null ? note : "No additional notes"
////////            );
////////            emailService.sendEmail(payment.getUser().getEmail(), subject, html);
////////        } catch (Exception e) {
////////            System.out.println("Email error: " + e.getMessage());
////////        }
////////    }
////////}