package com.skillsync.skillsync_ai.project.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        // Optional: null adds the member without a named project role.
        UUID roleId
) {}
