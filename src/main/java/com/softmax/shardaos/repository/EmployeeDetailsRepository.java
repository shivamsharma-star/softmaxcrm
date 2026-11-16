package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.EmployeeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeDetailsRepository extends JpaRepository<EmployeeDetails, Long> {

    Optional<EmployeeDetails> findByUserUserId(Long userId);

    // ✅ ADD THIS METHOD
    List<EmployeeDetails> findByDepartmentRefDepartmentId(Long departmentId);
}