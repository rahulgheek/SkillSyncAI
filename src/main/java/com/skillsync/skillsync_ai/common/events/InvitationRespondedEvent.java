package com.skillsync.skillsync_ai.common.events;

import java.util.UUID;

public record InvitationRespondedEvent(
        UUID invitationId,
        UUID projectOwnerId,
        UUID studentUserId,
        String studentName,
        UUID projectId,
        String projectTitle,
        boolean accepted
) {}
