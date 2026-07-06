package com.skillsync.skillsync_ai.authentication.dto;

public record AuthResponse(
        String token,
        String message
) {}