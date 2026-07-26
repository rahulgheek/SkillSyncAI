package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.AiGateway;
import com.skillsync.skillsync_ai.ai.entity.SkillGapItem;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiGatewayImpl implements AiGateway {

    private final SkillGapAnalysisRepository skillGapRepository;

    @Override
    @Transactional(readOnly = true)
    public AiDashboardMetrics getDashboardMetrics(UUID userId) {
        return skillGapRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(gap -> new AiDashboardMetrics(
                        gap.getMatchPercentage(),
                        gap.getMissingSkills().stream()
                                .map(SkillGapItem::skillName)
                                .limit(3)
                                .toList()
                ))
                .orElse(AiDashboardMetrics.EMPTY); // Perfectly clean fallback!
    }
}