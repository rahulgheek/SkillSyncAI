package com.skillsync.skillsync_ai.todo.dto;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import java.time.Instant;

public record TodoForAi(
        String title,
        TaskPriority priority,
        Instant dueDate
) {}