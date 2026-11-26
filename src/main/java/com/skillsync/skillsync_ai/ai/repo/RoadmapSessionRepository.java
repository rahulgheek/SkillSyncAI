package com.skillsync.skillsync_ai.ai.repo;

import com.skillsync.skillsync_ai.ai.entity.RoadmapSession;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoadmapSessionRepository extends JpaRepository<RoadmapSession, UUID> {

    Page<RoadmapSession> findAllByUserId(UUID userId, Pageable pageable);

    Page<RoadmapSession> findAllByUserIdAndStatus(UUID userId, RoadmapSessionStatus status, Pageable pageable);

    Optional<RoadmapSession> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Most recently opened session. Only sessions that have actually been
     * viewed are considered, so creating a session in the background does not
     * hijack the dashboard.
     */
    Optional<RoadmapSession> findFirstByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(UUID userId);

    /**
     * Most recently opened session that did not fail. A failed generation must
     * not replace the last working path as the default workspace.
     */
    Optional<RoadmapSession> findFirstByUserIdAndStatusNotAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(
            UUID userId, RoadmapSessionStatus status);

    /**
     * Fallback for users who have never opened a session (for example accounts
     * created before view tracking was introduced).
     */
    Optional<RoadmapSession> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<RoadmapSession> findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(
            UUID userId, RoadmapSessionStatus status);
}
