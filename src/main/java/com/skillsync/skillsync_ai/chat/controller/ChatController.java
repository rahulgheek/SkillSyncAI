package com.skillsync.skillsync_ai.chat.controller;

import com.skillsync.skillsync_ai.chat.dto.ChatMessageResponse;
import com.skillsync.skillsync_ai.chat.dto.ChatRequest;
import com.skillsync.skillsync_ai.chat.dto.ConversationResponse;
import com.skillsync.skillsync_ai.chat.dto.RenameChatRequest;
import com.skillsync.skillsync_ai.chat.services.ChatService;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved",
                chatService.getUserConversations(userId, PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @AuthenticationPrincipal(expression = "id") UUID userId) {

        return ResponseEntity.ok(ApiResponse.success("Conversation created",
                chatService.createConversation(userId)));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getChatHistory(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success("History retrieved",
                chatService.getChatHistory(userId, conversationId, PageRequest.of(page, size))));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<String>> sendMessage(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ChatRequest request) {

        String aiResponse = chatService.sendMessage(userId, conversationId, request.message());
        return ResponseEntity.ok(ApiResponse.success("Message processed", aiResponse));
    }

    @PutMapping("/{conversationId}/title")
    public ResponseEntity<ApiResponse<Void>> renameConversation(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody RenameChatRequest request) {

        chatService.renameConversation(userId, conversationId, request.title());
        return ResponseEntity.ok(ApiResponse.success("Conversation renamed", null));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID conversationId) {

        chatService.deleteConversation(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted", null));
    }
}