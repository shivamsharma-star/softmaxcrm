package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.TeamDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamDetailsRepository extends JpaRepository<TeamDetails, Long> {
    List<TeamDetails> findByProjectProjectId(Long projectId);
    List<TeamDetails> findByManagerUserId(Long managerId);
}