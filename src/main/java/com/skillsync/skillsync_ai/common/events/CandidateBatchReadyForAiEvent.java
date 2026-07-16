package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record CandidateBatchReadyForAiEvent(
        UUID projectId,
        String requiredSkillsJson,
        List<CandidateProfileForAi> candidates // Upgraded from Map
) {}