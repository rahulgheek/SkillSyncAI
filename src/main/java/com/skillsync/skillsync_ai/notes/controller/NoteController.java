package com.skillsync.skillsync_ai.notes.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.notes.dto.NoteRequest;
import com.skillsync.skillsync_ai.notes.dto.NoteResponse;
import com.skillsync.skillsync_ai.notes.services.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NoteResponse>>> getMyNotes(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) UUID projectId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("Notes retrieved successfully",
                noteService.getUserNotes(currentUser.getId(), projectId, pageable)));
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<ApiResponse<NoteResponse>> getNote(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID noteId) {

        return ResponseEntity.ok(ApiResponse.success("Note retrieved successfully",
                noteService.getNoteById(currentUser.getId(), noteId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody NoteRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created successfully",
                        noteService.createNote(currentUser.getId(), request)));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID noteId,
            @Valid @RequestBody NoteRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Note updated successfully",
                noteService.updateNote(currentUser.getId(), noteId, request)));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID noteId) {

        noteService.deleteNote(currentUser.getId(), noteId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted successfully", null));
    }

    @PostMapping("/{noteId}/summarize")
    public ResponseEntity<ApiResponse<Void>> requestSummarization(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID noteId) {

        noteService.requestSummarization(currentUser.getId(), noteId);

        // 202 ACCEPTED is the perfect HTTP code for an async background job!
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Summarization queued successfully. It will appear shortly.", null));
    }
}