package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_details")
@Getter
@Setter
public class ProjectDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projectId;

    private String projectName;
    private String projectDescription;
    private String projectType;
//    department id added by BDE
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // 🔹 Uploaded by Client
    @ManyToOne
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    // Add these fields to your existing ProjectDetails class

    @Column(nullable = false)
    private String status = "PENDING";

    @ManyToOne
    @JoinColumn(name = "assigned_manager_id")
    private User assignedManager;
    //add extra filed cost
    private String Cost;
    private LocalDateTime deadline;
}