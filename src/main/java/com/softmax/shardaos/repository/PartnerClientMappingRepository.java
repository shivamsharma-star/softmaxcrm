package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.PartnerClientMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerClientMappingRepository extends JpaRepository<PartnerClientMapping, Long> {

    // ==================== BY PARTNER ====================

    List<PartnerClientMapping> findByPartnerPartnerId(Long partnerId);

    List<PartnerClientMapping> findByPartnerPartnerIdAndMappingStatus(Long partnerId, String mappingStatus);

    long countByPartnerPartnerId(Long partnerId);

    long countByPartnerPartnerIdAndMappingStatus(Long partnerId, String mappingStatus);

    // ==================== BY CLIENT ====================

    List<PartnerClientMapping> findByClientId(Long clientId);

    List<PartnerClientMapping> findByClientUserUserId(Long clientUserId);

    Optional<PartnerClientMapping> findFirstByClientIdAndMappingStatus(Long clientId, String mappingStatus);

    // ==================== SPECIFIC PAIR ====================

    Optional<PartnerClientMapping> findByPartnerPartnerIdAndClientId(Long partnerId, Long clientId);

    boolean existsByPartnerPartnerIdAndClientId(Long partnerId, Long clientId);

    // ==================== BY PARTNER CODE (convenience) ====================

    @Query("""
        SELECT m FROM PartnerClientMapping m
        WHERE m.partner.partnerCode = :partnerCode
          AND m.mappingStatus = 'ACTIVE'
        """)
    List<PartnerClientMapping> findActiveByPartnerCode(@Param("partnerCode") String partnerCode);

    // ==================== RECENT ====================

    @Query("""
        SELECT m FROM PartnerClientMapping m
        WHERE m.partner.partnerId = :partnerId
        ORDER BY m.assignedAt DESC
        """)
    List<PartnerClientMapping> findByPartnerIdOrderByAssignedAtDesc(@Param("partnerId") Long partnerId);

    // ==================== FETCH WITH CLIENT (avoid N+1 on dashboard) ====================

    @Query("""
        SELECT m FROM PartnerClientMapping m
        JOIN FETCH m.client c
        JOIN FETCH c.user
        WHERE m.partner.partnerId = :partnerId
          AND m.mappingStatus = 'ACTIVE'
        """)
    List<PartnerClientMapping> findActiveWithClientByPartnerId(@Param("partnerId") Long partnerId);

    // ==================== SECURITY CHECK ====================

    /**
     * True if the given client (ClientDetails.id) belongs to this partner
     * via an ACTIVE mapping. Use this before allowing any partner action on a client.
     */
    @Query("""
        SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END
        FROM PartnerClientMapping m
        WHERE m.partner.partnerId = :partnerId
          AND m.client.id = :clientId
          AND m.mappingStatus = 'ACTIVE'
        """)
    boolean isClientOwnedByPartner(@Param("partnerId") Long partnerId,
                                   @Param("clientId") Long clientId);
}