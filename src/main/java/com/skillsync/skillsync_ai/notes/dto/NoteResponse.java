package com.skillsync.skillsync_ai.notes.dto;

import com.skillsync.skillsync_ai.notes.entity.enums.SummaryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        String title,
        String content,
        String aiSummary,
        SummaryStatus summaryStatus,
        Instant summaryUpdatedAt, // NEW: Exported for frontend "outdated" comparison
        UUID projectId,
        boolean pinned,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}