package com.skillsync.skillsync_ai.common.events;

import java.math.BigDecimal;
import java.util.UUID;


public record CompatibilityScoreComputedEvent(
        UUID projectId,
        UUID studentUserId,
        BigDecimal score,
        String rationale
) {}