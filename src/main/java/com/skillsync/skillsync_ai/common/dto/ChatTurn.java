package com.skillsync.skillsync_ai.common.dto;

import com.skillsync.skillsync_ai.common.enums.MessageRole;

public record ChatTurn(
        MessageRole role,
        String content
) {}