package com.skillsync.skillsync_ai.project.repo;

import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    // For the "My Projects" dashboard
    Page<Project> findAllByOwnerId(UUID ownerId, Pageable pageable);

    // For the main recruiting feed, filtered by college
    Page<Project> findAllByCollegeIdAndStatus(UUID collegeId, ProjectStatus status, Pageable pageable);
}