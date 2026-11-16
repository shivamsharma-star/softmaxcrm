package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_status")
@Getter
@Setter
public class ProjectStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statusId;

    private LocalDateTime dateTime;

    private String status;

    // 🔹 Project reference
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectDetails project;

    // 🔹 Handled by Director
    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;

    // 🔹 Assigned to Manager
    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    private String directorNote;
    private String managerNote;

    private LocalDateTime deadline;
}