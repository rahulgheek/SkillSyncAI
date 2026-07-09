package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.ResumeAnalysisCompletedEvent;
import com.skillsync.skillsync_ai.userprofile.entity.Skill;
import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillLevel;
import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillSource;
import com.skillsync.skillsync_ai.userprofile.repo.SkillRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisListener {

    private final UserProfileService userProfileService;
    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;

    @ApplicationModuleListener
    @Transactional
    public void onResumeAnalyzed(ResumeAnalysisCompletedEvent event) {
        log.info("UserProfile module received AI extracted skills for User ID: {}", event.userId());

        StudentProfile profile = userProfileService.getProfileByUserId(event.userId());

        event.extractedSkills().forEach(extracted -> {
            // 1. Find or create the global skill
            Skill globalSkill = skillRepository.findByNameIgnoreCase(extracted.skillName())
                    .orElseGet(() -> skillRepository.save(
                            Skill.builder()
                                    .name(extracted.skillName())
                                    .category("AI_EXTRACTED")
                                    .isActive(true)
                                    .build()
                    ));

            // 2. Check if student already has this skill mapped
            boolean alreadyHasSkill = studentSkillRepository
                    .findByStudentProfileIdAndSkillId(profile.getId(), globalSkill.getId())
                    .isPresent();

            // 3. Map it if they don't have it
            if (!alreadyHasSkill) {
                StudentSkill newSkill = StudentSkill.builder()
                        .studentProfile(profile)
                        .skill(globalSkill)
                        .level(SkillLevel.valueOf(extracted.inferredLevel().toUpperCase())) // Assuming AI returns BEGINNER, INTERMEDIATE, etc.
                        .source(SkillSource.AI_EXTRACTED)
                        .build();
                studentSkillRepository.save(newSkill);
            }
        });

        log.info("Successfully updated student skills from AI extraction for User ID: {}", event.userId());
    }
}