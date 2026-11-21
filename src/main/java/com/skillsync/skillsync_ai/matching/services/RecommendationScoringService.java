package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredCandidate;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredProject;
import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.matching.entity.ProjectRecommendation;
import com.skillsync.skillsync_ai.matching.repo.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationScoringService {

    private static final int EXPIRY_DAYS = 14;

    private final RecommendationRepository recommendationRepository;
    private final com.skillsync.skillsync_ai.userprofile.UserProfileGateway userProfileGateway;
    private final com.skillsync.skillsync_ai.project.ProjectGateway projectGateway;

    /**
     * Deterministic evidence computed from the shortlist row, used as the
     * fallback when the LLM does not return a score for a candidate.
     */
    public record DeterministicScore(BigDecimal score, List<String> matchedSkills) {
    }

    /**
     * Persists a whole shortlist in one batch: one lookup for existing rows and
     * one {@code saveAll}. No per-candidate select or save.
     */
    @Transactional
    public int persistProjectScores(
            UUID projectId,
            String promptVersion,
            List<ScoredCandidate> aiScores,
            Map<UUID, DeterministicScore> deterministicScores) {

        if (deterministicScores == null || deterministicScores.isEmpty()) {
            return 0;
        }

        Map<UUID, ScoredCandidate> aiByUser = safeList(aiScores).stream()
                .collect(Collectors.toMap(ScoredCandidate::userId, Function.identity(), (a, b) -> a));

        Map<UUID, ProjectRecommendation> existing = recommendationRepository
                .findAllByProjectIdAndUserIdIn(projectId, deterministicScores.keySet())
                .stream()
                .collect(Collectors.toMap(ProjectRecommendation::getUserId, Function.identity()));

        Instant expiresAt = Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS);
        List<ProjectRecommendation> batch = new ArrayList<>(deterministicScores.size());

        deterministicScores.forEach((userId, deterministic) -> {
            ScoredCandidate ai = aiByUser.get(userId);
            BigDecimal semantic = ai != null ? ai.semanticScore() : deterministic.score();
            BigDecimal finalScore = ai != null
                    ? DeterministicMatchScore.blend(semantic, deterministic.score())
                    : DeterministicMatchScore.clamp(deterministic.score());

            ProjectRecommendation recommendation = existing.getOrDefault(
                    userId,
                    ProjectRecommendation.builder()
                            .projectId(projectId)
                            .userId(userId)
                            .build());

            recommendation.setSemanticScore(DeterministicMatchScore.clamp(semantic));
            recommendation.setDeterministicScore(DeterministicMatchScore.clamp(deterministic.score()));
            recommendation.setFinalScore(finalScore);
            recommendation.setConfidence(ai != null
                    ? ai.confidence()
                    : DeterministicMatchScore.confidence(finalScore));
            recommendation.setMatchedSkills(ai != null && !ai.matchedSkills().isEmpty()
                    ? ai.matchedSkills()
                    : deterministic.matchedSkills());
            recommendation.setPromptVersion(promptVersion);
            recommendation.setRationale(ai != null
                    ? ai.rationale()
                    : "Ranked by verified skill overlap. AI rationale is unavailable.");
            recommendation.setStatus(DeterministicMatchScore.status(finalScore));
            recommendation.setExpiresAt(expiresAt);
            batch.add(recommendation);
        });

        recommendationRepository.saveAll(batch);
        log.info("Persisted {} recommendations for project {} ({} AI-scored)",
                batch.size(), projectId, aiByUser.size());
        return batch.size();
    }

    @Transactional
    public int persistStudentScores(
            UUID studentUserId,
            String promptVersion,
            List<ScoredProject> aiScores,
            Map<UUID, DeterministicScore> deterministicScores) {

        if (deterministicScores == null || deterministicScores.isEmpty()) {
            return 0;
        }

        Map<UUID, ScoredProject> aiByProject = safeList(aiScores).stream()
                .collect(Collectors.toMap(ScoredProject::projectId, Function.identity(), (a, b) -> a));

        Map<UUID, ProjectRecommendation> existing = recommendationRepository
                .findAllByUserIdAndProjectIdIn(studentUserId, deterministicScores.keySet())
                .stream()
                .collect(Collectors.toMap(ProjectRecommendation::getProjectId, Function.identity()));

        Instant expiresAt = Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS);
        List<ProjectRecommendation> batch = new ArrayList<>(deterministicScores.size());

        deterministicScores.forEach((projectId, deterministic) -> {
            ScoredProject ai = aiByProject.get(projectId);
            BigDecimal semantic = ai != null ? ai.semanticScore() : deterministic.score();
            BigDecimal finalScore = ai != null
                    ? DeterministicMatchScore.blend(semantic, deterministic.score())
                    : DeterministicMatchScore.clamp(deterministic.score());

            ProjectRecommendation recommendation = existing.getOrDefault(
                    projectId,
                    ProjectRecommendation.builder()
                            .projectId(projectId)
                            .userId(studentUserId)
                            .build());

            recommendation.setSemanticScore(DeterministicMatchScore.clamp(semantic));
            recommendation.setDeterministicScore(DeterministicMatchScore.clamp(deterministic.score()));
            recommendation.setFinalScore(finalScore);
            recommendation.setConfidence(ai != null
                    ? ai.confidence()
                    : DeterministicMatchScore.confidence(finalScore));
            recommendation.setMatchedSkills(ai != null && !ai.matchedSkills().isEmpty()
                    ? ai.matchedSkills()
                    : deterministic.matchedSkills());
            recommendation.setPromptVersion(promptVersion);
            recommendation.setRationale(ai != null
                    ? ai.rationale()
                    : "Ranked by verified skill overlap. AI rationale is unavailable.");
            recommendation.setStatus(DeterministicMatchScore.status(finalScore));
            recommendation.setExpiresAt(expiresAt);
            batch.add(recommendation);
        });

        recommendationRepository.saveAll(batch);
        log.info("Persisted {} recommendations for student {} ({} AI-scored)",
                batch.size(), studentUserId, aiByProject.size());
        return batch.size();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.skillsync.skillsync_ai.matching.dto.ProjectRecommendationResponse> getTopCandidatesForProject(java.util.UUID projectId, org.springframework.data.domain.Pageable pageable) {
        var page = recommendationRepository.findAllByProjectIdOrderByFinalScoreDesc(projectId, pageable);

        var profiles = userProfileGateway.getUserProfiles(
                        page.getContent().stream().map(ProjectRecommendation::getUserId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.skillsync.skillsync_ai.userprofile.UserProfileGateway.UserProfileContext::userId,
                        p -> p
                ));

        return page.map(r -> {
            var profile = profiles.get(r.getUserId());
            return new com.skillsync.skillsync_ai.matching.dto.ProjectRecommendationResponse(
                    r.getId(), r.getProjectId(), r.getUserId(),
                    profile != null ? profile.fullName() : "Unknown Candidate",
                    profile != null ? profile.profilePictureUrl() : null,
                    r.getSemanticScore(), r.getFinalScore(),
                    r.getConfidence() != null ? r.getConfidence().name() : null,
                    r.getMatchedSkills(), r.getRationale(), r.getStatus().name(), r.getExpiresAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.skillsync.skillsync_ai.matching.dto.StudentProjectRecommendationResponse> getRecommendedProjectsForStudent(java.util.UUID studentId, org.springframework.data.domain.Pageable pageable) {
        var page = recommendationRepository.findAllByUserIdOrderByFinalScoreDesc(studentId, pageable);

        var projects = projectGateway.getProjectDiscoveryContexts(
                        page.getContent().stream().map(ProjectRecommendation::getProjectId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.skillsync.skillsync_ai.project.ProjectGateway.ProjectDiscoveryContext::projectId,
                        p -> p
                ));

        return page.map(r -> {
            var projectContext = projects.get(r.getProjectId());
            return new com.skillsync.skillsync_ai.matching.dto.StudentProjectRecommendationResponse(
                    r.getId(), r.getProjectId(),
                    projectContext != null ? projectContext.title() : "Unknown Project",
                    projectContext != null ? projectContext.description() : null,
                    r.getUserId(),
                    r.getSemanticScore(), r.getFinalScore(),
                    r.getConfidence() != null ? r.getConfidence().name() : null,
                    r.getMatchedSkills(), r.getRationale(), r.getStatus().name(), r.getExpiresAt()
            );
        });
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
