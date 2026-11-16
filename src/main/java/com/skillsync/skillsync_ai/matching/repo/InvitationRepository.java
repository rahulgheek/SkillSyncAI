package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.Invitation;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Page<Invitation> findAllByInvitedUserIdAndStatus(UUID invitedUserId, InvitationStatus status, Pageable pageable);
    Page<Invitation> findAllByProjectId(UUID projectId, Pageable pageable);

    // Prevent duplicate pending invitations
    boolean existsByProjectIdAndInvitedUserIdAndStatus(UUID projectId, UUID invitedUserId, InvitationStatus status);
}