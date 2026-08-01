package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record InvitationSentEvent(
        UUID invitedUserId,
        UUID projectId,
        UUID inviterId,
        String inviterName,
        String projectTitle
) {
}
