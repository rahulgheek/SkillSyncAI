package com.skillsync.skillsync_ai.notification.events;

import com.skillsync.skillsync_ai.notification.dto.NotificationResponse;

import java.util.UUID;

/**
 * Published after a notification has been persisted. The push listener sends
 * it over STOMP only after the surrounding transaction commits, so a rollback
 * can never deliver a notification that does not exist in the database.
 */
public record NotificationCreatedEvent(
        UUID userId,
        NotificationResponse notification
) {
}
