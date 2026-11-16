package com.skillsync.skillsync_ai.ai.repo;

import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {

    // Finds all roadmaps belonging to a specific user
    List<Roadmap> findAllByUserId(UUID userId);

    List<Roadmap> findAllByUserIdAndStatus(UUID userId, RoadmapStatus status);

    // Safely finds a specific roadmap, ensuring the user actually owns it
    Optional<Roadmap> findByIdAndUserId(UUID id, UUID userId);
}