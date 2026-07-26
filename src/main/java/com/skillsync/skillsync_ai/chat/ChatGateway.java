package com.skillsync.skillsync_ai.chat;

import java.util.List;
import java.util.UUID;

public interface ChatGateway {
    ChatMetrics getChatMetrics(UUID userId);
    record ChatMetrics(long totalConversations) {}
    
    // Retrieves the most recent chat messages across the user's rooms (limit 50)
    List<String> getRecentChatHistory(UUID userId, int limit);
}