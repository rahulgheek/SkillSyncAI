package com.skillsync.skillsync_ai.ai.dto;

import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import jakarta.validation.constraints.Size;

public record UpdateRoadmapSessionRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        RoadmapSessionStatus status
) {
}
