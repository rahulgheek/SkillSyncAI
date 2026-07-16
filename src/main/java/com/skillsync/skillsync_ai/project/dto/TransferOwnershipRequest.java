package com.skillsync.skillsync_ai.project.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferOwnershipRequest(
        @NotNull(message = "New owner ID is required")
        UUID newOwnerId
) {}