package com.skillsync.skillsync_ai.chat.dto;
import com.skillsync.skillsync_ai.common.enums.MessageRole;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {}