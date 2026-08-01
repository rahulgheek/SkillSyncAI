package com.skillsync.skillsync_ai.userprofile.dto;

import java.util.UUID;

public record AchievementResponse(
        UUID id,
        String title,
        String organization,
        String date,
        String description,
        String type,
        String certificateUrl
) {}
