package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import java.util.List;
import java.util.UUID;

public record ProjectRoleResponse(
        UUID id,
        String title,
        String description,
        List<RequiredSkill> requiredSkills,
        Short headcount,
        Short filledCount
) {}