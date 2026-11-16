package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ProjectFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectFilesRepository extends JpaRepository<ProjectFiles, Long> {

    // ✅ Project ke files
    List<ProjectFiles> findByProject_ProjectId(Long projectId);

    // ✅ Uploader ke files
    List<ProjectFiles> findByUploaderUserId(Long userId);

    // ✅ Department ke files
    List<ProjectFiles> findByDepartmentDepartmentId(Long departmentId);
}