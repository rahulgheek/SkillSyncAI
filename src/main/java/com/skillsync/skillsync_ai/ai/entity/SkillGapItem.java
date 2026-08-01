package com.skillsync.skillsync_ai.ai.entity;

// Maps the JSONB array for matched and missing skills
public record SkillGapItem(
        String skillName,
        String importance // e.g., "CRITICAL", "NICE_TO_HAVE"
) {}