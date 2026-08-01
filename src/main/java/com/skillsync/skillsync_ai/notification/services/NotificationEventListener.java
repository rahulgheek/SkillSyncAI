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
import org.springframework.kafka.annotation.KafkaListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;
import com.skillsync.skillsync_ai.common.events.CertificateUploadedEvent;
import com.skillsync.skillsync_ai.common.events.ApplicationSubmittedEvent;
import com.skillsync.skillsync_ai.common.events.InvitationSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final SseConnectionService sseConnectionService;
    private final ObjectMapper objectMapper;

    // --- The DRY Helper Method ---
    private void createNotification(UUID userId, NotificationType type, String title, String message, UUID relatedEntityId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityId(relatedEntityId)
                .build();
        Notification saved = notificationRepository.save(notification);
        
        // Dispatch to SSE if the user is connected
        try {
            sseConnectionService.sendEventToUser(userId, "notification", saved);
        } catch (Exception e) {
            log.warn("Failed to dispatch SSE for notification {}", saved.getId());
        }
    }

    // --- The Listeners ---
    @KafkaListener(topics = KafkaConfig.RESUME_ANALYZED_TOPIC, groupId = "notification-module-group")
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

    @KafkaListener(topics = KafkaConfig.CERTIFICATE_UPLOADED_TOPIC, groupId = "notification-module-group")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCertificateUploaded(CertificateUploadedEvent event) {
        try {
            log.info("Creating notification for Certificate Upload: {}", event.userId());
            createNotification(
                    event.userId(),
                    NotificationType.SYSTEM_ALERT, // Using existing type
                    "Certificate Uploaded",
                    "Your certificate for '" + event.title() + "' was successfully added to your profile.",
                    null
            );
        } catch (Exception e) {
            log.error("Failed to process CertificateUploadedEvent: {}", e.getMessage(), e);
        }
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

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        log.info("Creating notification for Application Submitted: {}", event.projectOwnerId());
        createNotification(
                event.projectOwnerId(),
                NotificationType.SYSTEM_ALERT, // or a new type like APPLICATION_RECEIVED
                "New Project Application",
                event.applicantName() + " has applied to your project '" + event.projectTitle() + "'.",
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvitationSent(InvitationSentEvent event) {
        log.info("Creating notification for Invitation Sent: {}", event.invitedUserId());
        createNotification(
                event.invitedUserId(),
                NotificationType.SYSTEM_ALERT, // or a new type like INVITATION_RECEIVED
                "New Project Invitation",
                event.inviterName() + " has invited you to join their project '" + event.projectTitle() + "'.",
                event.projectId()
        );
    }
}