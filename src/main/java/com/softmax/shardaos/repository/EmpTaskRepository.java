package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.EmpTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmpTaskRepository extends JpaRepository<EmpTask, Long> {
    List<EmpTask> findByAssignedToUserId(Long userId);
    List<EmpTask> findByCreatorUserId(Long userId);
}