package com.skillsync.skillsync_ai.ai.dto;

import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full session payload returned when a session is opened. It contains every
 * roadmap, checkpoint, saved task and subtask so the client can render the
 * roadmap workspace from a single request.
 */
public record RoadmapSessionDetailResponse(
        UUID id,
        String title,
        String targetRole,
        String description,
        RoadmapSessionStatus status,
        Instant lastViewedAt,
        Instant createdAt,
        Instant updatedAt,
        SessionProgress progress,
        List<RoadmapDetail> roadmaps,
        List<TodoResponse> tasks
) {

    public record SessionProgress(
            int roadmapCount,
            int checkpointCount,
            int completedCheckpoints,
            int taskCount,
            int completedTasks,
            int progressPercentage
    ) {
    }

    public record RoadmapDetail(
            UUID id,
            String title,
            String description,
            RoadmapStatus status,
            String currentTopic,
            List<String> completedTopics,
            List<String> curriculum,
            Instant createdAt,
            List<CheckpointDetail> checkpoints
    ) {
    }

    public record CheckpointDetail(
            UUID id,
            String title,
            String description,
            short orderIndex,
            CheckpointStatus status,
            LocalDate dueDate,
            Instant completedAt,
            List<SubtaskDto> subtasks
    ) {
    }
}
