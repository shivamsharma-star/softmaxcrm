package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "partner_client_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"partner_id", "client_id"})
)
@Getter
@Setter
public class PartnerClientMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mappingId;

    // 🔹 Which partner brought this client
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerDetails partner;

    // 🔹 Which client (ClientDetails.id, not userId)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientDetails client;

    private LocalDateTime assignedAt = LocalDateTime.now();

    // 🔹 Who created this mapping (partner's userId)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(length = 1000)
    private String notes;

    // Values: ACTIVE, INACTIVE
    private String mappingStatus = "ACTIVE";
}