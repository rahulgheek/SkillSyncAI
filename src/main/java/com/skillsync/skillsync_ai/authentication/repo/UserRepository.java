package com.skillsync.skillsync_ai.authentication.repo;

import com.skillsync.skillsync_ai.authentication.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "college")
    Optional<User> findByEmail(String email);

    List<User> findAllByCollegeId(UUID collegeId);

    Optional<User> findFirstByOrderByCreatedAtAsc();

    boolean existsByEmail(String email);

    @Query("SELECT u.id AS id, u.email AS email FROM User u WHERE u.id IN :ids")
    List<UserEmailProjection> findEmailsByIdIn(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = "college")
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findWithCollegeById(@Param("id") UUID id);

    interface UserEmailProjection {
        UUID getId();

        String getEmail();
    }
}
