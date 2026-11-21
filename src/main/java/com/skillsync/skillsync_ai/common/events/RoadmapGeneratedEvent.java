package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;

import java.time.Instant;
import java.util.List;

import java.util.UUID;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;

public record RoadmapGeneratedEvent(
        UUID userId,
        UUID roadmapId,
        UUID sessionId,
        String roadmapTitle,
        List<CheckpointPayload> checkpoints
) {
    public record CheckpointPayload(
            UUID checkpointId,
            String title,
            String description,
            short orderIndex,
            Instant dueDate,        // UPGRADED: AI calculates the exact date
            TaskPriority priority,   // UPGRADED: Strict typing, no parsing needed!
            List<SubtaskDto> subtasks
    ) {}
}
