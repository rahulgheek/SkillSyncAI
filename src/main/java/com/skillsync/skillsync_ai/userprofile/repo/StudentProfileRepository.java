package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    // Cross-module lookup: Finds a profile using the Auth module's User ID
    Optional<StudentProfile> findByUserId(UUID userId);
}