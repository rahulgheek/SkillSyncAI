package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        UUID userId,
        UUID roleId,
        String roleTitle,
        MemberStatus status,
        Instant joinedAt
) {}