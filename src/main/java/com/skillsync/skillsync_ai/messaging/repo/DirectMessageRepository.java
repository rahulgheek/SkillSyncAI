package com.skillsync.skillsync_ai.messaging.repo;

import com.skillsync.skillsync_ai.messaging.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    @Query("SELECT m FROM DirectMessage m WHERE (m.senderId = :user1 AND m.recipientId = :user2) OR (m.senderId = :user2 AND m.recipientId = :user1) ORDER BY m.createdAt DESC")
    Page<DirectMessage> findConversationHistory(@Param("user1") UUID user1, @Param("user2") UUID user2, Pageable pageable);

    @Query(value = "SELECT DISTINCT CASE WHEN sender_id = :userId THEN recipient_id ELSE sender_id END FROM messaging.direct_messages WHERE sender_id = :userId OR recipient_id = :userId", nativeQuery = true)
    List<UUID> findRecentInterlocutors(@Param("userId") UUID userId);

    @Query("SELECT m FROM DirectMessage m WHERE m.recipientId = :userId AND m.isRead = false")
    List<DirectMessage> countUnreadMessagesForUser(@Param("userId") UUID userId);
}
