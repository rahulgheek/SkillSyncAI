package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record ApplicationSubmittedEvent(
        UUID applicantId,
        UUID projectId,
        UUID projectOwnerId,
        String applicantName,
        String projectTitle
) {
}
