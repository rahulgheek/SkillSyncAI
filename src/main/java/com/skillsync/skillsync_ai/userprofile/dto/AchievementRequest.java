package com.skillsync.skillsync_ai.userprofile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AchievementRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Organization is required") String organization,
        @NotBlank(message = "Date is required") String date,
        String description,
        @NotBlank(message = "Type is required") String type,
        String certificateUrl
) {}
