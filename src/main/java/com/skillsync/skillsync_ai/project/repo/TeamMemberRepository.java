package com.skillsync.skillsync_ai.project.repo;

import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findAllByProjectIdAndStatus(UUID projectId, MemberStatus status);
    Optional<TeamMember> findByProjectIdAndUserIdAndStatus(UUID projectId, UUID userId, MemberStatus status);
    List<TeamMember> findAllByUserIdAndStatus(UUID userId, MemberStatus status);
}