package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.CandidateBatchReadyForAiEvent;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGapAnalyzerService {

    private final SingleCandidateGapProcessor gapProcessor;

    // No @Transactional here! We want the loop to stay open and manage individual transactions inside the processor.
    @ApplicationModuleListener
    public void onCandidateBatchReady(CandidateBatchReadyForAiEvent event) {
        log.info("AI Module received {} candidates to score for Project ID: {}", event.candidates().size(), event.projectId());

        for (CandidateProfileForAi candidate : event.candidates()) {
            // Each iteration delegates to a new method that spins up its own safe, isolated transaction
            gapProcessor.processCandidate(candidate, event.projectId(), event.requiredSkillsJson());
        }

        log.info("Batch AI processing completed for Project ID: {}", event.projectId());
    }
}