package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.userprofile.dto.AddSkillRequest;
import com.skillsync.skillsync_ai.userprofile.entity.Skill;
import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import com.skillsync.skillsync_ai.userprofile.services.SkillManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillManagementService skillService;

    // Global search for the frontend dropdown menu
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Skill>>> searchSkills(@RequestParam String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Search query must be at least 2 characters"));
        }
        return ResponseEntity.ok(ApiResponse.success("Skills retrieved successfully",
                skillService.searchGlobalSkills(query)));
    }

    // Get the logged-in user's skills
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<StudentSkill>>> getMySkills(@AuthenticationPrincipal(expression = "id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("User skills retrieved successfully",
                skillService.getStudentSkills(userId)));
    }

    // Add a skill to the logged-in user's profile
    @PostMapping("/me")
    public ResponseEntity<ApiResponse<StudentSkill>> addSkillToProfile(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody AddSkillRequest request) {

        StudentSkill addedSkill = skillService.addSkillToStudent(
                userId,
                request.skillId(),
                request.level()
        );
        return ResponseEntity.ok(ApiResponse.success("Skill added to profile successfully", addedSkill));
    }

    // Remove a skill from the logged-in user's profile
    @DeleteMapping("/me/{studentSkillId}")
    public ResponseEntity<ApiResponse<Void>> removeSkillFromProfile(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID studentSkillId) {

        skillService.removeSkillFromStudent(userId, studentSkillId);
        return ResponseEntity.ok(ApiResponse.success("Skill removed successfully", null));
    }
}