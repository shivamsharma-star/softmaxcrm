package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_payments")
@Getter
@Setter
public class SalaryPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long salaryPaymentId;

    // 🔹 Employee receiving salary
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 🔹 Payment Details reference
    @ManyToOne
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentDetails payment;

    private String month;              // e.g., "April 2026"

    private String salaryAmount;
}