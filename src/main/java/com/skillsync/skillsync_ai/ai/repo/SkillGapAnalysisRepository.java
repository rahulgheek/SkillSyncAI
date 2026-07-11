package com.skillsync.skillsync_ai.ai.repo;

import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillGapAnalysisRepository extends JpaRepository<SkillGapAnalysis, UUID> {
    Optional<SkillGapAnalysis> findByUserIdAndProjectId(UUID userId, UUID projectId);
}