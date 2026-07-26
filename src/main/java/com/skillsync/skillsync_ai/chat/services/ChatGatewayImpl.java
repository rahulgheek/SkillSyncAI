package com.skillsync.skillsync_ai.chat.services; // CHANGED: singular 'service'

import com.skillsync.skillsync_ai.chat.ChatGateway;
import com.skillsync.skillsync_ai.chat.repo.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatGatewayImpl implements ChatGateway {

    private final ConversationRepository conversationRepository;
    private final com.skillsync.skillsync_ai.chat.repo.ChatMessageRepository chatMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public ChatMetrics getChatMetrics(UUID userId) { // CHANGED: Now matches ChatMetrics contract!
        // Let the database do the counting!
        long totalConversations = conversationRepository.countByUserId(userId);

        // CHANGED: Returning the new ChatMetrics record!
        return new ChatMetrics(totalConversations);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<String> getRecentChatHistory(UUID userId, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        return chatMessageRepository.findRecentMessagesByUserId(userId, pageable)
                .stream()
                .map(msg -> msg.getRole() + ": " + msg.getContent())
                .collect(java.util.stream.Collectors.toList());
    }
}