package com.skillsync.skillsync_ai.ai.controller;

import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionSummaryResponse;
import com.skillsync.skillsync_ai.ai.dto.SkillAnalysisResponse;
import com.skillsync.skillsync_ai.ai.dto.SkillGapResponse;
import com.skillsync.skillsync_ai.ai.services.AiDashboardService;
import com.skillsync.skillsync_ai.ai.services.RoadmapSessionService;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiDashboardService dashboardService;
    private final RoadmapSessionService roadmapSessionService;

    @GetMapping("/resume/status")
    public ResponseEntity<ApiResponse<SkillAnalysisResponse>> getLatestResumeAnalysis(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved latest resume analysis",
                dashboardService.getLatestResumeAnalysis(currentUser.getId())));
    }

    @GetMapping("/skill-gap/{projectId}")
    public ResponseEntity<ApiResponse<SkillGapResponse>> getSkillGapForProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved project skill gap analysis",
                dashboardService.getSkillGapForProject(currentUser.getId(), projectId)));
    }

    /**
     * Dashboard card: the roadmap session the user most recently opened.
     */
    @GetMapping("/dashboard/recent-roadmap-session")
    public ResponseEntity<ApiResponse<RoadmapSessionSummaryResponse>> getRecentRoadmapSession(
            @AuthenticationPrincipal User currentUser) {
        RoadmapSessionSummaryResponse session =
                roadmapSessionService.getMostRecentViewedSession(currentUser.getId());

        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success("No roadmap sessions found", null));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Most recently viewed roadmap session retrieved", session));
    }
}