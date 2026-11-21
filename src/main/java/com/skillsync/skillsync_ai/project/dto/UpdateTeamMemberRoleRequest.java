package com.skillsync.skillsync_ai.project.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateTeamMemberRoleRequest(
        @NotNull(message = "Role ID is required")
        UUID roleId
) {}
