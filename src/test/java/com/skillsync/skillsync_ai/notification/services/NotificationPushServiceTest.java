package com.skillsync.skillsync_ai.notification.services;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPushServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    private NotificationPushService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPushService(messagingTemplate, userRepository);
    }

    @Test
    void push_addressesTheStompPrincipalByEmail() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("student@example.com")
                .build();
        Object payload = new Object();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.push(userId, payload);

        verify(messagingTemplate).convertAndSendToUser(
                "student@example.com", "/queue/notifications", payload);
    }

    @Test
    void push_cachesTheEmailAcrossNotifications() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("student@example.com")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.push(userId, "first");
        service.push(userId, "second");

        verify(userRepository, times(1)).findById(userId);
        verify(messagingTemplate).convertAndSendToUser(
                "student@example.com", "/queue/notifications", "first");
        verify(messagingTemplate).convertAndSendToUser(
                "student@example.com", "/queue/notifications", "second");
    }

    @Test
    void push_unknownUserDoesNotSend() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.push(userId, "payload");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void push_nullPayloadIsIgnored() {
        service.push(UUID.randomUUID(), null);

        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(messagingTemplate);
    }
}
