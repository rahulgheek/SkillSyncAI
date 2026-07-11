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

    @PatchMapping("/checkpoints/{checkpointId}/status")
    public ResponseEntity<ApiResponse<Void>> updateCheckpointStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID checkpointId,
            @RequestParam CheckpointStatus status) {
        roadmapService.updateCheckpointStatus(currentUser.getId(), checkpointId, status);
        return ResponseEntity.ok(ApiResponse.success("Checkpoint status updated", null));
    }
}