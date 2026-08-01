package com.skillsync.skillsync_ai.analytics.controller;

import com.skillsync.skillsync_ai.analytics.dto.DashboardAnalyticsResponse;
import com.skillsync.skillsync_ai.analytics.services.AnalyticsService;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
// Import your User entity!
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardAnalyticsResponse>> getDashboard(
            @AuthenticationPrincipal User currentUser) {

        DashboardAnalyticsResponse dashboardData = analyticsService.getDashboardData(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Dashboard data aggregated successfully", dashboardData));
    }
}