package com.softmax.shardaos.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "partner_details")
@Getter
@Setter
public class PartnerDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long partnerId;

    // 🔹 One partner company = one login user
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String partnerCode;              // e.g. "PTR-A1B2C3D4"

    // ----- Company info -----
    @Column(nullable = false)
    private String companyName;
    private String companyEmail;
    private String companyMobileNumber;
    private String companyWebsite;
    private String companyAddress;
    private String contactPersonName;
    private String contactPersonDesignation;

    // ----- Services offered -----
    // Values like: CLIENT_RELATIONSHIP, SELLING, SECURITY, MARKETING, SUPPORT, DEVELOPMENT, OTHER
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "partner_services", joinColumns = @JoinColumn(name = "partner_id"))
    @Column(name = "service_type")
    private Set<String> servicesOffered = new HashSet<>();

    // ----- Agreement -----
    // agreementType values: PERCENTAGE, MONTHLY, FIXED
    private String agreementType;

    @Column(precision = 19, scale = 2)
    private BigDecimal agreementValue;

    @Column(length = 1000)
    private String agreementPdfDriveLink;    // Google Drive PDF link

    private String agreementStartDate;
    private String agreementEndDate;

    // ----- Status -----
    // Values: PENDING, APPROVED, BLOCKED, DEACTIVATED
    @Column(nullable = false)
    private String partnerStatus = "PENDING";

    private Boolean isApproved = false;

    @Column(length = 1000)
    private String directorNote;

    @Column(length = 1000)
    private String internalNotes;
}