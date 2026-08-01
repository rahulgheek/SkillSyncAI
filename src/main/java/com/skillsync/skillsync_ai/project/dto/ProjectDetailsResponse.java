package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDetailsResponse(
        UUID id,
        UUID ownerId,
        UUID collegeId,
        String title,
        String description,
        ProjectStatus status,
        List<RequiredSkill> requiredSkills,
        Short maxTeamSize,
        Integer currentMemberCount,
        Instant applicationDeadline,
        Instant createdAt,
        OwnerInfo owner
) {
    public record OwnerInfo(
            String name,
            String profilePictureUrl,
            String department,
            Short graduationYear
    ) {}
}
