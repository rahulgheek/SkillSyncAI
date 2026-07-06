package com.skillsync.skillsync_ai.project.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.project.dto.CreateProjectRequest;
import com.skillsync.skillsync_ai.project.dto.CreateProjectRoleRequest;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.services.ProjectManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectManagementService projectService;

    @PostMapping
    public ResponseEntity<Project> createDraftProject(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateProjectRequest request) {

        // Passing the user's collegeId ensures the project is scoped to their campus
        Project project = projectService.createDraftProject(
                currentUser.getId(),
                currentUser.getCollege().getId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PostMapping("/{projectId}/roles")
    public ResponseEntity<ProjectRole> addRoleToProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectRoleRequest request) {

        ProjectRole role = projectService.addRoleToProject(currentUser.getId(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @PostMapping("/{projectId}/publish")
    public ResponseEntity<Project> publishProject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {

        Project publishedProject = projectService.publishProject(currentUser.getId(), projectId);
        return ResponseEntity.ok(publishedProject);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getMyProjectDetails(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {

        Project project = projectService.getProjectByIdAndOwner(projectId, currentUser.getId());
        return ResponseEntity.ok(project);
    }
}