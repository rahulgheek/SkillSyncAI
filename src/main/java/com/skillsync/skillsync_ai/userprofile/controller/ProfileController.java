package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.userprofile.dto.ProfileResponse;
import com.skillsync.skillsync_ai.userprofile.dto.UpdateMediaUrlRequest;
import com.skillsync.skillsync_ai.userprofile.dto.UpdateProfileBasicInfoRequest;
import com.skillsync.skillsync_ai.userprofile.dto.UpdateSocialLinksRequest;
import com.skillsync.skillsync_ai.userprofile.services.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService profileService;

    // Fetch the entire dashboard profile (Info + Projects)
    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully",
                profileService.getAggregatedProfile(userId)));
    }

    @PostMapping("/insights/generate")
    public ResponseEntity<ApiResponse<ProfileResponse>> generateAiInsights(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("AI Insights generated successfully",
                profileService.generateAiInsights(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                                      @Valid @RequestBody UpdateProfileBasicInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile created successfully",
                profileService.createProfile(userId, request)));
    }

    @PutMapping("/basic-info")
    public ResponseEntity<ApiResponse<Void>> updateBasicInfo(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                             @Valid @RequestBody UpdateProfileBasicInfoRequest request) {
        profileService.updateBasicInfo(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Basic info updated successfully", null));
    }

    @PutMapping("/social-links")
    public ResponseEntity<ApiResponse<Void>> updateSocialLinks(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                               @Valid @RequestBody UpdateSocialLinksRequest request) {
        profileService.updateSocialLinks(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Social links updated successfully", null));
    }

    @PutMapping("/resume")
    public ResponseEntity<ApiResponse<Void>> updateResume(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                          @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateResumeUrl(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Resume URL updated successfully", null));
    }

    @PutMapping("/avatar")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(@AuthenticationPrincipal(expression = "id") UUID userId,
                                                          @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateAvatarUrl(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Avatar URL updated successfully", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<ProfileResponse>>> searchProfiles(
            @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @RequestParam(required = false, defaultValue = "") String query,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Profiles retrieved successfully",
                profileService.searchPublicProfiles(query, currentUserId, pageable)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getPublicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Public profile retrieved successfully",
                profileService.getPublicProfile(userId)));
    }
}