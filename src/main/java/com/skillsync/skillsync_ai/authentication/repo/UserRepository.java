package com.skillsync.skillsync_ai.authentication.repo;

import com.skillsync.skillsync_ai.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);
}
