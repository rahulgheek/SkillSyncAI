package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record TeamMemberRemovedEvent(
        UUID projectId,
        UUID userId,
        String projectTitle,
        boolean removedByOwner
) {}
