package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

/**
 * Fired when the team becomes full (full=true) or a slot opens again
 * (full=false).
 */
public record ProjectRecruitmentStatusChangedEvent(
        UUID projectId,
        UUID projectOwnerId,
        String projectTitle,
        boolean full
) {}
