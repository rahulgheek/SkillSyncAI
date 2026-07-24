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

    @Override
    @Transactional(readOnly = true)
    public ChatMetrics getChatMetrics(UUID userId) { // CHANGED: Now matches ChatMetrics contract!
        // Let the database do the counting!
        long totalConversations = conversationRepository.countByUserId(userId);

        // CHANGED: Returning the new ChatMetrics record!
        return new ChatMetrics(totalConversations);
    }
}