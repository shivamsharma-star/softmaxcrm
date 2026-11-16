package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ConsultantReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    private String reviewNote;
    private Double reviewMarks;

    // 🔹 Reviewed candidate
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}