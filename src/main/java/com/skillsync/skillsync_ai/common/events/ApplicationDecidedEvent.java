package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record ApplicationDecidedEvent(
        UUID applicationId,
        UUID applicantUserId,
        UUID projectId,
        String projectTitle,
        boolean accepted
) {}
