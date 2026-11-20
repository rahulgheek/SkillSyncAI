package com.skillsync.skillsync_ai.messaging.services;

import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import com.skillsync.skillsync_ai.common.events.DirectMessageSentEvent;
import com.skillsync.skillsync_ai.messaging.dto.ChatInboxItemDto;
import com.skillsync.skillsync_ai.messaging.dto.DirectMessageDto;
import com.skillsync.skillsync_ai.messaging.entity.DirectMessage;
import com.skillsync.skillsync_ai.messaging.repo.DirectMessageRepository;
import com.skillsync.skillsync_ai.messaging.repo.ProjectMessageRepository;
import com.skillsync.skillsync_ai.project.ProjectGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
    private final ProjectMessageRepository projectMessageRepository;
    private final UserProfileGateway userProfileGateway;
    private final ProjectGateway projectGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DirectMessageDto processAndSaveMessage(UUID senderId, UUID recipientId, String content) {
        // We can add logic to ensure users can message each other (e.g. one applied to the other's project)
        // For now, allow any DM as requested: "anyone is able to send the msg and also i want person to send the msg even before the applying"

        DirectMessage savedMessage = directMessageRepository.save(DirectMessage.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .content(content)
                .build());

        String preview = content.length() > 50 ? content.substring(0, 47) + "..." : content;
        eventPublisher.publishEvent(new DirectMessageSentEvent(
                savedMessage.getId(),
                senderId,
                recipientId,
                preview
        ));

        return mapToDto(savedMessage);
    }

    @Transactional(readOnly = true)
    public Page<DirectMessageDto> getDirectChatHistory(UUID currentUserId, UUID otherUserId, Pageable pageable) {
        Map<UUID, com.skillsync.skillsync_ai.userprofile.UserProfileGateway.UserProfileContext> profiles =
                userProfileGateway.getUserProfiles(List.of(currentUserId, otherUserId)).stream()
                        .collect(Collectors.toMap(
                                com.skillsync.skillsync_ai.userprofile.UserProfileGateway.UserProfileContext::userId,
                                p -> p
                        ));

        return directMessageRepository.findConversationHistory(currentUserId, otherUserId, pageable)
                .map(msg -> {
                    var profile = profiles.get(msg.getSenderId());
                    String name = profile != null ? profile.fullName() : "Unknown User";
                    String avatar = profile != null ? profile.profilePictureUrl() : null;
                    return new DirectMessageDto(
                            msg.getId(),
                            msg.getSenderId(),
                            name,
                            avatar,
                            msg.getRecipientId(),
                            msg.getContent(),
                            msg.getCreatedAt(),
                            msg.isRead()
                    );
                });
    }

    @Transactional
    public void markMessagesAsRead(UUID currentUserId, UUID senderId) {
        directMessageRepository.markConversationAsRead(currentUserId, senderId);
    }

    @Transactional(readOnly = true)
    public List<ChatInboxItemDto> getUserInbox(UUID userId) {
        List<ChatInboxItemDto> inbox = new ArrayList<>();

        // 1. Projects: one query for the projects, one for the latest message
        // of every project (instead of one query per project).
        List<ProjectGateway.ProjectChatContext> activeProjects = projectGateway.getUserActiveProjects(userId);
        if (!activeProjects.isEmpty()) {
            List<UUID> projectIds = activeProjects.stream()
                    .map(ProjectGateway.ProjectChatContext::projectId)
                    .toList();
            Map<UUID, com.skillsync.skillsync_ai.messaging.entity.ProjectMessage> latestByProject =
                    projectMessageRepository.findLatestByProjectIds(projectIds).stream()
                            .collect(Collectors.toMap(
                                    com.skillsync.skillsync_ai.messaging.entity.ProjectMessage::getProjectId,
                                    m -> m
                            ));

            for (ProjectGateway.ProjectChatContext projectCtx : activeProjects) {
                var latest = latestByProject.get(projectCtx.projectId());
                String lastMsg = latest == null ? "" : latest.getContent();
                LocalDateTime lastMsgAt = latest == null ? projectCtx.createdAt()
                        : java.time.LocalDateTime.ofInstant(latest.getCreatedAt(), java.time.ZoneId.systemDefault());

                inbox.add(new ChatInboxItemDto(
                        projectCtx.projectId().toString(),
                        "PROJECT",
                        projectCtx.title(),
                        null,
                        lastMsg,
                        lastMsgAt,
                        0
                ));
            }
        }

        // 2. Direct messages: one query for the recent messages, one for all
        // profiles, one for unread counts. No per-conversation lookups.
        List<DirectMessage> recentMessages = directMessageRepository
                .findRecentMessagesForUser(userId, org.springframework.data.domain.PageRequest.of(0, 500));

        Map<UUID, DirectMessage> latestByInterlocutor = new LinkedHashMap<>();
        for (DirectMessage message : recentMessages) {
            UUID otherId = message.getSenderId().equals(userId) ? message.getRecipientId() : message.getSenderId();
            latestByInterlocutor.putIfAbsent(otherId, message);
        }

        if (!latestByInterlocutor.isEmpty()) {
            List<UUID> otherIds = new ArrayList<>(latestByInterlocutor.keySet());

            Map<UUID, com.skillsync.skillsync_ai.userprofile.UserProfileGateway.UserProfileContext> profiles =
                    userProfileGateway.getUserProfiles(otherIds).stream()
                            .collect(Collectors.toMap(
                                    com.skillsync.skillsync_ai.userprofile.UserProfileGateway.UserProfileContext::userId,
                                    p -> p
                            ));

            Map<UUID, Long> unreadBySender = directMessageRepository.countUnreadBySender(userId).stream()
                    .collect(Collectors.toMap(
                            com.skillsync.skillsync_ai.messaging.repo.DirectMessageRepository.UnreadCountProjection::getSenderId,
                            com.skillsync.skillsync_ai.messaging.repo.DirectMessageRepository.UnreadCountProjection::getUnreadCount
                    ));

            for (Map.Entry<UUID, DirectMessage> entry : latestByInterlocutor.entrySet()) {
                UUID otherId = entry.getKey();
                var otherUser = profiles.get(otherId);
                if (otherUser == null) {
                    continue;
                }
                DirectMessage lastMessage = entry.getValue();
                inbox.add(new ChatInboxItemDto(
                        otherId.toString(),
                        "DIRECT",
                        otherUser.fullName(),
                        otherUser.profilePictureUrl(),
                        lastMessage.getContent(),
                        lastMessage.getCreatedAt(),
                        unreadBySender.getOrDefault(otherId, 0L).intValue()
                ));
            }
        }

        // Sort by most recent
        inbox.sort(Comparator.comparing(ChatInboxItemDto::lastMessageAt).reversed());
        return inbox;
    }

    private DirectMessageDto mapToDto(DirectMessage msg) {
        var profile = userProfileGateway.getUserProfile(msg.getSenderId()).orElse(null);
        String name = profile != null ? profile.fullName() : "Unknown User";
        String avatar = profile != null ? profile.profilePictureUrl() : null;

        return new DirectMessageDto(
                msg.getId(),
                msg.getSenderId(),
                name,
                avatar,
                msg.getRecipientId(),
                msg.getContent(),
                msg.getCreatedAt(),
                msg.isRead()
        );
    }
}
