package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ProjectRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {

    List<ProjectRequest> findByUserUserId(Long userId);

    List<ProjectRequest> findByProjectProjectId(Long projectId);

    boolean existsByProjectProjectIdAndUserUserId(Long projectId, Long userId);

    Optional<ProjectRequest> findByProjectProjectIdAndUserUserId(Long projectId, Long userId);

    // ✅ For manager - get requests for projects they manage
    @Query("SELECT pr FROM ProjectRequest pr WHERE pr.project.assignedManager.userId = :managerId")
    List<ProjectRequest> findByProjectAssignedManagerUserId(Long managerId);

    // ✅ For manager - get requests by department
    @Query("SELECT pr FROM ProjectRequest pr WHERE pr.project.department.departmentId = :departmentId")
    List<ProjectRequest> findByProjectDepartmentDepartmentId(Long departmentId);
}