package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_type",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ptype", "stype"})
)
@Getter
@Setter
public class UserType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔹 User reference
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String ptype; // EMP, CLIENT, CONSULTANT
    private String stype; // ManagerA, ManagerB
    private String isapproved;
    private LocalDateTime createdAt;
}