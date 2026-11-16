package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_payments")
@Getter
@Setter
public class ProjectPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projectPaymentId;

    // 🔹 Project reference
    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectDetails project;

    // 🔹 Payment Details reference
    @ManyToOne
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentDetails payment;
}