package com.skillsync.skillsync_ai.notification.services;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseConnectionService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE Emitter completed for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE Emitter timed out for user: {}", userId);
            emitter.complete();
            emitters.remove(userId);
        });

        emitter.onError((e) -> {
            log.debug("SSE Emitter error for user: {}: {}", userId, e.getMessage());
            emitter.complete();
            emitters.remove(userId);
        });

        // Send a dummy event to keep the connection alive immediately
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE Connection Established"));
        } catch (IOException e) {
            log.warn("Could not send initial SSE connection event to user {}: {}", userId, e.getMessage());
            emitters.remove(userId);
        }

        return emitter;
    }

    public void sendEventToUser(UUID userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("Failed to send SSE event to user {}. Removing emitter.", userId);
                emitter.complete();
                emitters.remove(userId);
            }
        }
    }
}
