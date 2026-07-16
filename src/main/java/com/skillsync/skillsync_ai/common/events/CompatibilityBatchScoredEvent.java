package com.skillsync.skillsync_ai.common.events;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompatibilityBatchScoredEvent(
        UUID projectId,
        String promptVersion,
        Instant generatedAt, // ADDED: Audit timestamp
        List<IndividualCandidateScore> scoredCandidates
) {
    public record IndividualCandidateScore(
            UUID userId,
            BigDecimal semanticScore, // FIXED: Renamed from aiScore
            RecommendationConfidence confidence,
            List<String> matchedSkills,// FIXED: Now an Enum
            String rationale
    ) {}
}