package com.skillsync.skillsync_ai.common.events;

import java.util.List;
import java.util.UUID;

public record CandidateProfileForAi(
        UUID userId,
        UUID studentProfileId,
        List<AiSkill> skills,
        List<AiAchievement> achievements
) {}