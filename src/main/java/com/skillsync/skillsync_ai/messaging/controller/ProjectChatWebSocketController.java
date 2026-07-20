package com.skillsync.skillsync_ai.messaging.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.messaging.dto.ProjectChatMessage;
import com.skillsync.skillsync_ai.messaging.services.ProjectChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProjectChatWebSocketController {

    private final ProjectChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/projects/{projectId}/chat")
    public void processMessage(
            @DestinationVariable UUID projectId,
            @Payload ProjectChatMessage incomingMsg, // Standard DTO with 'content'
            Authentication authentication) {

        User sender = (User) authentication.getPrincipal();

        // Service handles security, persistence, and domain events
        ProjectChatMessage outgoingMsg = chatService.processAndSaveMessage(
                projectId, sender.getId(), incomingMsg.content()
        );

        // Broadcast to all valid subscribers
        messagingTemplate.convertAndSend("/topic/projects/" + projectId, outgoingMsg);
    }

    // Handles security exceptions gracefully over WebSockets
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable exception) {
        log.warn("WebSocket Error: {}", exception.getMessage());
        return exception.getMessage();
    }
}