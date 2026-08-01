package com.skillsync.skillsync_ai.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentProjectRecommendationResponse(
        UUID id,
        UUID projectId,
        String projectTitle,
        String projectDescription,
        UUID userId,
        BigDecimal semanticScore,
        BigDecimal finalScore,
        String confidence,
        List<String> matchedSkills,
        String rationale,
        String status,
        Instant expiresAt
) {}
