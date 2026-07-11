package com.skillsync.skillsync_ai.ai.repo;

import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CheckpointRepository extends JpaRepository<Checkpoint, UUID> {
    List<Checkpoint> findAllByRoadmapIdOrderByOrderIndexAsc(UUID roadmapId);
}