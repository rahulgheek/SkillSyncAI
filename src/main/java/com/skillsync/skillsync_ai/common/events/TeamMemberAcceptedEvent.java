package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// Fired by the Matching module, consumed by the Project module
public record TeamMemberAcceptedEvent(
        UUID projectId,
        UUID projectRoleId, // Can be null if joining generally
        UUID userId
) {}