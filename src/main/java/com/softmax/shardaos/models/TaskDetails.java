package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_details")
@Getter
@Setter
public class TaskDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    // 🔹 Project reference
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectDetails project;

    // 🔹 Assigned user (employee / consultant)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String taskDetails;

    private LocalDateTime createdAt;

    // 🔹 Created by (Manager / Director)
    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private String taskStatus; // PENDING, IN_PROGRESS, COMPLETED

    private LocalDateTime deadline;

    private String managerNote;

    private String description;
}