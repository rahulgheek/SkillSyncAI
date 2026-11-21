package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicMatchScoreTest {

    @Test
    void compute_rewardsOverlapLevelAndEndorsements() {
        // Full overlap, expert level, 10+ endorsements -> 100.
        assertThat(DeterministicMatchScore.compute(4, 4, 4.0, 12))
                .isEqualByComparingTo("100.00");

        // Half overlap, intermediate, no endorsements -> 60*0.5 + 30*0.33 = 40.
        assertThat(DeterministicMatchScore.compute(2, 4, 2.0, 0))
                .isEqualByComparingTo("40.00");

        assertThat(DeterministicMatchScore.compute(0, 0, 0.0, 0))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void blend_keepsDeterministicEvidenceInTheFinalScore() {
        BigDecimal blended = DeterministicMatchScore.blend(new BigDecimal("90"), new BigDecimal("50"));

        // 0.8 * 90 + 0.2 * 50 = 82
        assertThat(blended).isEqualByComparingTo("82.00");
    }

    @Test
    void blend_clampsOutOfRangeValues() {
        assertThat(DeterministicMatchScore.blend(new BigDecimal("150"), new BigDecimal("150")))
                .isEqualByComparingTo("100.00");
        assertThat(DeterministicMatchScore.blend(new BigDecimal("-50"), new BigDecimal("-50")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void confidenceAndStatusUseStableThresholds() {
        assertThat(DeterministicMatchScore.confidence(new BigDecimal("80")))
                .isEqualTo(RecommendationConfidence.HIGH);
        assertThat(DeterministicMatchScore.confidence(new BigDecimal("60")))
                .isEqualTo(RecommendationConfidence.MEDIUM);
        assertThat(DeterministicMatchScore.confidence(new BigDecimal("20")))
                .isEqualTo(RecommendationConfidence.LOW);

        assertThat(DeterministicMatchScore.status(new BigDecimal("40")))
                .isEqualTo(RecommendationStatus.GENERATED);
        assertThat(DeterministicMatchScore.status(new BigDecimal("39.99")))
                .isEqualTo(RecommendationStatus.LOW_MATCH);
    }
}
