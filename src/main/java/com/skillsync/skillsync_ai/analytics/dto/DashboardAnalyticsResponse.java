package com.skillsync.skillsync_ai.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardAnalyticsResponse(
        TaskMetrics tasks,
        SkillMetrics skills,
        EngagementMetrics engagement
) {
    public record TaskMetrics(long totalTasks, long completedTasks, int completionPercentage) {}

    // CHANGED: Double is now BigDecimal!
    public record SkillMetrics(BigDecimal latestMatchPercentage, List<String> focusSkills) {}

    public record EngagementMetrics(long totalConversations) {}
}