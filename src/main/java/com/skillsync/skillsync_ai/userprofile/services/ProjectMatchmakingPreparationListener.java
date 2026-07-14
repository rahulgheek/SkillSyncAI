package com.skillsync.skillsync_ai.userprofile.services;

import com.skillsync.skillsync_ai.common.events.AiSkill;
import com.skillsync.skillsync_ai.common.events.CandidateBatchReadyForAiEvent;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectPublishedEvent;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
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

        List<CandidateProfileForAi> candidates = groupedSkills.entrySet().stream()
                .map(e -> new CandidateProfileForAi(e.getKey(), e.getValue()))
                .toList();

        eventPublisher.publishEvent(new CandidateBatchReadyForAiEvent(
                event.projectId(), event.requiredSkillsJson(), candidates
        ));
    }
}