package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.PartnerDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerDetailsRepository extends JpaRepository<PartnerDetails, Long> {

    // ==================== BASIC LOOKUPS ====================

    Optional<PartnerDetails> findByUserUserId(Long userId);

    Optional<PartnerDetails> findByPartnerCode(String partnerCode);

    Optional<PartnerDetails> findByCompanyEmail(String companyEmail);

    // ==================== EXISTENCE CHECKS ====================

    boolean existsByPartnerCode(String partnerCode);

    boolean existsByCompanyEmail(String companyEmail);

    boolean existsByUserUserId(Long userId);

    // ==================== STATUS FILTERS ====================

    List<PartnerDetails> findByPartnerStatus(String partnerStatus);

    List<PartnerDetails> findByIsApproved(Boolean isApproved);

    List<PartnerDetails> findByPartnerStatusAndIsApproved(String partnerStatus, Boolean isApproved);

    long countByPartnerStatus(String partnerStatus);

    long countByIsApproved(Boolean isApproved);

    // ==================== SEARCH ====================

    List<PartnerDetails> findByCompanyNameContainingIgnoreCase(String companyName);

    // ==================== DASHBOARD / AGGREGATES ====================

    @Query("SELECT p FROM PartnerDetails p ORDER BY p.createdAt DESC")
    List<PartnerDetails> findAllOrderByCreatedAtDesc();

    @Query("SELECT p FROM PartnerDetails p WHERE p.partnerStatus = :status ORDER BY p.createdAt DESC")
    List<PartnerDetails> findByStatusOrderByCreatedAtDesc(@Param("status") String status);

    // ==================== USER TYPE GUARD ====================

    /**
     * Returns the partner record only if the underlying user
     * is actually tagged as ptype=PARTNER in user_type.
     * Useful as a safety-check before trusting partner endpoints.
     */
    @Query("""
        SELECT p FROM PartnerDetails p
        WHERE p.user.userId = :userId
          AND EXISTS (
              SELECT 1 FROM UserType ut
              WHERE ut.user.userId = :userId
                AND ut.ptype = 'PARTNER'
          )
        """)
    Optional<PartnerDetails> findPartnerIfUserIsPartner(@Param("userId") Long userId);
}