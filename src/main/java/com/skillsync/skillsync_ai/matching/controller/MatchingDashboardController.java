package com.skillsync.skillsync_ai.matching.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.matching.dto.ApplicationResponse;
import com.skillsync.skillsync_ai.matching.dto.InvitationResponse;
import com.skillsync.skillsync_ai.matching.dto.ProjectRecommendationResponse;
import com.skillsync.skillsync_ai.matching.dto.RecommendationJobResponse;
import com.skillsync.skillsync_ai.matching.dto.StudentProjectRecommendationResponse;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.services.RecommendationJobService;
import com.skillsync.skillsync_ai.matching.services.RecommendationScoringService;
import com.skillsync.skillsync_ai.matching.services.TeamMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching/dashboard")
@RequiredArgsConstructor
public class MatchingDashboardController {

    private final TeamMatchingService matchingService;
    private final RecommendationScoringService recommendationScoringService;
    private final RecommendationJobService recommendationJobService;

    // ------------------------------------------------------------------
    // Student dashboard
    // ------------------------------------------------------------------

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
    public ResponseEntity<ApiResponse<Page<StudentProjectRecommendationResponse>>> getRecommendedProjects(
            @AuthenticationPrincipal User currentUser, @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved AI project recommendations",
                recommendationScoringService.getRecommendedProjectsForStudent(currentUser.getId(), pageable)));
    }

    @PostMapping("/me/recommendations/generate")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> generateStudentRecommendations(
            @AuthenticationPrincipal User currentUser) {
        RecommendationJobResponse job = recommendationJobService.requestStudentGeneration(currentUser.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Matching started. Poll the job or wait for the notification.", job));
    }

    @GetMapping("/me/recommendations/jobs/{jobId}")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> getStudentRecommendationJob(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved matching job",
                recommendationJobService.getJob(currentUser.getId(), jobId)));
    }

    @GetMapping("/me/recommendations/jobs/latest")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> getLatestStudentRecommendationJob(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved latest matching job",
                recommendationJobService.getLatestStudentJob(currentUser.getId())));
    }

    // ------------------------------------------------------------------
    // Project owner dashboard
    // ------------------------------------------------------------------

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
    public ResponseEntity<ApiResponse<Page<ProjectRecommendationResponse>>> getTopCandidatesForProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PageableDefault(size = 10) Pageable pageable) {
        matchingService.verifyProjectOwnership(projectId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Retrieved AI candidate recommendations",
                recommendationScoringService.getTopCandidatesForProject(projectId, pageable)));
    }

    @PostMapping("/projects/{projectId}/generate")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> generateRecommendations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        RecommendationJobResponse job = recommendationJobService.requestProjectGeneration(
                currentUser.getId(), projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Matching started. Poll the job or wait for the notification.", job));
    }

    @GetMapping("/projects/{projectId}/recommendations/jobs/{jobId}")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> getProjectRecommendationJob(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        // The job response is scoped to the requester; projectId is kept in the
        // path so the frontend can cache per project.
        return ResponseEntity.ok(ApiResponse.success("Retrieved matching job",
                recommendationJobService.getJob(currentUser.getId(), jobId)));
    }

    @GetMapping("/projects/{projectId}/recommendations/jobs/latest")
    public ResponseEntity<ApiResponse<RecommendationJobResponse>> getLatestProjectRecommendationJob(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved latest matching job",
                recommendationJobService.getLatestProjectJob(currentUser.getId(), projectId)));
    }
}
