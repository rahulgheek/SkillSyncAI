package com.skillsync.skillsync_ai.matching.dto;

import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

import java.util.List;

public record InvitationResponse(
        UUID id,
        UUID projectId,
        UUID projectRoleId,
        String roleTitle,
        UUID invitedUserId,
        UUID invitedBy,
        InvitationStatus status,
        String message,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt,
        String invitedUserName,
        String invitedUserProfilePictureUrl,
        String invitedUserDepartment,
        Short invitedUserGraduationYear,
        List<String> invitedUserTopSkills
) {}