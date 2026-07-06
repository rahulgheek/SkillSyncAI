package com.skillsync.skillsync_ai.project.entity.enums;

import java.util.UUID;

// This represents the JSON objects stored in the required_skills JSONB column
public record RequiredSkill(
        UUID skillId,
        String skillName, // Denormalized for fast rendering without joins
        String minimumLevel
) {}