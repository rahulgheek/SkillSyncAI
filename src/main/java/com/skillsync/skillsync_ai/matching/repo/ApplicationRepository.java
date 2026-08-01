package com.skillsync.skillsync_ai.matching.repo;

import com.skillsync.skillsync_ai.matching.entity.Application;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}