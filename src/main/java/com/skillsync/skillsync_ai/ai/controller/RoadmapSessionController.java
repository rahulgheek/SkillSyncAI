package com.skillsync.skillsync_ai.ai.controller;

import com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionDetailResponse;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionSummaryResponse;
import com.skillsync.skillsync_ai.ai.dto.UpdateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.services.RoadmapSessionService;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roadmaps/sessions")
@RequiredArgsConstructor
public class RoadmapSessionController {

    private final RoadmapSessionService roadmapSessionService;

    /**
     * Creates a new AI roadmap session. Supply {@code skillGapId} to build the
     * session from an existing skill-gap analysis, or use the free-text fields
     * to build a custom path.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RoadmapSessionDetailResponse>> createSession(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateRoadmapSessionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "AI roadmap session created successfully",
                        roadmapSessionService.createSession(currentUser.getId(), request)));
    }

    @PostMapping("/from-skill-gap/{skillGapId}")
    public ResponseEntity<ApiResponse<RoadmapSessionDetailResponse>> createSessionFromSkillGap(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID skillGapId) {

        CreateRoadmapSessionRequest request = new CreateRoadmapSessionRequest(
                null, null, null, null, skillGapId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "AI roadmap session created successfully",
                        roadmapSessionService.createSession(currentUser.getId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RoadmapSessionSummaryResponse>>> getSessions(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) RoadmapSessionStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                "Roadmap sessions retrieved successfully",
                roadmapSessionService.getSessions(currentUser.getId(), status, pageable)));
    }

    /**
     * Dashboard endpoint: the most recently viewed session (or the most
     * recently created one when no session has been opened yet).
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<RoadmapSessionSummaryResponse>> getMostRecentViewedSession(
            @AuthenticationPrincipal User currentUser) {

        RoadmapSessionSummaryResponse session =
                roadmapSessionService.getMostRecentViewedSession(currentUser.getId());

        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success("No roadmap sessions found", null));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Most recently viewed roadmap session retrieved", session));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<RoadmapSessionDetailResponse>> getSession(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID sessionId) {

        return ResponseEntity.ok(ApiResponse.success(
                "Roadmap session retrieved successfully",
                roadmapSessionService.getSessionDetail(currentUser.getId(), sessionId)));
    }

    /**
     * Explicit view tracking for clients that want to update the dashboard
     * without downloading the full session payload.
     */
    @PostMapping("/{sessionId}/view")
    public ResponseEntity<ApiResponse<RoadmapSessionSummaryResponse>> markSessionViewed(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID sessionId) {

        return ResponseEntity.ok(ApiResponse.success(
                "Roadmap session marked as viewed",
                roadmapSessionService.markViewed(currentUser.getId(), sessionId)));
    }

    @GetMapping("/{sessionId}/tasks")
    public ResponseEntity<ApiResponse<List<TodoResponse>>> getSessionTasks(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) TodoStatus status) {

        return ResponseEntity.ok(ApiResponse.success(
                "Roadmap session tasks retrieved successfully",
                roadmapSessionService.getSessionTasks(currentUser.getId(), sessionId, status)));
    }

    @PatchMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<RoadmapSessionSummaryResponse>> updateSession(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateRoadmapSessionRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Roadmap session updated successfully",
                roadmapSessionService.updateSession(currentUser.getId(), sessionId, request)));
    }
}
