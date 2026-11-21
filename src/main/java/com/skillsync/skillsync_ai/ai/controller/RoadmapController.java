package com.skillsync.skillsync_ai.ai.controller;

import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.services.RoadmapService;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roadmaps")
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @PostMapping("/generate/{skillGapId}")
    public ResponseEntity<ApiResponse<Roadmap>> generateRoadmap(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID skillGapId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("AI Roadmap generated successfully",
                        roadmapService.generateRoadmapFromGap(currentUser.getId(), skillGapId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Roadmap>> getActiveRoadmap(@AuthenticationPrincipal User currentUser) {
        Roadmap roadmap = roadmapService.getActiveRoadmap(currentUser.getId());
        if (roadmap == null) {
            return ResponseEntity.ok(ApiResponse.success("No active roadmap found", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Active roadmap retrieved", roadmap));
    }

    public record CustomRoadmapRequest(
            String targetRole,
            String currentKnowledge,
            String knowledgeGaps
    ) {}

    @PostMapping("/generate-custom")
    public ResponseEntity<ApiResponse<Roadmap>> generateCustomRoadmap(
            @AuthenticationPrincipal User currentUser,
            @RequestBody CustomRoadmapRequest request) {

        com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest sessionRequest =
                new com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest(
                        null,
                        request.targetRole(),
                        request.currentKnowledge(),
                        request.knowledgeGaps(),
                        null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Custom AI Roadmap generated successfully",
                        roadmapService.generateCustomRoadmap(currentUser.getId(), sessionRequest)));
    }

    public record RoadmapChatRequest(String message) {}
    public record RoadmapChatResponse(String reply) {}

    @PostMapping("/{roadmapId}/chat")
    public ResponseEntity<ApiResponse<RoadmapChatResponse>> chatWithRoadmap(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID roadmapId,
            @RequestBody RoadmapChatRequest request) {
        String reply = roadmapService.chatWithRoadmapAI(currentUser.getId(), roadmapId, request.message());
        return ResponseEntity.ok(ApiResponse.success("Success", new RoadmapChatResponse(reply)));
    }

    @PatchMapping("/checkpoints/{checkpointId}/status")
    public ResponseEntity<ApiResponse<Void>> updateCheckpointStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID checkpointId,
            @RequestParam CheckpointStatus status) {
        roadmapService.updateCheckpointStatus(currentUser.getId(), checkpointId, status);
        return ResponseEntity.ok(ApiResponse.success("Checkpoint status updated", null));
    }
}