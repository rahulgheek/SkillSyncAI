package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.userprofile.entity.Skill;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillLevel;
import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillSource;
import com.skillsync.skillsync_ai.userprofile.repo.SkillRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import com.skillsync.skillsync_ai.userprofile.services.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillManagementService {

    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final UserProfileService userProfileService; // Direct module-to-module call

    @Transactional(readOnly = true)
    public List<Skill> searchGlobalSkills(String query) {
        return skillRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(query);
    }

    @Transactional(readOnly = true)
    public List<StudentSkill> getStudentSkills(UUID userId) {
        StudentProfile profile = userProfileService.getProfileByUserId(userId);
        return studentSkillRepository.findAllByStudentProfileId(profile.getId());
    }

    @Transactional
    public StudentSkill addSkillToStudent(UUID userId, UUID skillId, SkillLevel level) {
        StudentProfile profile = userProfileService.getProfileByUserId(userId);

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("Global skill not found."));

        // Check if student already has this skill mapped
        studentSkillRepository.findByStudentProfileIdAndSkillId(profile.getId(), skill.getId())
                .ifPresent(s -> {
                    throw new RuntimeException("Student already has this skill listed.");
                });

        StudentSkill newStudentSkill = StudentSkill.builder()
                .studentProfile(profile)
                .skill(skill)
                .level(level)
                .source(SkillSource.MANUAL) // Change to AI_EXTRACTED when the AI module calls this
                .build();

        return studentSkillRepository.save(newStudentSkill);
    }

    @Transactional
    public void removeSkillFromStudent(UUID userId, UUID studentSkillId) {
        StudentProfile profile = userProfileService.getProfileByUserId(userId);

        StudentSkill skillToRemove = studentSkillRepository.findById(studentSkillId)
                .orElseThrow(() -> new RuntimeException("Student skill mapping not found."));

        // Security check: ensure the skill actually belongs to the user trying to delete it
        if (!skillToRemove.getStudentProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("Unauthorized to modify this skill.");
        }

        studentSkillRepository.delete(skillToRemove);
    }
}