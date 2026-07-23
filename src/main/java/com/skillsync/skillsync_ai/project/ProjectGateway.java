package com.skillsync.skillsync_ai.project;

import java.util.UUID;

public interface ProjectGateway {
    ProjectMatchingContext getProjectContext(UUID projectId);

    record ProjectMatchingContext(UUID projectId, UUID ownerId, UUID collegeId) {}

    boolean isUserActiveMember(UUID projectId, UUID userId);
}