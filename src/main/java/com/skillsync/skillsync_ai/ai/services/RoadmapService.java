package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final CheckpointRepository checkpointRepository;
    private final SkillGapAnalysisRepository skillGapRepository;
    private final GeminiApiService geminiApiService; // Ensure you add a generateRoadmap method to this similar to analyzeSkillGap

    @Transactional
    public Roadmap generateRoadmapFromGap(UUID userId, UUID skillGapId) {
        SkillGapAnalysis gap = skillGapRepository.findById(skillGapId).orElseThrow();
        if (!gap.getUserId().equals(userId)) throw new SecurityException("Unauthorized");

        Roadmap roadmap = Roadmap.builder()
                .userId(userId) // Fixed ID
                .skillGapAnalysis(gap)
                .title("Upskilling for " + (gap.getTargetRole() != null ? gap.getTargetRole() : "Project"))
                .description("AI-generated roadmap based on your skill gap.")
                .status(RoadmapStatus.ACTIVE) // Use Enum
                .build();
        Roadmap savedRoadmap = roadmapRepository.save(roadmap);

        try {
            // Format missing skills into a comma-separated string
            String missingSkillsStr = gap.getMissingSkills().stream()
                    .map(com.skillsync.skillsync_ai.ai.entity.SkillGapItem::skillName)
                    .reduce((a, b) -> a + ", " + b).orElse("");

            // REAL AI CALL
            var response = geminiApiService.generateRoadmap(missingSkillsStr).block();
            List<Map<String, String>> steps = geminiApiService.parseRoadmapSteps(response.getResponseText());

            short order = 1;
            for (Map<String, String> step : steps) {
                checkpointRepository.save(Checkpoint.builder()
                        .roadmap(savedRoadmap)
                        .title(step.get("title"))
                        .description(step.get("description"))
                        .orderIndex(order++)
                        .status(CheckpointStatus.PENDING)
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to generate checkpoints for roadmap {}", savedRoadmap.getUserId(), e);
            savedRoadmap.setStatus(RoadmapStatus.FAILED);
            roadmapRepository.save(savedRoadmap);
        }
        return savedRoadmap;
    }

    @Transactional
    public void updateCheckpointStatus(UUID userId, UUID checkpointId, CheckpointStatus status) {
        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        if (!checkpoint.getRoadmap().getStudentProfileId().equals(userId)) {
            throw new SecurityException("Unauthorized");
        }

        checkpoint.setStatus(status);
        if (status == CheckpointStatus.COMPLETED) {
            checkpoint.setCompletedAt(java.time.Instant.now());
        }
        checkpointRepository.save(checkpoint);
    }


}