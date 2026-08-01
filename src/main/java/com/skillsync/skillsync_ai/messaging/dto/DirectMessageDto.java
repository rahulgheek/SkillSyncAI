package com.skillsync.skillsync_ai.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DirectMessageDto(
        UUID id,
        UUID senderId,
        String senderName,
        String senderAvatar,
        UUID recipientId,
        String content,
        LocalDateTime createdAt,
        boolean isRead
) {}
