package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingException;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway;
import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectProfileForAi;
import com.skillsync.skillsync_ai.matching.services.DeterministicMatchScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Groq-backed re-ranker. It only receives the shortlist that the SQL query has
 * already produced, and it never persists or publishes events: the matching
 * worker owns both so the whole run has one clear transaction boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSkillGapAnalyzer implements AiMatchingGateway {

    private final GroqApiService groqApiService;
    private final RecommendationPromptFactory promptFactory;
    private final ObjectMapper objectMapper;

    public record GroqCandidateScore(
            UUID userId,
            BigDecimal semanticScore,
            String confidence,
            List<String> matchedSkills,
            String rationale) {
    }

    public record GroqBatchResponse(List<GroqCandidateScore> matches) {
    }

    public record GroqProjectScore(
            UUID projectId,
            BigDecimal semanticScore,
            String confidence,
            List<String> matchedSkills,
            String rationale) {
    }

    public record GroqProjectBatchResponse(List<GroqProjectScore> matches) {
    }

    @Override
    public List<ScoredCandidate> scoreCandidates(
            UUID projectId, String requiredSkillsJson, List<CandidateProfileForAi> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String prompt = promptFactory.buildBatchMatchPrompt(requiredSkillsJson, candidates);
        String response = callGroq(prompt, "project " + projectId);

        try {
            GroqBatchResponse parsed = objectMapper.readValue(response, GroqBatchResponse.class);
            Set<UUID> allowed = candidates.stream()
                    .map(CandidateProfileForAi::userId)
                    .collect(Collectors.toSet());

            List<ScoredCandidate> scores = safeList(parsed.matches()).stream()
                    // Never trust an id that was not in the shortlist.
                    .filter(score -> score.userId() != null && allowed.contains(score.userId()))
                    .map(score -> new ScoredCandidate(
                            score.userId(),
                            DeterministicMatchScore.clamp(score.semanticScore()),
                            parseConfidence(score.confidence()),
                            score.matchedSkills() == null ? List.of() : score.matchedSkills(),
                            score.rationale() == null || score.rationale().isBlank()
                                    ? "No rationale provided."
                                    : score.rationale()))
                    .toList();

            if (scores.isEmpty()) {
                throw new AiMatchingException("Groq returned no usable candidate scores");
            }

            log.info("Groq scored {} of {} shortlisted candidates for project {}",
                    scores.size(), candidates.size(), projectId);
            return scores;
        } catch (AiMatchingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiMatchingException("Failed to parse Groq candidate response", e);
        }
    }

    @Override
    public List<ScoredProject> scoreProjects(
            UUID studentUserId, String studentProfileJson, List<ProjectProfileForAi> projects) {

        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        String prompt = promptFactory.buildBatchProjectsPrompt(studentProfileJson, projects);
        String response = callGroq(prompt, "student " + studentUserId);

        try {
            GroqProjectBatchResponse parsed = objectMapper.readValue(response, GroqProjectBatchResponse.class);
            Set<UUID> allowed = projects.stream()
                    .map(ProjectProfileForAi::projectId)
                    .collect(Collectors.toSet());

            List<ScoredProject> scores = safeList(parsed.matches()).stream()
                    .filter(score -> score.projectId() != null && allowed.contains(score.projectId()))
                    .map(score -> new ScoredProject(
                            score.projectId(),
                            DeterministicMatchScore.clamp(score.semanticScore()),
                            parseConfidence(score.confidence()),
                            score.matchedSkills() == null ? List.of() : score.matchedSkills(),
                            score.rationale() == null || score.rationale().isBlank()
                                    ? "No rationale provided."
                                    : score.rationale()))
                    .toList();

            if (scores.isEmpty()) {
                throw new AiMatchingException("Groq returned no usable project scores");
            }

            log.info("Groq scored {} of {} shortlisted projects for student {}",
                    scores.size(), projects.size(), studentUserId);
            return scores;
        } catch (AiMatchingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiMatchingException("Failed to parse Groq project response", e);
        }
    }

    private String callGroq(String prompt, String subject) {
        try {
            String response = groqApiService.analyzeBatchMatch(prompt).block();
            if (response == null || response.isBlank()) {
                throw new AiMatchingException("Groq returned an empty response for " + subject);
            }
            return response;
        } catch (AiMatchingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiMatchingException("Groq call failed for " + subject, e);
        }
    }

    private RecommendationConfidence parseConfidence(String value) {
        if (value == null || value.isBlank()) {
            return RecommendationConfidence.LOW;
        }
        try {
            return RecommendationConfidence.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RecommendationConfidence.LOW;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
