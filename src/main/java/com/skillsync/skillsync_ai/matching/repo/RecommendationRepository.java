package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.ProjectRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<ProjectRecommendation, UUID> {
    Optional<ProjectRecommendation> findByProjectIdAndUserId(UUID projectId, UUID userId);

    org.springframework.data.domain.Page<ProjectRecommendation> findAllByProjectIdOrderByFinalScoreDesc(UUID projectId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ProjectRecommendation> findAllByUserIdOrderByFinalScoreDesc(UUID userId, org.springframework.data.domain.Pageable pageable);

    /**
     * One query for the whole batch instead of one query per shortlisted
     * candidate. Critical when the database is a cross-region round trip away.
     */
    List<ProjectRecommendation> findAllByProjectIdAndUserIdIn(UUID projectId, Collection<UUID> userIds);

    List<ProjectRecommendation> findAllByUserIdAndProjectIdIn(UUID userId, Collection<UUID> projectIds);
}
