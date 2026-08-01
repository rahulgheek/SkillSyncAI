package com.skillsync.skillsync_ai.messaging.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.messaging.dto.ChatInboxItemDto;
import com.skillsync.skillsync_ai.messaging.dto.DirectMessageDto;
import com.skillsync.skillsync_ai.messaging.services.DirectMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class DirectMessageRestController {

    private final DirectMessageService dmService;

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<ChatInboxItemDto>>> getInbox(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Inbox retrieved",
                dmService.getUserInbox(currentUser.getId())));
    }

    @GetMapping("/direct/{otherUserId}")
    public ResponseEntity<ApiResponse<Page<DirectMessageDto>>> getDirectChatHistory(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID otherUserId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("DM history retrieved",
                dmService.getDirectChatHistory(currentUser.getId(), otherUserId, pageable)));
    }

    @PostMapping("/direct/{otherUserId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID otherUserId) {
        dmService.markMessagesAsRead(currentUser.getId(), otherUserId);
        return ResponseEntity.ok(ApiResponse.success("Messages marked as read", null));
    }
}
