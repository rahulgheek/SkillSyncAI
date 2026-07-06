package com.skillsync.skillsync_ai.authentication.repo;

import com.skillsync.skillsync_ai.authentication.entity.College;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollegeRepository extends JpaRepository<College,Long> {
    Optional<College> findByEmailDomain(String emailDomain);
}
