package com.skillsync.skillsync_ai.project;

import java.util.UUID;

public interface ProjectGateway {
    ProjectMatchingContext getProjectContext(UUID projectId);

    record ProjectMatchingContext(UUID projectId, UUID ownerId, UUID collegeId) {}
    
    record ProjectChatContext(UUID projectId, String title, java.time.LocalDateTime createdAt) {}

    boolean isUserActiveMember(UUID projectId, UUID userId);
    
    java.util.List<ProjectChatContext> getUserActiveProjects(UUID userId);
    
    record ProjectDiscoveryContext(UUID projectId, String title, String description) {}
    ProjectDiscoveryContext getProjectDiscoveryContext(UUID projectId);

    java.util.List<ProjectDiscoveryContext> getProjectDiscoveryContexts(java.util.Collection<UUID> projectIds);
}