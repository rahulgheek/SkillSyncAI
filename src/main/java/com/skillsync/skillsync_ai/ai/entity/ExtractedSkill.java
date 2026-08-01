package com.skillsync.skillsync_ai.ai.entity;

// Maps the JSONB array returned by Grok when analyzing a resume
public record ExtractedSkill(
        String skillName,
        String confidence, // e.g., "HIGH", "MEDIUM"
        String inferredLevel // e.g., "BEGINNER", "INTERMEDIATE"
) {}