package com.skillsync.skillsync_ai.project.repo;

import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRoleRepository extends JpaRepository<ProjectRole, UUID> {
    List<ProjectRole> findAllByProjectId(UUID projectId);

    List<ProjectRole> findAllByIdIn(Collection<UUID> ids);
}
