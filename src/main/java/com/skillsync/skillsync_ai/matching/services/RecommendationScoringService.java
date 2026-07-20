package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.events.CompatibilityBatchScoredEvent;
import com.skillsync.skillsync_ai.common.events.CompatibilityBatchScoredEvent.IndividualCandidateScore;
import com.skillsync.skillsync_ai.matching.entity.ProjectRecommendation;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus;
import com.skillsync.skillsync_ai.matching.repo.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationScoringService {

    private final RecommendationRepository recommendationRepository;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBatchScored(CompatibilityBatchScoredEvent event) {
        log.info("Applying deterministic business rules to {} AI scores for Project {}",
                event.scoredCandidates().size(), event.projectId());

        for (IndividualCandidateScore score : event.scoredCandidates()) {
            try {
                // 1. The AI Foundation
                BigDecimal finalScore = score.semanticScore();

                // 2. Business Rule: Activity Bonus (+5 points if highly active)
                if (isUserHighlyActive(score.userId())) {
                    finalScore = finalScore.add(new BigDecimal("5.0"));
                }

                // 3. Business Rule: Penalty for low completion rate (-10 points)
                if (hasLowCompletionRate(score.userId())) {
                    finalScore = finalScore.subtract(new BigDecimal("10.0"));
                }

                // 4. Cap & Floor Constraints
                finalScore = finalScore.max(BigDecimal.ZERO).min(new BigDecimal("100.0"));

                // 5. Threshold Validation
                com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus status = finalScore.compareTo(new BigDecimal("40.0")) >= 0
                        ? RecommendationStatus.GENERATED
                        : RecommendationStatus.LOW_MATCH;

                // 6. Persistence
                ProjectRecommendation recommendation = recommendationRepository
                        .findByProjectIdAndUserId(event.projectId(), score.userId())
                        .orElse(ProjectRecommendation.builder()
                                .projectId(event.projectId())
                                .userId(score.userId())
                                .build());

                // Update fields
                recommendation.setSemanticScore(score.semanticScore());
                recommendation.setFinalScore(finalScore);
                recommendation.setConfidence(score.confidence()); // Direct Enum assignment
                recommendation.setMatchedSkills(score.matchedSkills()); // Rich UI data
                recommendation.setPromptVersion(event.promptVersion());
                recommendation.setRationale(score.rationale());
                recommendation.setStatus(status);

                // Recommendations expire after 14 days to keep the feed fresh
                recommendation.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));

                recommendationRepository.save(recommendation);

            } catch (Exception e) {
                log.error("Failed to process scoring for User {} on Project {}", score.userId(), event.projectId(), e);
            }
        }

        log.info("Successfully persisted ranked recommendations for Project {}", event.projectId());
    }

    // --- Mock Business Logic Fetchers (Integrate with your actual Gateways) ---
    private boolean isUserHighlyActive(java.util.UUID userId) {
        // e.g., call UserProfileGateway.hasRecentActivity(userId)
        return true;
    }

    private boolean hasLowCompletionRate(java.util.UUID userId) {
        // e.g., call AnalyticsGateway.getCompletionRate(userId) < 0.5
        return false;
    }
}