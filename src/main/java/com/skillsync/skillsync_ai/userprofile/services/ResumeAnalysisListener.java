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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisListener {

    private final UserProfileService userProfileService;
    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onResumeAnalyzed(ResumeAnalysisCompletedEvent event) {
        log.info("UserProfile module received AI extracted skills for User ID: {}", event.userId());
        StudentProfile profile = userProfileService.getProfileByUserId(event.userId());

        event.extractedSkills().forEach(extracted -> {
            Skill globalSkill = skillRepository.findByNameIgnoreCase(extracted.skillName())
                    .orElseGet(() -> skillRepository.save(
                            Skill.builder().name(extracted.skillName()).category("AI_EXTRACTED").isActive(true).build()
                    ));

            Optional<StudentSkill> existingSkill = studentSkillRepository.findByStudentProfileIdAndSkillId(profile.getId(), globalSkill.getId());

            // FIX: Safe Enum Parsing
            SkillLevel safeLevel;
            try {
                safeLevel = SkillLevel.valueOf(extracted.inferredLevel().toUpperCase());
            } catch (Exception e) {
                safeLevel = SkillLevel.BEGINNER; // Fallback if AI hallucinates a level
            }

            if (existingSkill.isPresent()) {
                // FEATURE: Increase confidence! They claimed it manually, and AI verified it in their resume.
                StudentSkill skill = existingSkill.get();
                if (skill.getSource() == SkillSource.MANUAL) {
                    skill.setSource(SkillSource.MANUAL_AND_AI_VERIFIED); // You must add this to your enum!
                    studentSkillRepository.save(skill);
                }
            } else {
                studentSkillRepository.save(StudentSkill.builder()
                        .studentProfile(profile).skill(globalSkill)
                        .level(safeLevel).source(SkillSource.AI_EXTRACTED)
                        .build());
            }
        });
    }
}