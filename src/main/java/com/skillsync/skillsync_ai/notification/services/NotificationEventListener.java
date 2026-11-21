package com.skillsync.skillsync_ai.notification.services;

import com.skillsync.skillsync_ai.common.events.ApplicationDecidedEvent;
import com.skillsync.skillsync_ai.common.events.InvitationRespondedEvent;
import com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent;
import com.skillsync.skillsync_ai.common.events.ProjectRecruitmentStatusChangedEvent;
import com.skillsync.skillsync_ai.common.events.RecommendationsReadyEvent;
import com.skillsync.skillsync_ai.common.events.ResumeAnalysisCompletedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberRemovedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberRoleChangedEvent;
import com.skillsync.skillsync_ai.notification.dto.NotificationResponse;
import com.skillsync.skillsync_ai.notification.entity.Notification;
import com.skillsync.skillsync_ai.notification.entity.enums.NotificationType;
import com.skillsync.skillsync_ai.notification.events.NotificationCreatedEvent;
import com.skillsync.skillsync_ai.notification.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.kafka.annotation.KafkaListener;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;
import com.skillsync.skillsync_ai.common.events.CertificateUploadedEvent;
import com.skillsync.skillsync_ai.common.events.ApplicationSubmittedEvent;
import com.skillsync.skillsync_ai.common.events.InvitationSentEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        // The push listener only fires after this transaction commits, so a
        // rollback never leaves a phantom notification on the client.
        eventPublisher.publishEvent(new NotificationCreatedEvent(userId, toResponse(saved)));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getRelatedEntityId(),
                notification.getCreatedAt()
        );
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
                NotificationType.TEAM_MEMBER_ADDED,
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
                NotificationType.APPLICATION_RECEIVED,
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
                NotificationType.INVITATION_RECEIVED,
                "New Project Invitation",
                event.inviterName() + " has invited you to join their project '" + event.projectTitle() + "'.",
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationDecided(ApplicationDecidedEvent event) {
        log.info("Creating notification for Application Decision: {}", event.applicationId());
        createNotification(
                event.applicantUserId(),
                NotificationType.APPLICATION_DECIDED,
                NotificationTemplates.TITLE_APPLICATION_DECIDED,
                NotificationTemplates.messageApplicationDecided(event.projectTitle(), event.accepted()),
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvitationResponded(InvitationRespondedEvent event) {
        log.info("Creating notification for Invitation Response: {}", event.invitationId());
        createNotification(
                event.projectOwnerId(),
                NotificationType.INVITATION_RESPONDED,
                NotificationTemplates.TITLE_INVITATION_RESPONDED,
                NotificationTemplates.messageInvitationResponded(
                        event.studentName(), event.projectTitle(), event.accepted()),
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeamMemberRemoved(TeamMemberRemovedEvent event) {
        log.info("Creating notification for Team Member Removed on Project: {}", event.projectId());
        createNotification(
                event.userId(),
                NotificationType.TEAM_MEMBER_REMOVED,
                NotificationTemplates.TITLE_MEMBER_REMOVED,
                NotificationTemplates.messageMemberRemoved(event.projectTitle(), event.removedByOwner()),
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeamMemberRoleChanged(TeamMemberRoleChangedEvent event) {
        log.info("Creating notification for Team Member Role Change on Project: {}", event.projectId());
        createNotification(
                event.userId(),
                NotificationType.TEAM_ROLE_CHANGED,
                NotificationTemplates.TITLE_ROLE_CHANGED,
                NotificationTemplates.messageRoleChanged(
                        event.projectTitle(), event.oldRoleTitle(), event.newRoleTitle()),
                event.projectId()
        );
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectRecruitmentStatusChanged(ProjectRecruitmentStatusChangedEvent event) {
        log.info("Creating notification for Project Recruitment Status on Project: {}", event.projectId());
        if (event.full()) {
            createNotification(
                    event.projectOwnerId(),
                    NotificationType.PROJECT_FULL,
                    NotificationTemplates.TITLE_PROJECT_FULL,
                    NotificationTemplates.messageProjectFull(event.projectTitle()),
                    event.projectId()
            );
        } else {
            createNotification(
                    event.projectOwnerId(),
                    NotificationType.PROJECT_REOPENED,
                    NotificationTemplates.TITLE_PROJECT_REOPENED,
                    NotificationTemplates.messageProjectReopened(event.projectTitle()),
                    event.projectId()
            );
        }
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecommendationsReady(RecommendationsReadyEvent event) {
        log.info("Creating notification for ready {} recommendations for user {}", event.scope(), event.userId());
        createNotification(
                event.userId(),
                NotificationType.MATCHING_READY,
                NotificationTemplates.TITLE_MATCHING_READY,
                NotificationTemplates.messageMatchingReady(event.candidateCount()),
                event.projectId()
        );
    }
}
