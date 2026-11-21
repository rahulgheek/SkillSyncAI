package com.skillsync.skillsync_ai.todo;

import com.skillsync.skillsync_ai.todo.dto.TodoForAi;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TodoGateway {
    List<TodoForAi> getActiveTodosForAi(UUID userId); // Renamed for semantic accuracy
    TodoMetrics getTodoMetrics(UUID userId); // Renamed to Metrics

    List<TodoResponse> getTasksForSession(UUID userId, UUID sessionId, TodoStatus status);

    Map<UUID, SessionTaskMetrics> getTaskMetricsForSessions(UUID userId, Collection<UUID> sessionIds);

    record TodoMetrics(long total, long pending, long inProgress, long completed, int completionPercentage) {}

    record SessionTaskMetrics(long total, long completed) {}
}
