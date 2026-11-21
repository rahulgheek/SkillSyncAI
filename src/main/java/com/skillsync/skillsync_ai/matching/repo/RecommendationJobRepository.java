package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.RecommendationJob;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecommendationJobRepository extends JpaRepository<RecommendationJob, UUID> {

    Optional<RecommendationJob> findByLockKey(String lockKey);

    Optional<RecommendationJob> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    Optional<RecommendationJob> findFirstByProjectIdOrderByRequestedAtDesc(UUID projectId);

    Optional<RecommendationJob> findFirstByTypeAndSubjectIdOrderByRequestedAtDesc(
            RecommendationJobType type, UUID subjectId);

    Optional<RecommendationJob> findFirstByProjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
            UUID projectId, String inputHash, RecommendationJobStatus status, Instant after);

    Optional<RecommendationJob> findFirstByTypeAndSubjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
            RecommendationJobType type, UUID subjectId, String inputHash,
            RecommendationJobStatus status, Instant after);
}
