package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ProjectDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectDetailsRepository extends JpaRepository<ProjectDetails, Long> {

    // Client ke projects
    List<ProjectDetails> findByUploaderUserId(Long userId);

    // Department ke projects (Director ke liye)
    List<ProjectDetails> findByDepartmentDepartmentId(Long departmentId);

    // Manager ko assign kiye projects
    List<ProjectDetails> findByAssignedManagerUserId(Long managerId);

    // Status wise projects of a department
    List<ProjectDetails> findByDepartmentDepartmentIdAndStatus(Long departmentId, String status);

    // Unassigned projects of a department
    List<ProjectDetails> findByDepartmentDepartmentIdAndAssignedManagerIsNull(Long departmentId);
}