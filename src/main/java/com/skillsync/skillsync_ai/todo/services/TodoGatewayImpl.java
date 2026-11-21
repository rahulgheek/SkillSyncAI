package com.skillsync.skillsync_ai.todo.services;

import com.skillsync.skillsync_ai.todo.TodoGateway;
import com.skillsync.skillsync_ai.todo.dto.TodoForAi;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import com.skillsync.skillsync_ai.todo.repo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoGatewayImpl implements TodoGateway {

    private final TodoRepository todoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TodoForAi> getActiveTodosForAi(UUID userId) {
        List<TodoStatus> activeStatuses = List.of(TodoStatus.PENDING, TodoStatus.IN_PROGRESS);

        return todoRepository.findAllByUserIdAndStatusIn(userId, activeStatuses, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(todo -> new TodoForAi(todo.getTitle(), todo.getPriority(), todo.getDueDate()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TodoMetrics getTodoMetrics(UUID userId) {
        // One grouped query instead of four COUNT round-trips.
        long pending = 0;
        long inProgress = 0;
        long completed = 0;
        long total = 0;

        for (var row : todoRepository.countByStatusForUser(userId)) {
            long count = row.getCount();
            total += count;
            switch (row.getStatus()) {
                case PENDING -> pending = count;
                case IN_PROGRESS -> inProgress = count;
                case COMPLETED -> completed = count;
                default -> { }
            }
        }

        int completionPercentage = total == 0 ? 0 : Math.round((completed * 100f) / total);
        return new TodoMetrics(total, pending, inProgress, completed, completionPercentage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TodoResponse> getTasksForSession(UUID userId, UUID sessionId, TodoStatus status) {
        List<Todo> todos = status == null
                ? todoRepository.findAllByUserIdAndRoadmapSessionIdOrderByDueDateAsc(userId, sessionId)
                : todoRepository.findAllByUserIdAndRoadmapSessionIdAndStatusOrderByDueDateAsc(userId, sessionId, status);

        return todos.stream().map(TodoResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SessionTaskMetrics> getTaskMetricsForSessions(UUID userId, Collection<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, SessionTaskMetrics> metrics = new HashMap<>();
        for (var row : todoRepository.countTasksBySession(userId, sessionIds, TodoStatus.COMPLETED)) {
            long completed = row.getCompleted() == null ? 0L : row.getCompleted();
            metrics.put(row.getSessionId(), new SessionTaskMetrics(row.getTotal(), completed));
        }
        return metrics;
    }
}
