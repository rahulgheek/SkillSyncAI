package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// Fired by the Project module, consumed by the AI module to trigger match scoring
public record ProjectPublishedEvent(
        UUID projectId,
        String projectTitle,
        String requiredSkillsJson // Pass as a string to avoid complex DTO dependencies across modules
) {}