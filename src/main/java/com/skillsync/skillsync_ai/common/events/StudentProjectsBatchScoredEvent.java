package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentProjectsBatchScoredEvent(
        UUID studentUserId,
        String promptVersion,
        List<IndividualProjectScore> scoredProjects
) {
    public record IndividualProjectScore(
            UUID projectId,
            BigDecimal semanticScore,
            RecommendationConfidence confidence,
            List<String> matchedSkills,
            String rationale
    ) {}
}
