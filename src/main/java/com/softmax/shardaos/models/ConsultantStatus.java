package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ConsultantStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statusId;

    // 🔹 Consultant reference
    @ManyToOne
    @JoinColumn(name = "consultant_id")
    private ConsultantDetails consultant;

    private String status;
    private LocalDateTime dateTime;

    private String note;
}