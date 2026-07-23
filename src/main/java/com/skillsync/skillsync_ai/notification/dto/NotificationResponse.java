package com.skillsync.skillsync_ai.notification.dto;

import com.skillsync.skillsync_ai.notification.entity.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        boolean isRead,
        UUID relatedEntityId,
        Instant createdAt
) {}