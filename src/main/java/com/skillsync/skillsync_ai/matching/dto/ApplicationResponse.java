package com.skillsync.skillsync_ai.matching.dto;

import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

import java.util.List;

public record ApplicationResponse(
        UUID id,
        UUID projectId,
        UUID projectRoleId,
        String roleTitle,
        UUID applicantUserId,
        ApplicationStatus status,
        String coverMessage,
        Instant createdAt,
        Instant decidedAt,
        String applicantName,
        String applicantProfilePictureUrl,
        String applicantDepartment,
        Short applicantGraduationYear,
        List<String> applicantTopSkills
) {}