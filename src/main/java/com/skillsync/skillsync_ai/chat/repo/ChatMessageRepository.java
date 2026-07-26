package com.skillsync.skillsync_ai.chat.repo;

import com.skillsync.skillsync_ai.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findAllByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    // Custom query for the AI context window
    List<ChatMessage> findTop30ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @org.springframework.data.jpa.repository.Query("SELECT cm FROM ChatMessage cm JOIN Conversation c ON cm.conversationId = c.id WHERE c.userId = :userId ORDER BY cm.createdAt DESC")
    List<ChatMessage> findRecentMessagesByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId, Pageable pageable);
}