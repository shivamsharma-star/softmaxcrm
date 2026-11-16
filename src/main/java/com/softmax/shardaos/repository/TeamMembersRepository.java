package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.TeamMembers;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamMembersRepository extends JpaRepository<TeamMembers, Long> {
    List<TeamMembers> findByTeamTeamId(Long teamId);
    List<TeamMembers> findByMemberUserId(Long userId);
    void deleteByTeamTeamId(Long teamId);
    // ✅ Add this - Check existence
    boolean existsByMemberUserId(Long userId);

    // ✅ Add this - Direct return (nullable)
    TeamMembers findFirstByMemberUserId(Long userId);
}