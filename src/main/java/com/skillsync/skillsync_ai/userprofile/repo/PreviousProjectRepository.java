package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.PreviousProject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreviousProjectRepository extends JpaRepository<PreviousProject, UUID> {

    // Fetch all projects for a specific user
    @EntityGraph(attributePaths = "techStack")
    List<PreviousProject> findByStudentProfile_UserId(UUID userId);

    @Query("SELECT DISTINCT p FROM PreviousProject p JOIN FETCH p.studentProfile sp LEFT JOIN FETCH p.techStack WHERE sp.userId IN :userIds")
    List<PreviousProject> findAllByUserIdInWithTechStack(@Param("userIds") Collection<UUID> userIds);

    // Crucial for security: Find a specific project ONLY if it belongs to this user
    Optional<PreviousProject> findByIdAndStudentProfile_UserId(UUID projectId, UUID userId);
}
