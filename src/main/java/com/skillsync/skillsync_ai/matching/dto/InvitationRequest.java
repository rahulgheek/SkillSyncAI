package com.skillsync.skillsync_ai.matching.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InvitationRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotNull(message = "Invited User ID is required")
        UUID invitedUserId,

        UUID projectRoleId, // Optional

        @jakarta.validation.constraints.Size(max = 1000, message = "Invitation message cannot exceed 1000 characters")
        String message
) {}