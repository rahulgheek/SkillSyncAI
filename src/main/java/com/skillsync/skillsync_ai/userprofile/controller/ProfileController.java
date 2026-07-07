package com.skillsync.skillsync_ai.userprofile.controller;

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

    @PutMapping("/basic-info")
    public ResponseEntity<Void> updateBasicInfo(@AuthenticationPrincipal UUID userId,
                                                @Valid @RequestBody UpdateProfileBasicInfoRequest request) {
        profileService.updateBasicInfo(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/social-links")
    public ResponseEntity<Void> updateSocialLinks(@AuthenticationPrincipal UUID userId,
                                                  @Valid @RequestBody UpdateSocialLinksRequest request) {
        profileService.updateSocialLinks(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/resume")
    public ResponseEntity<Void> updateResume(@AuthenticationPrincipal UUID userId,
                                             @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateResumeUrl(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/avatar")
    public ResponseEntity<Void> updateAvatar(@AuthenticationPrincipal UUID userId,
                                             @Valid @RequestBody UpdateMediaUrlRequest request) {
        profileService.updateAvatarUrl(userId, request);
        return ResponseEntity.ok().build();
    }
}