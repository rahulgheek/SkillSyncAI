package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record CandidatePoolPreparedEvent(
        UUID projectId,
        String requiredSkillsJson,
        List<CandidateProfileForAi> filteredCandidates
) {}