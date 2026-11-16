package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserTypeRepository extends JpaRepository<UserType, Long> {

    List<UserType> findByUserUserId(Long userId);
    // ✅ Get all consultants
    List<UserType> findByPtypeAndIsapproved(String ptype, String isapproved);

    // ✅ Update user type
    @Modifying
    @Query("UPDATE UserType ut SET ut.isapproved = :status WHERE ut.user.userId = :userId AND ut.ptype = 'CONSULTANT'")
    void updateConsultantApprovalStatus(Long userId, String status);
    // ✅ Add this if not exists
    Optional<UserType> findByUserUserIdAndPtype(Long userId, String ptype);
}