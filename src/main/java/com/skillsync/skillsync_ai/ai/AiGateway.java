package com.skillsync.skillsync_ai.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AiGateway {
    AiDashboardMetrics getDashboardMetrics(UUID userId);

    record AiDashboardMetrics(BigDecimal latestMatchPercentage, List<String> topMissingSkills) {
        public static final AiDashboardMetrics EMPTY = new AiDashboardMetrics(BigDecimal.ZERO, List.of());
    }
}