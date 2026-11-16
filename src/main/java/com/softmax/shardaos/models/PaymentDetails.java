package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_details")
@Getter
@Setter
public class PaymentDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    private String transactionId;      // UPI Transaction ID from QR

    private LocalDateTime dateTime;

    private String type;               // PROJECT_PAYMENT, SALARY

    private String amount;             // Amount in INR

    private String status;             // PENDING, APPROVED, REJECTED

    // 🔹 User (client who paid OR employee receiving salary)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String paymentNote;

    // 🔹 Approved by Head Director
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    private String directorNote;       // Director's note (for approval or rejection)
}