package com.skillsync.skillsync_ai.authentication.repo;

import com.skillsync.skillsync_ai.authentication.entity.College;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollegeRepository extends JpaRepository<College, UUID> {
    Optional<College> findByEmailDomain(String emailDomain);

    boolean existsByEmailDomainIgnoreCase(String emailDomain);

    Optional<College> findFirstByIsActiveTrueOrderByCreatedAtAsc();
}
