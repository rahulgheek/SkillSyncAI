package com.skillsync.skillsync_ai.ai.dto;

import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight session representation used by the session list and the
 * dashboard's "most recent session" card.
 */
public record RoadmapSessionSummaryResponse(
        UUID id,
        String title,
        String targetRole,
        RoadmapSessionStatus status,
        Instant lastViewedAt,
        Instant createdAt,
        Instant updatedAt,
        int roadmapCount,
        int checkpointCount,
        int completedCheckpoints,
        int taskCount,
        int completedTasks,
        int progressPercentage,
        UUID latestRoadmapId,
        String currentTopic
) {
}
