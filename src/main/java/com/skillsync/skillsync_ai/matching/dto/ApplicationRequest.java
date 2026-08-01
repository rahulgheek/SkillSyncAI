package com.skillsync.skillsync_ai.matching.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApplicationRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        UUID projectRoleId, // Optional: Null means applying to the project generally
        @jakarta.validation.constraints.Size(max = 1000, message = "Cover message cannot exceed 1000 characters")
        String coverMessage
) {}