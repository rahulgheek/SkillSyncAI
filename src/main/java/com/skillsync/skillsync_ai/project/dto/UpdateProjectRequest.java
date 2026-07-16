package com.skillsync.skillsync_ai.project.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record UpdateProjectRequest(
        @NotBlank(message = "Title is required") String title,
        String description,
        @Positive(message = "Max team size must be positive") Short maxTeamSize,
        @Future(message = "Deadline must be in the future") Instant applicationDeadline
) {}