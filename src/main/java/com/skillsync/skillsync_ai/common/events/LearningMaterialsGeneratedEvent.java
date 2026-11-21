package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.common.dto.LearningResourceDto;

import java.util.List;
import java.util.UUID;

/**
 * Fired after AI learning materials are generated for an existing checkpoint
 * so the matching task keeps the same resources.
 */
public record LearningMaterialsGeneratedEvent(
        UUID checkpointId,
        List<LearningResourceDto> learningResources
) {
}
