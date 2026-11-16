package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "team_details")
@Getter
@Setter
public class TeamDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teamId;

    // 🔹 Project reference
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectDetails project;

    private String teamName;

    // 🔹 Manager of team
    @ManyToOne
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;
}