package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

/**
 * Raised when a background matching job has produced (or refreshed) its
 * recommendations. The notification module turns this into a persisted
 * notification that is pushed over STOMP.
 *
 * @param scope "PROJECT" when the user owns a project whose candidate list is
 *              ready, or "STUDENT" when the user's project discovery feed is
 *              ready.
 */
public record RecommendationsReadyEvent(
        UUID userId,
        UUID projectId,
        String scope,
        int candidateCount,
        boolean aiApplied
) {
}
