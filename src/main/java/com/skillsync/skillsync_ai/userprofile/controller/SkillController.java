package com.skillsync.skillsync_ai.userprofile.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
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
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillManagementService skillService;

    // Global search for the frontend dropdown menu
    @GetMapping("/search")
    public ResponseEntity<List<Skill>> searchSkills(@RequestParam String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(skillService.searchGlobalSkills(query));
    }

    // Get the logged-in user's skills
    @GetMapping("/me")
    public ResponseEntity<List<StudentSkill>> getMySkills(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(skillService.getStudentSkills(currentUser.getId()));
    }

    // Add a skill to the logged-in user's profile
    @PostMapping("/me")
    public ResponseEntity<StudentSkill> addSkillToProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AddSkillRequest request) {

        StudentSkill addedSkill = skillService.addSkillToStudent(
                currentUser.getId(),
                request.skillId(),
                request.level()
        );
        return ResponseEntity.ok(addedSkill);
    }

    // Remove a skill from the logged-in user's profile
    @DeleteMapping("/me/{studentSkillId}")
    public ResponseEntity<String> removeSkillFromProfile(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID studentSkillId) {

        skillService.removeSkillFromStudent(currentUser.getId(), studentSkillId);
        return ResponseEntity.ok("Skill removed successfully.");
    }
}