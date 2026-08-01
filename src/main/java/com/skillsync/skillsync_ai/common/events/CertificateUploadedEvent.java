package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

// Fired by userprofile module, consumed by notification module
public record CertificateUploadedEvent(
        UUID userId,
        String title,
        String certificateUrl
) {}
