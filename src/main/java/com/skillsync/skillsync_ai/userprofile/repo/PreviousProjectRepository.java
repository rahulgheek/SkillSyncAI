package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.PreviousProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreviousProjectRepository extends JpaRepository<PreviousProject, UUID> {
    // Fetch all projects for a specific user
    List<PreviousProject> findByStudentProfile_UserId(UUID userId);

    // Crucial for security: Find a specific project ONLY if it belongs to this user
    Optional<PreviousProject> findByIdAndStudentProfile_UserId(UUID projectId, UUID userId);
}