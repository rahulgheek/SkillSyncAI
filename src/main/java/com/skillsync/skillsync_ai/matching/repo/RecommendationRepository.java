package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.ProjectRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<ProjectRecommendation, UUID> {
    Optional<ProjectRecommendation> findByProjectIdAndUserId(UUID projectId, UUID userId);
}