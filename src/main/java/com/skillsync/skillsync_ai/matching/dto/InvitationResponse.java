package com.skillsync.skillsync_ai.matching.dto;

import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID projectId,
        UUID projectRoleId,
        UUID invitedUserId,
        UUID invitedBy,
        InvitationStatus status,
        String message,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {}