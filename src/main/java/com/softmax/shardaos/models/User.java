package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String uid; // Firebase UID

    private String name;

    @Column(unique = true)
    private String email;

    private Boolean emailVerified;

    private String photoUrl;

    private String phoneNumber;

    private String fcmToken;

    private Boolean isDglockerVerified;

    private String address;

    private String gender;

    private LocalDate dob;

    private Boolean isProfileCompleted;

    private LocalDate profileCreatedAt;
}