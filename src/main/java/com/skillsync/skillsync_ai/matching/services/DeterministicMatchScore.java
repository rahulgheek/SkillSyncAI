package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic, explainable scoring used before (and when) the LLM is
 * unavailable.
 *
 * <p>Weights: 60% canonical skill overlap, 30% proficiency relative to the
 * required skill, 10% endorsements. Every input comes from the shortlist query,
 * so computing this costs zero extra database round trips.</p>
 */
public final class DeterministicMatchScore {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal GENERATED_THRESHOLD = new BigDecimal("40.00");
    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("75.00");
    private static final BigDecimal MEDIUM_CONFIDENCE = new BigDecimal("50.00");
    private static final BigDecimal AI_WEIGHT = new BigDecimal("0.80");
    private static final BigDecimal DETERMINISTIC_WEIGHT = new BigDecimal("0.20");

    private DeterministicMatchScore() {
    }

    public static BigDecimal compute(
            int matchedSkills, int requiredSkillCount, double averageLevel, long endorsements) {

        double overlap = requiredSkillCount <= 0
                ? 0.0
                : Math.min(1.0, matchedSkills / (double) requiredSkillCount);
        // Levels map to 1..4 (BEGINNER..EXPERT); normalise to 0..1.
        double level = Math.max(0.0, Math.min(1.0, (averageLevel - 1.0) / 3.0));
        double endorsementSignal = Math.min(1.0, endorsements / 10.0);

        double score = 100.0 * (0.60 * overlap + 0.30 * level + 0.10 * endorsementSignal);
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Blends the LLM's semantic score with the deterministic score so the LLM
     * cannot completely override verifiable skill evidence.
     */
    public static BigDecimal blend(BigDecimal semanticScore, BigDecimal deterministicScore) {
        BigDecimal semantic = semanticScore == null ? MIN_SCORE : semanticScore;
        BigDecimal deterministic = deterministicScore == null ? MIN_SCORE : deterministicScore;
        BigDecimal blended = semantic.multiply(AI_WEIGHT)
                .add(deterministic.multiply(DETERMINISTIC_WEIGHT));
        return clamp(blended);
    }

    public static RecommendationStatus status(BigDecimal finalScore) {
        BigDecimal score = finalScore == null ? MIN_SCORE : finalScore;
        return score.compareTo(GENERATED_THRESHOLD) >= 0
                ? RecommendationStatus.GENERATED
                : RecommendationStatus.LOW_MATCH;
    }

    public static RecommendationConfidence confidence(BigDecimal score) {
        BigDecimal value = score == null ? MIN_SCORE : score;
        if (value.compareTo(HIGH_CONFIDENCE) >= 0) {
            return RecommendationConfidence.HIGH;
        }
        if (value.compareTo(MEDIUM_CONFIDENCE) >= 0) {
            return RecommendationConfidence.MEDIUM;
        }
        return RecommendationConfidence.LOW;
    }

    public static BigDecimal clamp(BigDecimal score) {
        if (score == null) {
            return MIN_SCORE;
        }
        return score.max(MIN_SCORE).min(MAX_SCORE).setScale(2, RoundingMode.HALF_UP);
    }
}
