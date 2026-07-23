package com.skillsync.skillsync_ai.project.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.project.dto.*;
import com.skillsync.skillsync_ai.project.services.ProjectManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectManagementService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createDraftProject(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createDraftProject(currentUser.getId(), currentUser.getCollege().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Draft project created successfully", project));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully",
                projectService.updateProject(currentUser.getId(), projectId, request)));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        log.info("User {} is permanently deleting project {}", currentUser.getEmail(), projectId);
        projectService.deleteProject(currentUser.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }

    @PostMapping("/{projectId}/publish")
    public ResponseEntity<ApiResponse<ProjectResponse>> publishProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Project published successfully",
                projectService.publishProject(currentUser.getId(), projectId)));
    }

    @PostMapping("/{projectId}/close")
    public ResponseEntity<ApiResponse<ProjectResponse>> closeRecruitment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Project recruitment closed",
                projectService.closeRecruitment(currentUser.getId(), projectId)));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetailsResponse>> getProjectDetails(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Project retrieved", projectService.getProjectDetails(projectId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getMyProjects(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("User projects retrieved",
                projectService.getMyProjects(currentUser.getId(), pageable)));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getRecruitingFeed(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Recruiting feed retrieved",
                projectService.getRecruitingFeed(currentUser.getCollege().getId(), pageable)));
    }

    @PostMapping("/{projectId}/roles")
    public ResponseEntity<ApiResponse<ProjectRoleResponse>> addRoleToProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role added to project",
                        projectService.addRoleToProject(currentUser.getId(), projectId, request)));
    }

    @GetMapping("/{projectId}/roles")
    public ResponseEntity<ApiResponse<List<ProjectRoleResponse>>> getProjectRoles(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Project roles retrieved", projectService.getProjectRoles(projectId)));
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getProjectMembers(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Project members retrieved", projectService.getProjectMembers(projectId)));
    }

    @DeleteMapping("/{projectId}/members/{memberUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PathVariable UUID memberUserId) {
        projectService.removeMember(currentUser.getId(), projectId, memberUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from project", null));
    }

    @PostMapping("/{projectId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        projectService.leaveProject(currentUser.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully left the project", null));
    }

    @PostMapping("/{projectId}/transfer-ownership/{newOwnerId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> transferOwnership(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @PathVariable UUID newOwnerId) {
        return ResponseEntity.ok(ApiResponse.success("Project ownership transferred successfully",
                projectService.transferOwnership(currentUser.getId(), projectId, newOwnerId)));
    }

    @PatchMapping("/{projectId}/owner") // THE FIX: Changed to PATCH
    public ResponseEntity<ApiResponse<ProjectResponse>> transferOwnership(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody TransferOwnershipRequest request) { // THE FIX: Using Request Body
        return ResponseEntity.ok(ApiResponse.success("Project ownership transferred successfully",
                projectService.transferOwnership(currentUser.getId(), projectId, request.newOwnerId())));
    }
}