package com.skillsync.skillsync_ai.messaging.dto;

import java.time.LocalDateTime;

public record ChatInboxItemDto(
        String id, // projectId or userId
        String type, // "PROJECT" or "DIRECT"
        String name,
        String avatarUrl,
        String lastMessage,
        LocalDateTime lastMessageAt,
        int unreadCount
) {}
