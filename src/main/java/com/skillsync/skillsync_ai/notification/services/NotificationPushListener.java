package com.skillsync.skillsync_ai.notification.services;

import com.skillsync.skillsync_ai.notification.events.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends notifications over STOMP after the notification row has committed.
 * This replaces the old SSE emitter registry: one transport, one connection
 * pool, one authentication lifecycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final NotificationPushService notificationPushService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationPushService.push(event.userId(), event.notification());
    }
}
