package com.skillsync.skillsync_ai.todo.services;

import com.skillsync.skillsync_ai.todo.TodoGateway;
import com.skillsync.skillsync_ai.todo.dto.TodoForAi;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import com.skillsync.skillsync_ai.todo.repo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public TodoMetrics getTodoMetrics(UUID userId) { // CHANGED: Now matches the interface!
        long total = todoRepository.countByUserId(userId);
        long completed = todoRepository.countByUserIdAndStatus(userId, TodoStatus.COMPLETED);
        long pending = todoRepository.countByUserIdAndStatus(userId, TodoStatus.PENDING);
        long inProgress = todoRepository.countByUserIdAndStatus(userId, TodoStatus.IN_PROGRESS);

        int completionPercentage = total == 0 ? 0 : Math.round((completed * 100f) / total);

        // CHANGED: Returning the new TodoMetrics record!
        return new TodoMetrics(total, pending, inProgress, completed, completionPercentage);
    }
}