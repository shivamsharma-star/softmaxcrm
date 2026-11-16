package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "client_details")
@Getter
@Setter
public class ClientDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔹 One client = one user
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String clientStatus; // ACTIVE, DEACTIVE, BLOCKED

    private String companyName;
    private String companyMobileNumber;
    private String companyEmail;

    private String orgType;     // Pvt Ltd, Startup, etc.
    private String clientType;  // Individual / Company

    private Boolean isApproved;

    private String clientWebsite;
}