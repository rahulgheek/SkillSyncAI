package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.CompatibilityBatchScoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSkillGapAnalyzer implements AiMatchingGateway {

    private final GeminiApiService geminiApiService;
    private final RecommendationPromptFactory promptFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // Strict record mapping to replace Maps
    public record GeminiCandidateScore(UUID userId, java.math.BigDecimal semanticScore, String confidence,List<String> matchedSkills, String rationale) {}

    @Override
    @org.springframework.scheduling.annotation.Async
    public void analyzeCandidatesBatch(UUID projectId, String requiredSkillsJson, List<CandidateProfileForAi> candidates) {
        log.info("AI Gateway processing batch of {} candidates for Project: {}", candidates.size(), projectId);

        try {
            log.info("STEP 1 - Prompt created");
            String prompt = promptFactory.buildBatchMatchPrompt(requiredSkillsJson, candidates);

            log.info("Candidate count = {}", candidates.size());
            log.info("Prompt length = {}", prompt.length());

            log.info("STEP 2 - Calling Gemini");
            var geminiResponse = geminiApiService.analyzeBatchSkillGap(prompt).block();
            log.info("STEP 3 - Gemini returned");

            if (geminiResponse == null || geminiResponse.getExtractedText() == null || geminiResponse.getExtractedText().isBlank()) {
                log.error("Gemini batch analysis returned empty response.");
                return;
            }
            
            log.info("Response = {}", geminiResponse.getExtractedText());

            log.info("STEP 4 - Parsing JSON");
            // Strictly parse into the record list
            List<GeminiCandidateScore> parsedScores = objectMapper.readValue(
                    geminiResponse.getExtractedText(),
                    new TypeReference<List<GeminiCandidateScore>>() {}
            );
            log.info("STEP 5 - Parsed");

            List<CompatibilityBatchScoredEvent.IndividualCandidateScore> mappedScores = parsedScores.stream()
                    .map(score -> new CompatibilityBatchScoredEvent.IndividualCandidateScore(
                            score.userId(),
                            score.semanticScore() != null ? score.semanticScore() : java.math.BigDecimal.ZERO,
                            score.confidence() != null ? com.skillsync.skillsync_ai.common.enums.RecommendationConfidence.valueOf(score.confidence().toUpperCase()) : com.skillsync.skillsync_ai.common.enums.RecommendationConfidence.LOW,
                            score.matchedSkills() != null ? score.matchedSkills() : java.util.Collections.emptyList(),
                            score.rationale() != null ? score.rationale() : "No rationale provided."))
                    .toList();

            eventPublisher.publishEvent(new CompatibilityBatchScoredEvent(
                    projectId,
                    RecommendationPromptFactory.CURRENT_VERSION,
                    java.time.Instant.now(), // Pass the generation timestamp
                    mappedScores
            ));
            
            log.info("STEP 6 - Event published");

        } catch (Exception e) {
            log.error("Failed to execute batch AI matching for project {}", projectId, e);
        }
    }
}