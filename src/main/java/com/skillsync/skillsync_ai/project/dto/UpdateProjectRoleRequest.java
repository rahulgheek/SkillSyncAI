package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Partial update. Null fields keep their current value, so the owner can
 * change only the headcount or only the required skills.
 */
public record UpdateProjectRoleRequest(
        String title,
        String description,
        List<RequiredSkill> requiredSkills,

        @Positive(message = "Headcount must be at least 1")
        Short headcount
) {}
