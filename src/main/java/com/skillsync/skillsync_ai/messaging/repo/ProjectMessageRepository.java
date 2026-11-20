package com.skillsync.skillsync_ai.messaging.repo;

import com.skillsync.skillsync_ai.messaging.entity.ProjectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectMessageRepository extends JpaRepository<ProjectMessage, UUID> {
    Page<ProjectMessage> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    @Query(value = "SELECT DISTINCT ON (project_id) * FROM messaging.project_messages " +
            "WHERE project_id IN (:projectIds) ORDER BY project_id, created_at DESC", nativeQuery = true)
    List<ProjectMessage> findLatestByProjectIds(@Param("projectIds") Collection<UUID> projectIds);
}
