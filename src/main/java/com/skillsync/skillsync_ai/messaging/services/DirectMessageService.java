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
import java.util.List;
import java.util.UUID;

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
        var currentUserProfile = userProfileGateway.getUserProfile(currentUserId).orElse(null);
        var otherUserProfile = userProfileGateway.getUserProfile(otherUserId).orElse(null);

        return directMessageRepository.findConversationHistory(currentUserId, otherUserId, pageable)
                .map(msg -> {
                    var profile = msg.getSenderId().equals(currentUserId) ? currentUserProfile : otherUserProfile;
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
        // A simple query to update would be better, but doing it via JPA for simplicity
        List<DirectMessage> unread = directMessageRepository.countUnreadMessagesForUser(currentUserId);
        for (DirectMessage dm : unread) {
            if (dm.getSenderId().equals(senderId)) {
                dm.setRead(true);
            }
        }
        directMessageRepository.saveAll(unread);
    }

    @Transactional(readOnly = true)
    public List<ChatInboxItemDto> getUserInbox(UUID userId) {
        List<ChatInboxItemDto> inbox = new ArrayList<>();

        // 1. Get Projects the user is part of
        List<ProjectGateway.ProjectChatContext> activeProjects = projectGateway.getUserActiveProjects(userId);
        for (ProjectGateway.ProjectChatContext projectCtx : activeProjects) {
            // Get last message
            var lastMsgs = projectMessageRepository.findAllByProjectIdOrderByCreatedAtDesc(projectCtx.projectId(), Pageable.ofSize(1));
            String lastMsg = lastMsgs.isEmpty() ? "" : lastMsgs.getContent().get(0).getContent();
            LocalDateTime lastMsgAt = lastMsgs.isEmpty() ? projectCtx.createdAt() : java.time.LocalDateTime.ofInstant(lastMsgs.getContent().get(0).getCreatedAt(), java.time.ZoneId.systemDefault());
            
            inbox.add(new ChatInboxItemDto(
                    projectCtx.projectId().toString(),
                    "PROJECT",
                    projectCtx.title(),
                    null, // Project avatar if any
                    lastMsg,
                    lastMsgAt,
                    0 // Unread logic for projects can be complex, skip for now
            ));
        }

        // Fetch all unread messages for this user ONCE to avoid N+1 inside the loop
        List<DirectMessage> allUnread = directMessageRepository.countUnreadMessagesForUser(userId);

        // 2. Get Users the user has DM'd
        List<UUID> dmUserIds = directMessageRepository.findRecentInterlocutors(userId);
        for (UUID otherId : dmUserIds) {
            userProfileGateway.getUserProfile(otherId).ifPresent(otherUser -> {
                var lastMsgs = directMessageRepository.findConversationHistory(userId, otherId, Pageable.ofSize(1));
                String lastMsg = lastMsgs.isEmpty() ? "" : lastMsgs.getContent().get(0).getContent();
                LocalDateTime lastMsgAt = lastMsgs.isEmpty() ? LocalDateTime.MIN : lastMsgs.getContent().get(0).getCreatedAt();
                
                // Count unread from this user in-memory
                long unreadCount = allUnread.stream()
                        .filter(m -> m.getSenderId().equals(otherId))
                        .count();

                inbox.add(new ChatInboxItemDto(
                        otherId.toString(),
                        "DIRECT",
                        otherUser.fullName(),
                        otherUser.profilePictureUrl(),
                        lastMsg,
                        lastMsgAt,
                        (int) unreadCount
                ));
            });
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
