package com.skillsync.skillsync_ai.notification.services;

import com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent;
import com.skillsync.skillsync_ai.common.events.ResumeAnalysisCompletedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent;
import com.skillsync.skillsync_ai.notification.entity.Notification;
import com.skillsync.skillsync_ai.notification.entity.enums.NotificationType;
import com.skillsync.skillsync_ai.notification.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;

    // --- The DRY Helper Method ---
    private void createNotification(UUID userId, NotificationType type, String title, String message, UUID relatedEntityId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityId(relatedEntityId)
                .build();
        notificationRepository.save(notification);
    }

    // --- The Listeners ---
    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onResumeAnalyzed(ResumeAnalysisCompletedEvent event) {
        log.info("Creating notification for AI Resume Analysis: {}", event.userId());
        createNotification(
                event.userId(),
                NotificationType.RESUME_ANALYZED,
                NotificationTemplates.TITLE_RESUME_ANALYZED,
                NotificationTemplates.messageResumeAnalyzed(event.extractedSkills().size()),
                null
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeamMemberAccepted(TeamMemberAcceptedEvent event) {
        log.info("Creating notification for Team Member Accepted on Project: {}", event.projectId());
        createNotification(
                event.userId(),
                NotificationType.APPLICATION_DECIDED,
                NotificationTemplates.TITLE_WELCOME_TEAM,
                NotificationTemplates.messageWelcomeTeam(),
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectPublished(ProjectPublishedEvent event) {
        // Assuming we add ownerId to ProjectPublishedEvent in the future.
        // For now, this is how clean it looks when we do:
        /*
        createNotification(
                event.ownerId(),
                NotificationType.PROJECT_PUBLISHED,
                NotificationTemplates.TITLE_PROJECT_PUBLISHED,
                NotificationTemplates.messageProjectPublished(event.projectTitle()),
                event.projectId()
        );
        */
    }
}