package com.skillsync.skillsync_ai.todo.dto;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TodoResponse(
        UUID id,
        String title,
        String description,
        TodoStatus status,
        TaskPriority priority,
        Instant dueDate,
        Instant completedAt,
        UUID relatedEntityId,
        UUID roadmapSessionId,
        String sourceTitle, // Frontend can display this as a subtle tag
        Instant createdAt,
        List<SubtaskDto> subtasks
) {
    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.getStatus(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.getCompletedAt(),
                todo.getRelatedEntityId(),
                todo.getRoadmapSessionId(),
                todo.getSourceTitle(),
                todo.getCreatedAt(),
                todo.getSubtasks()
        );
    }
}
