package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.Application;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Page<Application> findAllByProjectIdAndStatus(UUID projectId, ApplicationStatus status, Pageable pageable);
    Page<Application> findAllByApplicantUserId(UUID applicantUserId, Pageable pageable);

    // Prevent duplicate pending applications
    boolean existsByProjectIdAndApplicantUserIdAndStatus(UUID projectId, UUID applicantUserId, ApplicationStatus status);

    List<Application> findAllByProjectIdAndApplicantUserIdAndStatus(
            UUID projectId, UUID applicantUserId, ApplicationStatus status);

    long countByProjectIdAndProjectRoleIdAndStatus(
            UUID projectId, UUID projectRoleId, ApplicationStatus status);

    @Query("""
            SELECT a.status, COUNT(a)
            FROM Application a
            WHERE a.projectId = :projectId
            GROUP BY a.status
            """)
    List<Object[]> countByProjectIdGroupByStatus(@Param("projectId") UUID projectId);

    @Query("""
            SELECT a.projectRoleId, COUNT(a)
            FROM Application a
            WHERE a.projectId = :projectId
              AND a.status = :status
              AND a.projectRoleId IS NOT NULL
            GROUP BY a.projectRoleId
            """)
    List<Object[]> countByProjectIdAndStatusGroupByRole(
            @Param("projectId") UUID projectId,
            @Param("status") ApplicationStatus status);
}