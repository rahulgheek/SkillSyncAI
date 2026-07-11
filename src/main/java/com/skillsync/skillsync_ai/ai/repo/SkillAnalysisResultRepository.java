package com.skillsync.skillsync_ai.ai.repo;

import com.skillsync.skillsync_ai.ai.entity.SkillAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillAnalysisResultRepository extends JpaRepository<SkillAnalysisResult, UUID> {
    // Fetches the most recent analysis for a given user
    Optional<SkillAnalysisResult> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}