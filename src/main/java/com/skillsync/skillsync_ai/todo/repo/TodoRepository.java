package com.skillsync.skillsync_ai.todo.repo;

import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TodoRepository extends JpaRepository<Todo, UUID> {

    // Fetch all todos for a user, usually sorted by due_date or priority
    Page<Todo> findAllByUserId(UUID userId, Pageable pageable);

    // Filter by status (e.g., show only PENDING or IN_PROGRESS tasks)
    Page<Todo> findAllByUserIdAndStatus(UUID userId, TodoStatus status, Pageable pageable);

    // Filter the task list down to one AI roadmap session
    Page<Todo> findAllByUserIdAndRoadmapSessionId(UUID userId, UUID roadmapSessionId, Pageable pageable);

    Page<Todo> findAllByUserIdAndRoadmapSessionIdAndStatus(
            UUID userId, UUID roadmapSessionId, TodoStatus status, Pageable pageable);

    List<Todo> findAllByUserIdAndRoadmapSessionIdOrderByDueDateAsc(UUID userId, UUID roadmapSessionId);

    List<Todo> findAllByUserIdAndRoadmapSessionIdAndStatusOrderByDueDateAsc(
            UUID userId, UUID roadmapSessionId, TodoStatus status);

    // Secure fetch for single operations
    Optional<Todo> findByIdAndUserId(UUID id, UUID userId);

    // Check if a todo already exists for a specific related entity (e.g., an AI Checkpoint)
    boolean existsByUserIdAndRelatedEntityId(UUID userId, UUID relatedEntityId);

    @Query("SELECT t.relatedEntityId FROM Todo t WHERE t.userId = :userId AND t.relatedEntityId IN :entityIds")
    Set<UUID> findExistingRelatedEntityIds(@Param("userId") UUID userId, @Param("entityIds") List<UUID> entityIds);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, TodoStatus status);

    Page<Todo> findAllByUserIdAndStatusIn(UUID userId, List<TodoStatus> statuses, Pageable pageable);

    @Query("SELECT t.status AS status, COUNT(t) AS count FROM Todo t WHERE t.userId = :userId GROUP BY t.status")
    List<TodoStatusCount> countByStatusForUser(@Param("userId") UUID userId);

    /**
     * One grouped query for the dashboard/session list instead of two count
     * queries per session.
     */
    @Query("""
            SELECT t.roadmapSessionId AS sessionId,
                   COUNT(t) AS total,
                   SUM(CASE WHEN t.status = :completedStatus THEN 1 ELSE 0 END) AS completed
            FROM Todo t
            WHERE t.userId = :userId AND t.roadmapSessionId IN :sessionIds
            GROUP BY t.roadmapSessionId
            """)
    List<SessionTaskCount> countTasksBySession(
            @Param("userId") UUID userId,
            @Param("sessionIds") Collection<UUID> sessionIds,
            @Param("completedStatus") TodoStatus completedStatus);

    interface TodoStatusCount {
        TodoStatus getStatus();

        long getCount();
    }

    interface SessionTaskCount {
        UUID getSessionId();

        long getTotal();

        Long getCompleted();
    }
}
