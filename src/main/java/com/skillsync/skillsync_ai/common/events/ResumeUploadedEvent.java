package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// Fired by userprofile module, consumed by ai module
public record ResumeUploadedEvent(
        UUID studentProfileId,
        String resumeUrl
) {}