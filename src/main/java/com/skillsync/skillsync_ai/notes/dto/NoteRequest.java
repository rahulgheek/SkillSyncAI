package com.skillsync.skillsync_ai.notes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record NoteRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        String content,

        UUID projectId,

        boolean pinned,

        // Deep Validation: Max 10 tags, and EACH tag must be <= 30 chars
        @Size(max = 10, message = "Cannot exceed 10 tags per note")
        List<@NotBlank @Size(max = 30, message = "Individual tags cannot exceed 30 characters") String> tags
) {}