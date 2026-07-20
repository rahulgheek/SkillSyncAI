package com.skillsync.skillsync_ai.matching.dto;

import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID projectId,
        UUID projectRoleId,
        UUID applicantUserId,
        ApplicationStatus status,
        String coverMessage,
        Instant createdAt,
        Instant decidedAt
) {}