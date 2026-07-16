package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record ProjectOwnershipTransferredEvent(
        UUID projectId,
        String projectTitle,
        UUID oldOwnerId,
        UUID newOwnerId
) {}