package com.skillsync.skillsync_ai.notes.services;

import com.skillsync.skillsync_ai.common.events.NoteSummarizationCompletedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationFailedEvent;
import com.skillsync.skillsync_ai.common.events.NoteSummarizationRequestedEvent;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.notes.dto.NoteRequest;
import com.skillsync.skillsync_ai.notes.dto.NoteResponse;
import com.skillsync.skillsync_ai.notes.entity.Note;
import com.skillsync.skillsync_ai.notes.entity.enums.SummaryStatus;
import com.skillsync.skillsync_ai.notes.repo.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==========================================
    // STANDARD CRUD OPERATIONS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<NoteResponse> getUserNotes(UUID userId, UUID projectId, Pageable pageable) {
        if (projectId != null) {
            return noteRepository.findAllByOwnerUserIdAndProjectIdOrderByPinnedDescUpdatedAtDesc(userId, projectId, pageable)
                    .map(this::mapToResponse);
        }
        return noteRepository.findAllByOwnerUserIdOrderByPinnedDescUpdatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public NoteResponse getNoteById(UUID userId, UUID noteId) {
        return mapToResponse(getNoteSafely(userId, noteId));
    }

    @Transactional
    public NoteResponse createNote(UUID userId, NoteRequest request) {
        Note note = Note.builder()
                .ownerUserId(userId)
                .title(request.title())
                .content(request.content())
                .projectId(request.projectId())
                .pinned(request.pinned())
                .tags(request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>())
                .build();

        return mapToResponse(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse updateNote(UUID userId, UUID noteId, NoteRequest request) {
        Note note = getNoteSafely(userId, noteId);

        // Safely trim and compare using Java's built-in Objects utility
        String oldContent = note.getContent() == null ? null : note.getContent().trim();
        String newContent = request.content() == null ? null : request.content().trim();
        boolean contentChanged = !java.util.Objects.equals(oldContent, newContent);

        note.setTitle(request.title());
        note.setContent(request.content());
        note.setProjectId(request.projectId());
        note.setPinned(request.pinned());

        // Full PUT semantics: always clear, then add if provided
        note.getTags().clear();
        if (request.tags() != null) {
            note.getTags().addAll(request.tags());
        }

        // INVALIDATION LOGIC: Wipe the AI cache completely if the text was edited
        if (contentChanged) {
            note.setAiSummary(null);
            note.setSummaryStatus(SummaryStatus.IDLE);
            note.setSummaryUpdatedAt(null);
        }

        return mapToResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(UUID userId, UUID noteId) {
        Note note = getNoteSafely(userId, noteId);
        noteRepository.delete(note);
    }

    // ==========================================
    // AI SUMMARIZATION (MANUAL TRIGGER)
    // ==========================================

    @Transactional
    public void requestSummarization(UUID userId, UUID noteId) {
        Note note = getNoteSafely(userId, noteId);

        if (note.getContent() == null || note.getContent().isBlank()) {
            throw new IllegalArgumentException("Cannot summarize an empty note.");
        }

        // IDEMPOTENCY CHECK: Block spam-clicking
        if (note.getSummaryStatus() == SummaryStatus.PROCESSING) {
            throw new IllegalStateException("Summarization is already in progress.");
        }

        // Lock the state to prevent duplicate calls
        note.setSummaryStatus(SummaryStatus.PROCESSING);
        noteRepository.save(note);

        // Fire the upgraded event containing the ownerId
        eventPublisher.publishEvent(new NoteSummarizationRequestedEvent(
                note.getId(),
                note.getOwnerUserId(),
                note.getContent()
        ));
    }

    // ==========================================
    // MODULITH EVENT LISTENERS (CATCHING AI RESPONSE)
    // ==========================================

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNoteSummarized(NoteSummarizationCompletedEvent event) {
        log.info("Notes Module received AI Summary for Note ID: {}", event.noteId());

        noteRepository.findById(event.noteId()).ifPresent(note -> {
            note.setAiSummary(event.aiSummary());
            note.setSummaryStatus(SummaryStatus.COMPLETED);
            note.setSummaryUpdatedAt(Instant.now()); // Mark exactly when this was generated
            noteRepository.save(note);
        });
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNoteSummarizationFailed(NoteSummarizationFailedEvent event) {
        log.warn("AI Summarization failed for Note {}. Reason: {}", event.noteId(), event.reason());

        noteRepository.findById(event.noteId()).ifPresent(note -> {
            note.setSummaryStatus(SummaryStatus.FAILED);
            noteRepository.save(note);
        });
    }


    private Note getNoteSafely(UUID userId, UUID noteId) {
        return noteRepository.findByIdAndOwnerUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found or access denied."));
    }

    private NoteResponse mapToResponse(Note n) {
        return new NoteResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getAiSummary(),
                n.getSummaryStatus(),
                n.getSummaryUpdatedAt(),
                n.getProjectId(),
                n.isPinned(),
                n.getTags(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }
}