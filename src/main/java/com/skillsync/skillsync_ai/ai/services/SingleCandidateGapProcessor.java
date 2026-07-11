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

    // REQUIRES_NEW guarantees a brand new, isolated transaction for every single candidate
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCandidate(CandidateProfileForAi candidate, UUID projectId, String requiredSkillsJson) {
        try {
            var geminiResponse = geminiApiService.analyzeSkillGap(candidate.skills(), requiredSkillsJson).block();

            // GUARD: Check for null or completely empty text before attempting JSON parsing
            if (geminiResponse == null || geminiResponse.getResponseText() == null || geminiResponse.getResponseText().isBlank()) {
                log.warn("Gemini returned an empty/null response for User: {} on Project: {}", candidate.userId(), projectId);
                return;
            }

            Map<String, Object> parsedData = geminiApiService.parseJsonToMap(geminiResponse.getResponseText());
            BigDecimal matchPercentage = BigDecimal.valueOf(((Number) parsedData.get("matchPercentage")).doubleValue());

            SkillGapAnalysis gapAnalysis = skillGapRepository.findByUserIdAndProjectId(candidate.userId(), projectId)
                    .orElse(SkillGapAnalysis.builder()
                            .userId(candidate.userId())
                            .projectId(projectId)
                            .build());

            gapAnalysis.setMatchPercentage(matchPercentage);
            gapAnalysis.setMissingSkills(mapToSkillGapItems((List<Map<String, String>>) parsedData.get("missingSkills")));
            gapAnalysis.setMatchedSkills(mapToSkillGapItems((List<Map<String, String>>) parsedData.get("matchedSkills")));

            skillGapRepository.save(gapAnalysis);

            eventPublisher.publishEvent(new CompatibilityScoreComputedEvent(
                    projectId, candidate.userId(), matchPercentage, (String) parsedData.get("rationale")
            ));

        } catch (Exception e) {
            // FULL STACK TRACE LOGGING: Passing 'e' as the last parameter to log.error
            log.error("AI Skill Gap Analysis FAILED for User: {} on Project: {}.",
                    candidate.userId(), projectId, e);
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