package com.skillsync.skillsync_ai.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompatibilityScoreResponse(
        UUID id,
        UUID projectId,
        UUID studentUserId,
        BigDecimal score,
        String rationale,
        Instant computedAt
) {}