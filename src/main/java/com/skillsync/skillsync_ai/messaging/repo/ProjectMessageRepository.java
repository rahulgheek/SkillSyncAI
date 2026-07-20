package com.skillsync.skillsync_ai.messaging.repo;

import com.skillsync.skillsync_ai.messaging.entity.ProjectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectMessageRepository extends JpaRepository<ProjectMessage, UUID> {
    Page<ProjectMessage> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}