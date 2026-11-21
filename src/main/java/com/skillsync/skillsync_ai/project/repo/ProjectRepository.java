package com.skillsync.skillsync_ai.project.repo;

import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    // For the "My Projects" dashboard
    Page<Project> findAllByOwnerId(UUID ownerId, Pageable pageable);

    // For the main recruiting feed, filtered by college
    Page<Project> findAllByCollegeIdAndStatus(UUID collegeId, ProjectStatus status, Pageable pageable);

    // For AI Discovery Batch Processing
    List<Project> findByCollegeIdAndStatus(UUID collegeId, ProjectStatus status);

    /**
     * Serialises concurrent generation requests for the same project without a
     * separate lock table. The row is already needed for the ownership check,
     * so this adds no extra round trip.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);

    // One query for the inbox: owned projects plus projects where the user is an active member
    @Query("SELECT DISTINCT p FROM Project p WHERE p.ownerId = :userId OR EXISTS " +
            "(SELECT 1 FROM TeamMember tm WHERE tm.project = p AND tm.userId = :userId AND tm.status = :status)")
    List<Project> findChatProjectsForUser(@Param("userId") UUID userId, @Param("status") MemberStatus status);
}
