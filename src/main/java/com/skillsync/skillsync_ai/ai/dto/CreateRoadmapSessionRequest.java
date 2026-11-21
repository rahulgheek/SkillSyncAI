package com.skillsync.skillsync_ai.ai.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request used to create a new AI roadmap session.
 *
 * <p>When {@code skillGapId} is supplied the roadmap is generated from an
 * existing skill-gap analysis. Otherwise the AI builds a custom path from the
 * user's profile, chats and the optional free-text fields below.</p>
 */
public record CreateRoadmapSessionRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 200, message = "Target role must be at most 200 characters")
        String targetRole,

        @Size(max = 4000, message = "Current knowledge must be at most 4000 characters")
        String currentKnowledge,

        @Size(max = 4000, message = "Knowledge gaps must be at most 4000 characters")
        String knowledgeGaps,

        UUID skillGapId
) {
}
