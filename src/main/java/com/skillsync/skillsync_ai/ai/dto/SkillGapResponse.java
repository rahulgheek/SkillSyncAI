package com.skillsync.skillsync_ai.ai.dto;

import com.skillsync.skillsync_ai.ai.entity.SkillGapItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SkillGapResponse(
        UUID id,
        UUID projectId,
        String targetRole,
        List<SkillGapItem> missingSkills,
        List<SkillGapItem> matchedSkills,
        BigDecimal matchPercentage,
        Instant createdAt
) {}