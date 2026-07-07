package com.skillsync.skillsync_ai.authentication.repo;

import com.skillsync.skillsync_ai.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
