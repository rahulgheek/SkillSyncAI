package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record NoteSummarizationCompletedEvent(UUID noteId, UUID ownerId, String aiSummary) {}