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
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully",
                profileService.getAggregatedProfile(userId)));
    }

    @PutMapping("/basic-info")
    public ResponseEntity<ApiResponse<Void>> updateBasicInfo(@AuthenticationPrincipal UUID userId,
                                                             @Valid @RequestBody UpdateProfileBasicInfoRequest request) {
        profileService.updateBasicInfo(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Basic info updated successfully", null));
    }

    @PutMapping("/social-links")
    public ResponseEntity<ApiResponse<Void>> updateSocialLinks(@AuthenticationPrincipal UUID userId,
                                                               @Valid @RequestBody UpdateSocialLinksRequest request) {
        profileService.updateSocialLinks(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Social links updated successfully", null));
    }

    @PutMapping("/resume")
    public ResponseEntity<ApiResponse<Void>> updateResume(@AuthenticationPrincipal UUID userId,
                                                          @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateResumeUrl(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Resume URL updated successfully", null));
    }

    @PutMapping("/avatar")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(@AuthenticationPrincipal UUID userId,
                                                          @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateAvatarUrl(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Avatar URL updated successfully", null));
    }
}