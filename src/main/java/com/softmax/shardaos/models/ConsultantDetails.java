package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ConsultantDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long consultantId;

    private Double marks10;
    private Double marks12;
    private Double graduationMarks;

    private String stream;
    private String experience;

    private String skill;
    private String skillType;

    private String resumeLink;
    private String ctc;
    // 🔹 User (candidate)
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 🔹 Director approval
    @ManyToOne
    @JoinColumn(name = "director_approved_by")
    private User directorApproved;

    // 🔹 Department
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;
}