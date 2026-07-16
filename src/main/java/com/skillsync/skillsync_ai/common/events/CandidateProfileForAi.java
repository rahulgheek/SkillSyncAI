package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record CandidateProfileForAi(
        UUID userId,
        List<AiSkill> skills // Upgraded from a pre-formatted String
) {}