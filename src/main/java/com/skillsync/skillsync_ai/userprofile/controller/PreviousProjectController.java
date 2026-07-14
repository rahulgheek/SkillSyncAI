package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectRequest;
import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectResponse;
import com.skillsync.skillsync_ai.userprofile.services.PreviousProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/me/projects")
@RequiredArgsConstructor
public class PreviousProjectController {

    private final PreviousProjectService projectService;

    // 1. Get all projects for the logged-in user
    @GetMapping
    public ResponseEntity<ApiResponse<List<PreviousProjectResponse>>> getMyProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully",
                projectService.getProjectsForUser(currentUser.getId())));
    }

    // 2. Add a new project
    @PostMapping
    public ResponseEntity<ApiResponse<PreviousProjectResponse>> addProject(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PreviousProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project added successfully",
                        projectService.addProject(currentUser.getId(), request)));
    }

    // 3. Update an existing project
    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<PreviousProjectResponse>> updateProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody PreviousProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully",
                projectService.updateProject(currentUser.getId(), projectId, request)));
    }

    // 4. Delete a project
    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        projectService.deleteProject(currentUser.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }
}