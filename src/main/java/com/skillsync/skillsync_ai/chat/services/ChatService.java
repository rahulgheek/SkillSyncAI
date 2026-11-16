package com.skillsync.skillsync_ai.chat.services;

import com.skillsync.skillsync_ai.ai.AiChatGateway;
import com.skillsync.skillsync_ai.chat.dto.ChatMessageResponse;
import com.skillsync.skillsync_ai.chat.dto.ConversationResponse;
import com.skillsync.skillsync_ai.chat.entity.ChatMessage;
import com.skillsync.skillsync_ai.chat.entity.Conversation;
import com.skillsync.skillsync_ai.chat.repo.ChatMessageRepository;
import com.skillsync.skillsync_ai.chat.repo.ConversationRepository;
import com.skillsync.skillsync_ai.common.dto.ChatTurn;
import com.skillsync.skillsync_ai.common.enums.MessageRole;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiChatGateway aiChatGateway;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(UUID userId, Pageable pageable) {
        return conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getUpdatedAt()));
    }

    @Transactional
    public ConversationResponse createConversation(UUID userId) {
        Conversation chat = conversationRepository.save(Conversation.builder().userId(userId).build());
        return new ConversationResponse(chat.getId(), chat.getTitle(), chat.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getChatHistory(UUID userId, UUID conversationId, Pageable pageable) {
        verifyOwnership(userId, conversationId);
        return messageRepository.findAllByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()));
    }

    @Transactional
    public void renameConversation(UUID userId, UUID conversationId, String newTitle) {
        Conversation conversation = verifyOwnership(userId, conversationId);
        conversation.setTitle(newTitle);
        conversationRepository.save(conversation);
    }

    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        Conversation conversation = verifyOwnership(userId, conversationId);
        conversationRepository.delete(conversation);
    }

    /**
     * The Gemini agent loop is an external call and can run for ~25 seconds.
     * It runs with no database transaction open: phase 1 loads the history and
     * saves the user message, phase 2 saves the model reply. Tool handlers
     * invoked during the AI turn open their own short read transactions.
     */
    public String sendMessage(UUID userId, UUID conversationId, String userText) {
        List<ChatTurn> history = transactionTemplate.execute(status -> {
            verifyOwnership(userId, conversationId);

            // 1. Fetch history BEFORE saving the new message (Fixes the double-message bug)
            var recentMessages = messageRepository.findAllByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(0, 30)).getContent();

            List<ChatMessage> keptMessages = new java.util.ArrayList<>(recentMessages);
            Collections.reverse(keptMessages); // Put back in chronological order

            List<ChatTurn> turns = keptMessages.stream()
                    .map(msg -> new ChatTurn(msg.getRole(), msg.getContent()))
                    .collect(Collectors.toList());

            // 2. Save User Message
            saveMessage(conversationId, MessageRole.USER, userText);

            return turns;
        });

        // 3. Fire the Agent without a transaction held for its duration
        String aiResponseText = aiChatGateway.processChatTurn(userId, history, userText);

        transactionTemplate.executeWithoutResult(status -> {
            // 4. Save Model Message
            saveMessage(conversationId, MessageRole.MODEL, aiResponseText);

            // 5. Update Conversation Timestamp (Fixes sorting issue)
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
        });

        return aiResponseText;
    }

    private Conversation verifyOwnership(UUID userId, UUID conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private void saveMessage(UUID conversationId, MessageRole role, String content) {
        messageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build());
    }
}