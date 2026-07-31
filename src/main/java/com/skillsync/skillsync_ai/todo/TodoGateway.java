package com.skillsync.skillsync_ai.todo;

import com.skillsync.skillsync_ai.todo.dto.TodoForAi;
import java.util.List;
import java.util.UUID;

public interface TodoGateway {
    List<TodoForAi> getActiveTodosForAi(UUID userId); // Renamed for semantic accuracy
    TodoMetrics getTodoMetrics(UUID userId); // Renamed to Metrics

    record TodoMetrics(long total, long pending, long inProgress, long completed, int completionPercentage) {}
}