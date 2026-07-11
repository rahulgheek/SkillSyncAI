package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.dto.SkillAnalysisResponse;
import com.skillsync.skillsync_ai.ai.dto.SkillGapResponse;
import com.skillsync.skillsync_ai.ai.entity.SkillAnalysisResult;
import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.repo.SkillAnalysisResultRepository;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiDashboardService {

    private final SkillAnalysisResultRepository analysisRepository;
    private final SkillGapAnalysisRepository gapRepository;

    @Transactional(readOnly = true)
    public SkillAnalysisResponse getLatestResumeAnalysis(UUID userId) {
        SkillAnalysisResult result = analysisRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException("No resume analysis found for this user."));

        return new SkillAnalysisResponse(
                result.getId(), result.getSourceResumeUrl(), result.getExtractedSkills(),
                result.getSummary(), result.getStatus(), result.getErrorMessage(), result.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public SkillGapResponse getSkillGapForProject(UUID userId, UUID projectId) {
        SkillGapAnalysis result = gapRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(() -> new RuntimeException("Skill gap analysis not found for this project."));

        return new SkillGapResponse(
                result.getId(), result.getProjectId(), result.getTargetRole(),
                result.getMissingSkills(), result.getMatchedSkills(),
                result.getMatchPercentage(), result.getCreatedAt()
        );
    }
}