package com.skillsync.skillsync_ai.ai.dto;

import com.skillsync.skillsync_ai.ai.entity.ExtractedSkill;
import com.skillsync.skillsync_ai.ai.entity.enums.AnalysisStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SkillAnalysisResponse(
        UUID id,
        String sourceResumeUrl,
        List<ExtractedSkill> extractedSkills,
        String summary,
        AnalysisStatus status,
        String errorMessage,
        Instant createdAt
) {}