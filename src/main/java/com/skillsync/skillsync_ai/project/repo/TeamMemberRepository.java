package com.skillsync.skillsync_ai.project.repo;

import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findAllByProjectIdAndStatus(UUID projectId, MemberStatus status);

    @EntityGraph(attributePaths = "projectRole")
    Optional<TeamMember> findByProjectIdAndUserIdAndStatus(UUID projectId, UUID userId, MemberStatus status);

    List<TeamMember> findAllByUserIdAndStatus(UUID userId, MemberStatus status);

    long countByProjectIdAndStatus(UUID projectId, MemberStatus status);

    @Query("SELECT DISTINCT p FROM TeamMember tm JOIN tm.project p WHERE tm.userId = :userId AND tm.status = :status")
    List<Project> findActiveProjectsByUserId(@Param("userId") UUID userId, @Param("status") MemberStatus status);
}
