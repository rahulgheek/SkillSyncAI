package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.CompatibilityScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompatibilityScoreRepository extends JpaRepository<CompatibilityScore, UUID> {
    // For students: "Projects recommended for me"
    Page<CompatibilityScore> findAllByStudentUserIdOrderByScoreDesc(UUID studentUserId, Pageable pageable);

    // For project owners: "Top candidates for my project"
    Page<CompatibilityScore> findAllByProjectIdOrderByScoreDesc(UUID projectId, Pageable pageable);

    Optional<CompatibilityScore> findByProjectIdAndStudentUserId(UUID projectId, UUID studentUserId);
}