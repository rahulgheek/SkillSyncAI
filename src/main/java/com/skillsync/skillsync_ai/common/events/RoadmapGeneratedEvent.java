package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoadmapGeneratedEvent(
        UUID userId,
        UUID roadmapId,
        String roadmapTitle,
        List<CheckpointPayload> checkpoints
) {
    public record CheckpointPayload(
            UUID checkpointId,
            String title,
            String description,
            short orderIndex,
            Instant dueDate,        // UPGRADED: AI calculates the exact date
            TaskPriority priority   // UPGRADED: Strict typing, no parsing needed!
    ) {}
}