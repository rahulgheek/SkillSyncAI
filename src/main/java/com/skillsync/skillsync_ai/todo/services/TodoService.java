package com.skillsync.skillsync_ai.todo.services;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.events.RoadmapGeneratedEvent;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.todo.dto.TodoRequest;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import com.skillsync.skillsync_ai.todo.repo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
// CHANGED: Removed "implements TodoGateway"
public class TodoService {

    private final TodoRepository todoRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==========================================
    // STANDARD CRUD OPERATIONS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<TodoResponse> getUserTodos(UUID userId, TodoStatus status, Pageable pageable) {
        if (status != null) {
            return todoRepository.findAllByUserIdAndStatus(userId, status, pageable).map(this::mapToResponse);
        }
        return todoRepository.findAllByUserId(userId, pageable).map(this::mapToResponse);
    }

    @Transactional
    public TodoResponse createTodo(UUID userId, TodoRequest request) {
        Todo todo = Todo.builder()
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .dueDate(request.dueDate())
                .relatedEntityId(request.relatedEntityId())
                .build();
        return mapToResponse(todoRepository.save(todo));
    }

    @Transactional
    public TodoResponse updateTodo(UUID userId, UUID todoId, TodoRequest request) {
        Todo todo = getTodoSafely(userId, todoId);

        todo.setTitle(request.title());
        todo.setDescription(request.description());
        if (request.priority() != null) todo.setPriority(request.priority());
        todo.setDueDate(request.dueDate());

        return mapToResponse(todoRepository.save(todo));
    }

    @Transactional
    public TodoResponse updateStatus(UUID userId, UUID todoId, TodoStatus newStatus) {
        Todo todo = getTodoSafely(userId, todoId);

        todo.setStatus(newStatus);
        if (newStatus == TodoStatus.COMPLETED) {
            todo.setCompletedAt(Instant.now());
        } else {
            todo.setCompletedAt(null);
        }

        Todo savedTodo = todoRepository.save(todo);
        
        if (newStatus == TodoStatus.COMPLETED && savedTodo.getRelatedEntityId() != null) {
            eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.TodoCompletedEvent(
                    userId, savedTodo.getId(), savedTodo.getRelatedEntityId()
            ));
        }

        return mapToResponse(savedTodo);
    }

    @Transactional
    public void deleteTodo(UUID userId, UUID todoId) {
        Todo todo = getTodoSafely(userId, todoId);
        todoRepository.delete(todo);
    }

    private Todo getTodoSafely(UUID userId, UUID todoId) {
        return todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found or access denied."));
    }

    // ==========================================
    // MODULITH EVENT LISTENERS
    // ==========================================

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRoadmapGenerated(RoadmapGeneratedEvent event) {
        log.info("Auto-generating Todos from AI Roadmap for User: {}", event.userId());

        // 1. Extract all incoming checkpoint IDs
        List<UUID> incomingIds = event.checkpoints().stream()
                .map(RoadmapGeneratedEvent.CheckpointPayload::checkpointId)
                .toList();

        // 2. Fetch existing IDs in ONE database query!
        java.util.Set<UUID> existingIds = todoRepository.findExistingRelatedEntityIds(event.userId(), incomingIds);

        List<Todo> todosToSave = new java.util.ArrayList<>();

        for (RoadmapGeneratedEvent.CheckpointPayload cp : event.checkpoints()) {

            // 3. Lightning-fast memory check (O(1) lookup)
            if (existingIds.contains(cp.checkpointId())) {
                continue;
            }

            Todo todo = Todo.builder()
                    .userId(event.userId())
                    .title("Learn: " + cp.title())
                    .description(cp.description())
                    .priority(cp.priority()) // Strict enum transfer, zero parsing!
                    .status(TodoStatus.PENDING)
                    .dueDate(cp.dueDate()) // AI dictates the schedule
                    .relatedEntityId(cp.checkpointId())
                    .sourceTitle("Roadmap: " + event.roadmapTitle())
                    .subtasks(cp.subtasks())
                    .build();

            todosToSave.add(todo);
        }

        if (!todosToSave.isEmpty()) {
            todoRepository.saveAll(todosToSave);
            log.info("Successfully bulk-saved {} tasks for User: {}", todosToSave.size(), event.userId());
        }
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRoadmapDeactivated(com.skillsync.skillsync_ai.common.events.RoadmapDeactivatedEvent event) {
        log.info("Archiving old Todos from deactivated Roadmap for User: {}", event.userId());
        if (event.checkpointIds() == null || event.checkpointIds().isEmpty()) return;
        
        List<Todo> activeTodos = todoRepository.findAllByUserId(event.userId(), Pageable.unpaged())
            .getContent()
            .stream()
            .filter(t -> event.checkpointIds().contains(t.getRelatedEntityId()))
            .toList();
            
        todoRepository.deleteAll(activeTodos);
    }

    private TodoResponse mapToResponse(Todo t) {
        return new TodoResponse(t.getId(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getDueDate(), t.getCompletedAt(),
                t.getRelatedEntityId(), t.getSourceTitle(), t.getCreatedAt(), t.getSubtasks());
    }

    @Transactional
    public TodoResponse toggleSubtask(UUID userId, UUID todoId, String subtaskId) {
        Todo todo = getTodoSafely(userId, todoId);
        if (todo.getSubtasks() != null) {
            for (com.skillsync.skillsync_ai.todo.dto.SubtaskDto st : todo.getSubtasks()) {
                if (st.getId().equals(subtaskId)) {
                    st.setCompleted(!st.isCompleted());
                    break;
                }
            }
            todo = todoRepository.save(todo);
        }
        return mapToResponse(todo);
    }

    // CHANGED: Deleted the getPendingTodosForAi method entirely. It lives in TodoGatewayImpl now!
}