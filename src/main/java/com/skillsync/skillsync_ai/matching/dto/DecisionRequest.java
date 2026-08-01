package com.skillsync.skillsync_ai.matching.dto;

import jakarta.validation.constraints.NotNull;

public record DecisionRequest(
        @NotNull(message = "Decision (accept boolean) is required")
        Boolean accept
) {}