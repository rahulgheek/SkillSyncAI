package com.skillsync.skillsync_ai.matching.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.matching.dto.DecisionRequest;
import com.skillsync.skillsync_ai.matching.dto.InvitationRequest;
import com.skillsync.skillsync_ai.matching.dto.InvitationResponse;
import com.skillsync.skillsync_ai.matching.services.TeamMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final TeamMatchingService matchingService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteStudent(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent successfully",
                        matchingService.inviteStudent(currentUser.getId(), request.projectId(), request.projectRoleId(), request.invitedUserId(), request.message())));
    }

    @PostMapping("/{invitationId}/respond")
    public ResponseEntity<ApiResponse<InvitationResponse>> respondToInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID invitationId,
            @Valid @RequestBody DecisionRequest request) {
        String decision = request.accept() ? "accepted" : "declined";
        return ResponseEntity.ok(ApiResponse.success("Invitation " + decision,
                matchingService.respondToInvitation(currentUser.getId(), invitationId, request.accept())));
    }

    @PostMapping("/{invitationId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID invitationId) {
        matchingService.revokeInvitation(currentUser.getId(), invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully", null));
    }
    
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<java.util.List<InvitationResponse>>> getProjectInvitations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID projectId) {
        org.springframework.data.domain.Page<InvitationResponse> page = matchingService.getProjectInvitations(
                currentUser.getId(), projectId, org.springframework.data.domain.PageRequest.of(0, 100));
        return ResponseEntity.ok(ApiResponse.success("Project invitations retrieved", page.getContent()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<java.util.List<InvitationResponse>>> getStudentInvitations(
            @AuthenticationPrincipal User currentUser) {
        org.springframework.data.domain.Page<InvitationResponse> page = matchingService.getStudentInvitations(
                currentUser.getId(), com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus.PENDING, 
                org.springframework.data.domain.PageRequest.of(0, 100));
        return ResponseEntity.ok(ApiResponse.success("User invitations retrieved", page.getContent()));
    }
}