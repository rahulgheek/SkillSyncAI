package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record TodoCompletedEvent(
        UUID userId,
        UUID todoId,
        UUID relatedEntityId // This is the checkpointId
) {}
