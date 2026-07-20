package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// Allows Notifications or Analytics to react without tightly coupling to Chat
public record ProjectMessageSentEvent(
        UUID messageId,
        UUID projectId,
        UUID senderId,
        String contentPreview
) {}