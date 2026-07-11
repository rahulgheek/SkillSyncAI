package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.NoteSummarizationCompletedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationFailedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteSummarizationListener {

    private final GeminiApiService geminiApiService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_CHAR_LIMIT = 15000; // Token Protection

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNoteSummarizationRequested(NoteSummarizationRequestedEvent event) {
        try {
            // Truncate massively long notes to protect Gemini API limits
            String contentToProcess = event.content();
            if (contentToProcess.length() > MAX_CHAR_LIMIT) {
                log.warn("Note {} exceeded limits. Truncating to {} chars.", event.noteId(), MAX_CHAR_LIMIT);
                contentToProcess = contentToProcess.substring(0, MAX_CHAR_LIMIT);
            }

            var geminiResponse = geminiApiService.summarizeNote(contentToProcess).block();

            if (geminiResponse == null || geminiResponse.getResponseText() == null || geminiResponse.getResponseText().isBlank()) {
                throw new RuntimeException("Gemini returned an empty response.");
            }

            Map<String, Object> parsedData = geminiApiService.parseJsonToMap(geminiResponse.getResponseText());
            String summary = (String) parsedData.get("summary");

            // Success
            eventPublisher.publishEvent(new NoteSummarizationCompletedEvent(event.noteId(), event.ownerId(), summary));

        } catch (Exception e) {
            // Failure
            eventPublisher.publishEvent(new NoteSummarizationFailedEvent(event.noteId(), event.ownerId(), e.getMessage()));
        }
    }
}