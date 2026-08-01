package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.userprofile.dto.AchievementRequest;
import com.skillsync.skillsync_ai.userprofile.dto.AchievementResponse;
import com.skillsync.skillsync_ai.userprofile.services.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/userprofile/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @PostMapping
    public ResponseEntity<AchievementResponse> addAchievement(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody AchievementRequest request) {
        return ResponseEntity.ok(achievementService.addAchievement(userId, request));
    }

    @PutMapping("/{achievementId}")
    public ResponseEntity<AchievementResponse> updateAchievement(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID achievementId,
            @Valid @RequestBody AchievementRequest request) {
        return ResponseEntity.ok(achievementService.updateAchievement(userId, achievementId, request));
    }

    @DeleteMapping("/{achievementId}")
    public ResponseEntity<Void> deleteAchievement(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID achievementId) {
        achievementService.deleteAchievement(userId, achievementId);
        return ResponseEntity.noContent().build();
    }
}
