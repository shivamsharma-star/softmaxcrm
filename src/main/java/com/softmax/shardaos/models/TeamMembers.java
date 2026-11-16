package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "team_members")
@Getter
@Setter
public class TeamMembers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔹 Member (User)
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    // 🔹 Team reference
    @ManyToOne
    @JoinColumn(name = "team_id", nullable = false)
    private TeamDetails team;

    private String role;
    private String description;
}