package com.skillsync.skillsync_ai.todo.repo;

import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TodoRepository extends JpaRepository<Todo, UUID> {

    // Fetch all todos for a user, usually sorted by due_date or priority
    Page<Todo> findAllByUserId(UUID userId, Pageable pageable);

    // Filter by status (e.g., show only PENDING or IN_PROGRESS tasks)
    Page<Todo> findAllByUserIdAndStatus(UUID userId, TodoStatus status, Pageable pageable);

    // Secure fetch for single operations
    Optional<Todo> findByIdAndUserId(UUID id, UUID userId);

    // Check if a todo already exists for a specific related entity (e.g., an AI Checkpoint)
    boolean existsByUserIdAndRelatedEntityId(UUID userId, UUID relatedEntityId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT t.relatedEntityId FROM Todo t WHERE t.userId = :userId AND t.relatedEntityId IN :entityIds"
    )
    java.util.Set<UUID> findExistingRelatedEntityIds(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("entityIds") java.util.List<UUID> entityIds
    );

    long countByUserId(UUID userId);
    long countByUserIdAndStatus(UUID userId, TodoStatus status);

    Page<Todo> findAllByUserIdAndStatusIn(UUID userId, List<TodoStatus> statuses, Pageable pageable);
}