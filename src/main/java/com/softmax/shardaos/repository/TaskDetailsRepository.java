package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.TaskDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskDetailsRepository extends JpaRepository<TaskDetails, Long> {
    List<TaskDetails> findByUserUserId(Long userId);
    List<TaskDetails> findByProjectProjectId(Long projectId);
    List<TaskDetails> findByCreatedByUserId(Long userId);
    List<TaskDetails> findByProjectProjectIdAndUserUserId(Long projectId, Long userId);
}