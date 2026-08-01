package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record RoadmapDeactivatedEvent(
        UUID userId,
        UUID deactivatedRoadmapId,
        List<UUID> checkpointIds
) {}
