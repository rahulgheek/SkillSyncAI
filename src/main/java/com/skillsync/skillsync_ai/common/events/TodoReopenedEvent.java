package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

/**
 * Published when a completed task is moved back to an active status, either
 * because the user changed its status or unchecked one of its subtasks.
 */
public record TodoReopenedEvent(
        UUID userId,
        UUID todoId,
        UUID relatedEntityId // This is the checkpointId
) {
}
