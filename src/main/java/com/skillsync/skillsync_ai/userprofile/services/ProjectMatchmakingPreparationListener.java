package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.AiSkill;
import com.skillsync.skillsync_ai.common.events.CandidateBatchReadyForAiEvent;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentAchievementRepository;
import com.skillsync.skillsync_ai.common.events.AiAchievement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMatchmakingPreparationListener {

    private final StudentSkillRepository studentSkillRepository;
    private final StudentAchievementRepository studentAchievementRepository;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    public void onProjectPublished(ProjectPublishedEvent event) {
        log.info("UserProfile gathering structured candidate data for Project: {}", event.projectId());

        List<StudentSkillProjection> rawSkills = studentSkillRepository.findAllActiveStudentSkills();

        // Map directly into the new structured AiSkill objects
        Map<UUID, List<AiSkill>> groupedSkills = rawSkills.stream()
                .collect(Collectors.groupingBy(
                        StudentSkillProjection::getUserId,
                        Collectors.mapping(
                                p -> new AiSkill(p.getSkillName(), p.getSkillLevel()),
                                Collectors.toList()
                        )
                ));

        List<UUID> candidateIds = new java.util.ArrayList<>(groupedSkills.keySet());
        
        List<com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement> allCandidateAchievements = 
            candidateIds.isEmpty() ? java.util.Collections.emptyList() : studentAchievementRepository.findAchievementsForUsers(candidateIds);
        
        Map<UUID, List<com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement>> achievementsByUserId = allCandidateAchievements.stream()
                .collect(Collectors.groupingBy(a -> a.getStudentProfile().getUserId()));

        List<CandidateProfileForAi> candidates = groupedSkills.entrySet().stream()
                .map(e -> {
                    UUID studentProfileId = rawSkills.stream().filter(p -> p.getUserId().equals(e.getKey())).findFirst().get().getStudentProfileId();
                    
                    List<com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement> userAchievements = achievementsByUserId.getOrDefault(e.getKey(), java.util.Collections.emptyList());
                    List<AiAchievement> aiAchievements = userAchievements.stream()
                            .map(a -> new AiAchievement(a.getTitle(), a.getType(), a.getOrganization()))
                            .toList();
                            
                    return new CandidateProfileForAi(e.getKey(), studentProfileId, e.getValue(), aiAchievements);
                })
                .toList();

        eventPublisher.publishEvent(new CandidateBatchReadyForAiEvent(
                event.projectId(), event.requiredSkillsJson(), candidates
        ));
    }
}