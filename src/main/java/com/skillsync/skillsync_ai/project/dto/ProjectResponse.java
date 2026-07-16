package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID ownerId,
        UUID collegeId,
        String title,
        String description,
        ProjectStatus status,
        List<RequiredSkill> requiredSkills,
        Short maxTeamSize,
        Instant applicationDeadline,
        Instant createdAt
) {}