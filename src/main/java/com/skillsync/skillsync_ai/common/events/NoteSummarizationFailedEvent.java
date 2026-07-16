package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record NoteSummarizationFailedEvent(UUID noteId, UUID ownerId, String reason) {}