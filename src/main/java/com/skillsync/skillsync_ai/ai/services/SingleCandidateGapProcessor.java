package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.entity.SkillGapItem;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.CompatibilityScoreComputedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SingleCandidateGapProcessor {

    private final GeminiApiService geminiApiService;
    private final SkillGapAnalysisRepository skillGapRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final GeminiApiService.RecommendationPromptFactory promptFactory;

    // --- NEW: Recommendation Engine Threshold ---
    private static final double MINIMUM_VIABLE_MATCH_PERCENTAGE = 40.0;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCandidateBatch(List<CandidateProfileForAi> candidates, UUID projectId, String requiredSkillsJson) {
        if (candidates == null || candidates.isEmpty()) return;
        
        try {
            String prompt = promptFactory.buildProjectSkillGapBatchPrompt(requiredSkillsJson, candidates);
            var geminiResponse = geminiApiService.analyzeSkillGapForBatch(prompt).block();

            if (geminiResponse == null || geminiResponse.getExtractedText() == null || geminiResponse.getExtractedText().isBlank()) {
                log.warn("Gemini returned an empty/null response for batch on Project: {}", projectId);
                return;
            }

            List<Map<String, Object>> parsedDataList = geminiApiService.parseJsonToList(geminiResponse.getExtractedText());

            for (Map<String, Object> parsedData : parsedDataList) {
                try {
                    UUID userId = UUID.fromString((String) parsedData.get("userId"));
                    BigDecimal matchPercentage = BigDecimal.valueOf(((Number) parsedData.get("matchPercentage")).doubleValue());

                    if (matchPercentage.doubleValue() < MINIMUM_VIABLE_MATCH_PERCENTAGE) {
                        log.info("Candidate {} scored {}%. Below threshold. Discarding recommendation.", userId, matchPercentage);
                        continue; 
                    }

                    SkillGapAnalysis gapAnalysis = skillGapRepository.findByUserIdAndProjectId(userId, projectId)
                            .orElse(SkillGapAnalysis.builder()
                                    .userId(userId)
                                    .projectId(projectId)
                                    .build());

                    CandidateProfileForAi matchingCandidate = candidates.stream()
                            .filter(c -> c.userId().equals(userId))
                            .findFirst()
                            .orElse(null);

                    if (matchingCandidate != null) {
                        gapAnalysis.setStudentProfileId(matchingCandidate.studentProfileId());
                    }

                    gapAnalysis.setMatchPercentage(matchPercentage);
                    gapAnalysis.setMissingSkills(mapToSkillGapItems((List<Map<String, String>>) parsedData.get("missingSkills")));
                    gapAnalysis.setMatchedSkills(mapToSkillGapItems((List<Map<String, String>>) parsedData.get("matchedSkills")));

                    skillGapRepository.save(gapAnalysis);

                    eventPublisher.publishEvent(new CompatibilityScoreComputedEvent(
                            projectId, userId, matchPercentage, (String) parsedData.get("rationale")
                    ));
                } catch (Exception ex) {
                    log.error("Failed to process individual candidate in batch for project {}", projectId, ex);
                }
            }

        } catch (Exception e) {
            log.error("AI Skill Gap Analysis Batch FAILED for Project: {}.", projectId, e);
        }
    }

    private List<SkillGapItem> mapToSkillGapItems(List<Map<String, String>> rawList) {
        List<SkillGapItem> items = new ArrayList<>();
        if (rawList != null) {
            for (Map<String, String> map : rawList) {
                items.add(new SkillGapItem(map.get("skillName"), map.get("importance")));
            }
        }
        return items;
    }
}