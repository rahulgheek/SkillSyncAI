package com.skillsync.skillsync_ai.todo.dto;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record TodoRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title cannot exceed 150 characters")
        String title,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        TaskPriority priority,
        Instant dueDate,
        UUID relatedEntityId
) {}