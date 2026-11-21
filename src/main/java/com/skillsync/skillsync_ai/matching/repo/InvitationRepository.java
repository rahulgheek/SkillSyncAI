package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.Invitation;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Page<Invitation> findAllByInvitedUserIdAndStatus(UUID invitedUserId, InvitationStatus status, Pageable pageable);
    Page<Invitation> findAllByProjectId(UUID projectId, Pageable pageable);

    // Prevent duplicate pending invitations
    boolean existsByProjectIdAndInvitedUserIdAndStatus(UUID projectId, UUID invitedUserId, InvitationStatus status);

    List<Invitation> findAllByProjectIdAndInvitedUserIdAndStatus(
            UUID projectId, UUID invitedUserId, InvitationStatus status);

    long countByProjectIdAndProjectRoleIdAndStatus(
            UUID projectId, UUID projectRoleId, InvitationStatus status);

    long countByProjectIdAndStatusAndExpiresAtBefore(
            UUID projectId, InvitationStatus status, Instant before);

    @Query("""
            SELECT i.status, COUNT(i)
            FROM Invitation i
            WHERE i.projectId = :projectId
            GROUP BY i.status
            """)
    List<Object[]> countByProjectIdGroupByStatus(@Param("projectId") UUID projectId);

    @Query("""
            SELECT i.projectRoleId, COUNT(i)
            FROM Invitation i
            WHERE i.projectId = :projectId
              AND i.status = :status
              AND i.projectRoleId IS NOT NULL
            GROUP BY i.projectRoleId
            """)
    List<Object[]> countByProjectIdAndStatusGroupByRole(
            @Param("projectId") UUID projectId,
            @Param("status") InvitationStatus status);
}