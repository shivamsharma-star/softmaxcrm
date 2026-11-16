package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_request")
@Getter
@Setter
public class ProjectRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    // 🔹 Project reference
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectDetails project;

    // 🔹 Request by User
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}