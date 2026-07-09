package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectRequest;
import com.skillsync.skillsync_ai.userprofile.dto.PreviousProjectResponse;
import com.skillsync.skillsync_ai.userprofile.services.PreviousProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<PreviousProjectResponse>> getMyProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projectService.getProjectsForUser(currentUser.getId()));
    }

    // 2. Add a new project
    @PostMapping
    public ResponseEntity<PreviousProjectResponse> addProject(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PreviousProjectRequest request) {
        return ResponseEntity.ok(projectService.addProject(currentUser.getId(), request));
    }

    // 3. Update an existing project
    @PutMapping("/{projectId}")
    public ResponseEntity<PreviousProjectResponse> updateProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody PreviousProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(currentUser.getId(), projectId, request));
    }

    // 4. Delete a project
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        projectService.deleteProject(currentUser.getId(), projectId);
        return ResponseEntity.noContent().build();
    }
}