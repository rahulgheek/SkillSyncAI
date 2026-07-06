package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateProjectRoleRequest(
        @NotBlank(message = "Role title is required")
        String title,

        String description,

        List<RequiredSkill> requiredSkills,

        @Positive(message = "Headcount must be at least 1")
        Short headcount
) {}