package com.skillsync.skillsync_ai.ai;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectProfileForAi;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Synchronous AI re-ranking boundary. Callers are responsible for running this
 * on the dedicated AI executor; the gateway never touches the request thread
 * itself and never persists data.
 */
public interface AiMatchingGateway {

    List<ScoredCandidate> scoreCandidates(
            UUID projectId, String requiredSkillsJson, List<CandidateProfileForAi> candidates);

    List<ScoredProject> scoreProjects(
            UUID studentUserId, String studentProfileJson, List<ProjectProfileForAi> projects);

    record ScoredCandidate(
            UUID userId,
            BigDecimal semanticScore,
            RecommendationConfidence confidence,
            List<String> matchedSkills,
            String rationale
    ) {
    }

    record ScoredProject(
            UUID projectId,
            BigDecimal semanticScore,
            RecommendationConfidence confidence,
            List<String> matchedSkills,
            String rationale
    ) {
    }
}
