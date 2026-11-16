package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_details")
@Getter
@Setter
public class EmployeeDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long employeeId;

    // 🔹 One employee = one user
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private Boolean isDirectorVerified;

    private String positionName;
    private Double salary;

    private String department; // optional text

    private String skillSet;

    // 🔹 Department FK (master table)
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department departmentRef;
}