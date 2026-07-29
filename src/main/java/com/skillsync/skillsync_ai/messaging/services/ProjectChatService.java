package com.skillsync.skillsync_ai.messaging.services;

import com.skillsync.skillsync_ai.common.events.ProjectMessageSentEvent;
import com.skillsync.skillsync_ai.common.exception.UnauthorizedActionException;
import com.skillsync.skillsync_ai.messaging.dto.ProjectChatMessage;
import com.skillsync.skillsync_ai.messaging.entity.ProjectMessage;
import com.skillsync.skillsync_ai.messaging.repo.ProjectMessageRepository;
import com.skillsync.skillsync_ai.project.ProjectGateway;
import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectChatService {

    private final ProjectMessageRepository messageRepository;
    private final ProjectGateway projectGateway;
    private final UserProfileGateway userProfileGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProjectChatMessage processAndSaveMessage(UUID projectId, UUID senderId, String content) {
        // 1. AUTHORIZATION: Is this user actually in this project?
        if (!projectGateway.isUserActiveMember(projectId, senderId)) {
            throw new UnauthorizedActionException("You are not an active member of this project.");
        }

        // 2. PERSISTENCE
        ProjectMessage savedMessage = messageRepository.save(ProjectMessage.builder()
                .projectId(projectId)
                .senderId(senderId)
                .content(content)
                .build());

        // 3. DOMAIN EVENT: Let other modules know (e.g., unread notifications)
        String preview = content.length() > 50 ? content.substring(0, 47) + "..." : content;
        eventPublisher.publishEvent(new ProjectMessageSentEvent(
                savedMessage.getId(), projectId, senderId, preview
        ));

        return mapToDto(savedMessage);
    }

    @Transactional(readOnly = true)
    public Page<ProjectChatMessage> getChatHistory(UUID projectId, UUID userId, Pageable pageable) {
        // 1. AUTHORIZATION: Only members can read history
        if (!projectGateway.isUserActiveMember(projectId, userId)) {
            throw new UnauthorizedActionException("You do not have permission to view this chat.");
        }

        return messageRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(this::mapToDto);
    }

    private ProjectChatMessage mapToDto(ProjectMessage msg) {
        var profile = userProfileGateway.getUserProfile(msg.getSenderId()).orElse(null);
        String name = profile != null ? profile.fullName() : "Unknown User";
        String avatar = profile != null ? profile.profilePictureUrl() : null;

        return new ProjectChatMessage(
                msg.getId(),
                msg.getProjectId(),
                msg.getSenderId(),
                name,
                avatar,
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}