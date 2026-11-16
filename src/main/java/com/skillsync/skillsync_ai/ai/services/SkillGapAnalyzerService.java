package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.CandidateBatchReadyForAiEvent;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGapAnalyzerService {

    private final SingleCandidateGapProcessor gapProcessor;

    // NOT @ApplicationModuleListener: that annotation starts a REQUIRES_NEW
    // transaction, which would wrap every Gemini batch call and keep a Neon
    // connection open for the whole analysis. This listener runs after the
    // publishing transaction commits, and SingleCandidateGapProcessor opens
    // one short transaction per candidate result.
    @Async
    @TransactionalEventListener
    public void onCandidateBatchReady(CandidateBatchReadyForAiEvent event) {
        log.info("AI Module received {} candidates to score for Project ID: {}", event.candidates().size(), event.projectId());

        int batchSize = 25;
        for (int i = 0; i < event.candidates().size(); i += batchSize) {
            int end = Math.min(i + batchSize, event.candidates().size());
            java.util.List<CandidateProfileForAi> batch = event.candidates().subList(i, end);
            
            gapProcessor.processCandidateBatch(batch, event.projectId(), event.requiredSkillsJson());
        }

        log.info("Batch AI processing completed for Project ID: {}", event.projectId());
    }
}