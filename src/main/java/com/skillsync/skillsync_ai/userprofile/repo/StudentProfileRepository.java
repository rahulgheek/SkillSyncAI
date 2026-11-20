package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.StudentProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    // Cross-module lookup: Finds a profile using the Auth module's User ID
    Optional<StudentProfile> findByUserId(UUID userId);

    List<StudentProfile> findAllByUserIdIn(Collection<UUID> userIds);

    List<StudentProfile> findAllByIdIn(Collection<UUID> ids);

    @Query("SELECT p FROM StudentProfile p WHERE p.isPublic = true AND p.userId != :currentUserId AND (LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.bio) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.major) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<StudentProfile> searchPublicProfiles(@Param("query") String query, @Param("currentUserId") UUID currentUserId, Pageable pageable);
}
