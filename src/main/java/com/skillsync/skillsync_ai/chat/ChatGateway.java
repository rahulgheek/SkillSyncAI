package com.skillsync.skillsync_ai.chat;

import java.util.UUID;

public interface ChatGateway {
    ChatMetrics getChatMetrics(UUID userId);
    record ChatMetrics(long totalConversations) {}
}