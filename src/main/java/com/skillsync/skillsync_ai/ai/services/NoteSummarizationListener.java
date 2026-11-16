package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.events.NoteSummarizationCompletedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationFailedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteSummarizationListener {

    private final GeminiApiService geminiApiService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    private static final int MAX_CHAR_LIMIT = 15000; // Token Protection

    // NOT @ApplicationModuleListener: that annotation starts a REQUIRES_NEW
    // transaction, which would keep a Neon connection open for the entire
    // Gemini call. This listener runs after the note request transaction
    // commits, calls Gemini with no transaction, then publishes the result in
    // a short transaction so NoteService's transactional listener still fires.
    @Async
    @TransactionalEventListener
    public void onNoteSummarizationRequested(NoteSummarizationRequestedEvent event) {
        try {
            // Truncate massively long notes to protect Gemini API limits
            String contentToProcess = event.content();
            if (contentToProcess.length() > MAX_CHAR_LIMIT) {
                log.warn("Note {} exceeded limits. Truncating to {} chars.", event.noteId(), MAX_CHAR_LIMIT);
                contentToProcess = contentToProcess.substring(0, MAX_CHAR_LIMIT);
            }

            var geminiResponse = geminiApiService.summarizeNote(contentToProcess).block();

            // FIXED: Using getExtractedText() instead of getResponseText()
            if (geminiResponse == null || geminiResponse.getExtractedText() == null || geminiResponse.getExtractedText().isBlank()) {
                throw new RuntimeException("Gemini returned an empty response.");
            }

            // FIXED: Using getExtractedText() instead of getResponseText()
            Map<String, Object> parsedData = geminiApiService.parseJsonToMap(geminiResponse.getExtractedText());
            String summary = (String) parsedData.get("summary");

            // Success. Publish inside a short transaction so the
            // @ApplicationModuleListener in NoteService receives it.
            transactionTemplate.executeWithoutResult(status ->
                    eventPublisher.publishEvent(new NoteSummarizationCompletedEvent(
                            event.noteId(), event.ownerId(), summary)));

        } catch (Exception e) {
            // Failure. Same short-transaction rule as above.
            try {
                transactionTemplate.executeWithoutResult(status ->
                        eventPublisher.publishEvent(new NoteSummarizationFailedEvent(
                                event.noteId(), event.ownerId(), e.getMessage())));
            } catch (Exception publishError) {
                log.error("Failed to publish summarization failure for note {}", event.noteId(), publishError);
            }
        }
    }
}