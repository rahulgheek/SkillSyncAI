package com.skillsync.skillsync_ai.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectChatMessage(
        UUID id,
        UUID projectId,
        UUID senderId,
        String content,
        Instant createdAt
) {}