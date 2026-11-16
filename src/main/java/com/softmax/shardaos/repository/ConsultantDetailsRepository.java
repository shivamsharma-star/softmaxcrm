package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ConsultantDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConsultantDetailsRepository extends JpaRepository<ConsultantDetails, Long> {

    List<ConsultantDetails> findByUserUserId(Long userId);

    List<ConsultantDetails> findAll();
    // ✅ ADD THIS METHOD
    List<ConsultantDetails> findByDepartmentDepartmentId(Long departmentId);
    Optional<ConsultantDetails> findFirstByUserUserId(Long userId);
}