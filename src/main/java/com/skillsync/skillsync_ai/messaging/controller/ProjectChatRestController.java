package com.skillsync.skillsync_ai.messaging.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.messaging.dto.ProjectChatMessage;
import com.skillsync.skillsync_ai.messaging.services.ProjectChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/messages")
@RequiredArgsConstructor
public class ProjectChatRestController {

    private final ProjectChatService chatService;

    // The REST endpoint to load history when the user opens the chat UI
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectChatMessage>>> getChatHistory(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("Chat history retrieved",
                chatService.getChatHistory(projectId, currentUser.getId(), pageable)));
    }
}