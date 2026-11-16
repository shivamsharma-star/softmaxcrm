package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectStatusRepository extends JpaRepository<ProjectStatus, Long> {

    List<ProjectStatus> findByProjectProjectId(Long projectId);
}