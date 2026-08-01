package com.skillsync.skillsync_ai.matching.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.matching.dto.ApplicationRequest;
import com.skillsync.skillsync_ai.matching.dto.ApplicationResponse;
import com.skillsync.skillsync_ai.matching.dto.DecisionRequest;
import com.skillsync.skillsync_ai.matching.services.TeamMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final TeamMatchingService matchingService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> applyToProject(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application submitted successfully",
                        matchingService.applyToProject(currentUser.getId(), request.projectId(), request.projectRoleId(), request.coverMessage())));
    }

    @PostMapping("/{applicationId}/decide")
    public ResponseEntity<ApiResponse<ApplicationResponse>> decideOnApplication(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID applicationId,
            @Valid @RequestBody DecisionRequest request) {
        String decision = request.accept() ? "accepted" : "rejected";
        return ResponseEntity.ok(ApiResponse.success("Application " + decision,
                matchingService.decideOnApplication(currentUser.getId(), applicationId, request.accept())));
    }

    @PostMapping("/{applicationId}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdrawApplication(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID applicationId) {
        matchingService.withdrawApplication(currentUser.getId(), applicationId);
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn successfully", null));
    }
    
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<java.util.List<ApplicationResponse>>> getProjectApplications(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        org.springframework.data.domain.Page<ApplicationResponse> page = matchingService.getProjectApplications(
                currentUser.getId(), projectId, com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus.PENDING, 
                org.springframework.data.domain.PageRequest.of(0, 100));
        return ResponseEntity.ok(ApiResponse.success("Project applications retrieved", page.getContent()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<java.util.List<ApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal User currentUser) {
        org.springframework.data.domain.Page<ApplicationResponse> page = matchingService.getStudentApplications(
                currentUser.getId(), org.springframework.data.domain.PageRequest.of(0, 100));
        return ResponseEntity.ok(ApiResponse.success("User applications retrieved", page.getContent()));
    }
}