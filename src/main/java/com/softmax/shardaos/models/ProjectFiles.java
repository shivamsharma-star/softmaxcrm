package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_files")
@Getter
@Setter
public class ProjectFiles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    private String fileType;
    private String fileLink;

    // 🔹 Uploaded by (User)
    @ManyToOne
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    private LocalDateTime uploadTime;

    // 🔹 Department reference
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // ✅ ADD THIS - Project reference
    @ManyToOne
    @JoinColumn(name = "project_id")
    private ProjectDetails project;
}