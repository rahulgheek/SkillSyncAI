package com.skillsync.skillsync_ai.matching.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.matching.dto.ApplicationResponse;
import com.skillsync.skillsync_ai.matching.dto.CompatibilityScoreResponse;
import com.skillsync.skillsync_ai.matching.dto.InvitationResponse;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.services.CompatibilityScoreService;
import com.skillsync.skillsync_ai.matching.services.TeamMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching/dashboard")
@RequiredArgsConstructor
public class MatchingDashboardController {

    private final TeamMatchingService matchingService;
    private final CompatibilityScoreService scoreService;

    @GetMapping("/me/applications")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal User currentUser, @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved user applications",
                matchingService.getStudentApplications(currentUser.getId(), pageable)));
    }

    @GetMapping("/me/invitations")
    public ResponseEntity<ApiResponse<Page<InvitationResponse>>> getMyInvitations(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "PENDING") InvitationStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved user invitations",
                matchingService.getStudentInvitations(currentUser.getId(), status, pageable)));
    }

    @GetMapping("/me/recommendations")
    public ResponseEntity<ApiResponse<Page<CompatibilityScoreResponse>>> getRecommendedProjects(
            @AuthenticationPrincipal User currentUser, @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved AI project recommendations",
                scoreService.getRecommendedProjectsForStudent(currentUser.getId(), pageable)));
    }

    @GetMapping("/projects/{projectId}/applications")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> getProjectApplications(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "PENDING") ApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved project applications",
                matchingService.getProjectApplications(currentUser.getId(), projectId, status, pageable)));
    }

    @GetMapping("/projects/{projectId}/invitations")
    public ResponseEntity<ApiResponse<Page<InvitationResponse>>> getProjectInvitations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved project invitations",
                matchingService.getProjectInvitations(currentUser.getId(), projectId, pageable)));
    }

    @GetMapping("/projects/{projectId}/recommendations")
    public ResponseEntity<ApiResponse<Page<CompatibilityScoreResponse>>> getTopCandidatesForProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PageableDefault(size = 10) Pageable pageable) {
        matchingService.verifyProjectOwnership(projectId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Retrieved AI candidate recommendations",
                scoreService.getTopCandidatesForProject(projectId, pageable)));
    }
}