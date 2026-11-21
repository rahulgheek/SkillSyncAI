package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record TeamMemberRoleChangedEvent(
        UUID projectId,
        UUID userId,
        String projectTitle,
        String oldRoleTitle,
        String newRoleTitle
) {}
