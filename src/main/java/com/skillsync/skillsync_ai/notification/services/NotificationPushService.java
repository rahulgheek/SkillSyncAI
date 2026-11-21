package com.skillsync.skillsync_ai.notification.services;

import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes user-scoped notifications over the single STOMP connection.
 *
 * <p>STOMP authenticates the connection with the user's email (see
 * {@code AuthChannelInterceptor}), so {@link SimpMessagingTemplate#convertAndSendToUser}
 * must be addressed by email rather than by UUID. The email is cached because
 * notification fan-out can happen in a background thread and an extra database
 * round trip to Singapore is expensive.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private static final int MAX_CACHE_ENTRIES = 10_000;
    private static final String NOTIFICATION_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private final Map<UUID, String> emailCache = new ConcurrentHashMap<>();

    public void push(UUID userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }

        String email = resolveEmail(userId);
        if (email == null) {
            log.debug("Skipping notification push: no email found for user {}", userId);
            return;
        }

        try {
            messagingTemplate.convertAndSendToUser(email, NOTIFICATION_DESTINATION, payload);
        } catch (Exception e) {
            // A failed push must never fail the business transaction that
            // produced the notification. The REST endpoint remains the source
            // of truth and the client re-syncs after every reconnect.
            log.warn("Failed to push notification to user {}: {}", userId, e.getMessage());
        }
    }

    private String resolveEmail(UUID userId) {
        String cached = emailCache.get(userId);
        if (cached != null) {
            return cached;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    if (emailCache.size() >= MAX_CACHE_ENTRIES) {
                        emailCache.clear();
                    }
                    emailCache.put(userId, user.getEmail());
                    return user.getEmail();
                })
                .orElse(null);
    }

    /**
     * Drops a cached address when an account changes its email. Exposed so the
     * authentication module can call it if email updates are added later.
     */
    public void evict(UUID userId) {
        emailCache.remove(userId);
    }
}
