package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record DirectMessageSentEvent(
        UUID messageId,
        UUID senderId,
        UUID recipientId,
        String contentPreview
) {}
