package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.DepartmentRequestDTO;
import com.softmax.shardaos.dto.output.DepartmentResponseDTO;
import com.softmax.shardaos.models.Department;
import com.softmax.shardaos.models.EmployeeDetails;
import com.softmax.shardaos.models.UserType;
import com.softmax.shardaos.repository.DepartmentRepository;
import com.softmax.shardaos.repository.EmployeeDetailsRepository;
import com.softmax.shardaos.repository.UserTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    private static final Long HEAD_DEPARTMENT_ID = 1L;

    // ==================== VALIDATION ====================

    public boolean isHeadDirector(Long userId) {
        List<UserType> userTypes = userTypeRepository.findByUserUserId(userId);
        boolean isEmp = userTypes.stream().anyMatch(ut -> "EMP".equals(ut.getPtype()));
        boolean isDirector = userTypes.stream().anyMatch(ut -> "DIRECTOR".equals(ut.getStype()));

        if (!isEmp || !isDirector) return false;

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        if (employee == null) return false;

        return employee.getDepartmentRef() != null &&
                HEAD_DEPARTMENT_ID.equals(employee.getDepartmentRef().getDepartmentId());
    }

    private void validateHeadDirector(Long userId) {
        if (!isHeadDirector(userId)) {
            throw new RuntimeException("Access denied: Only Head Director can perform this operation");
        }
    }

    // ==================== PUBLIC APIs (All Users) ====================

    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DepartmentResponseDTO getDepartmentById(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + departmentId));
        return convertToDTO(department);
    }

    // ==================== HEAD DIRECTOR ONLY APIs (CRUD) ====================

    @Transactional
    public DepartmentResponseDTO createDepartment(Long userId, DepartmentRequestDTO request) {
        validateHeadDirector(userId);

        if (request.getDepartmentName() == null || request.getDepartmentName().trim().isEmpty()) {
            throw new RuntimeException("Department name cannot be empty");
        }

        // Check if department already exists
        if (departmentRepository.findByDepartmentName(request.getDepartmentName()).isPresent()) {
            throw new RuntimeException("Department with name '" + request.getDepartmentName() + "' already exists");
        }

        Department department = new Department();
        department.setDepartmentName(request.getDepartmentName());

        Department saved = departmentRepository.save(department);
        return convertToDTO(saved);
    }

    @Transactional
    public DepartmentResponseDTO updateDepartment(Long userId, Long departmentId, DepartmentRequestDTO request) {
        validateHeadDirector(userId);

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + departmentId));

        if (request.getDepartmentName() == null || request.getDepartmentName().trim().isEmpty()) {
            throw new RuntimeException("Department name cannot be empty");
        }

        // Check if another department has same name
        departmentRepository.findByDepartmentName(request.getDepartmentName())
                .ifPresent(existing -> {
                    if (!existing.getDepartmentId().equals(departmentId)) {
                        throw new RuntimeException("Department with name '" + request.getDepartmentName() + "' already exists");
                    }
                });

        department.setDepartmentName(request.getDepartmentName());
        Department updated = departmentRepository.save(department);
        return convertToDTO(updated);
    }

    @Transactional
    public void deleteDepartment(Long userId, Long departmentId) {
        validateHeadDirector(userId);

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + departmentId));

        // Check if department is HEAD department (cannot delete)
        if (HEAD_DEPARTMENT_ID.equals(departmentId)) {
            throw new RuntimeException("Cannot delete HEAD department");
        }

        // Check if any employee belongs to this department
        List<EmployeeDetails> employees = employeeDetailsRepository.findByDepartmentRefDepartmentId(departmentId);
        if (!employees.isEmpty()) {
            throw new RuntimeException("Cannot delete department: " + employees.size() + " employees are assigned to this department");
        }

        departmentRepository.delete(department);
    }

    // ==================== HELPER METHODS ====================

    private DepartmentResponseDTO convertToDTO(Department department) {
        return DepartmentResponseDTO.builder()
                .departmentId(department.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .build();
    }
}