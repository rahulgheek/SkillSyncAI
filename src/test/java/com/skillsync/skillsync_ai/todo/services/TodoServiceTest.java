package com.skillsync.skillsync_ai.todo.services;

import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.events.RoadmapGeneratedEvent;
import com.skillsync.skillsync_ai.common.events.TodoCompletedEvent;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.Todo;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import com.skillsync.skillsync_ai.todo.repo.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TodoService todoService;

    private void setUp() {
        todoService = new TodoService(todoRepository, eventPublisher);
    }

    // TC-TODO-001: AI-generated checkpoints are persisted as tasks with their subtasks.
    @Test
    void onRoadmapGenerated_savesTasksWithSubtasksAndSessionId() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Instant dueDate = Instant.now().plusSeconds(86_400);

        List<SubtaskDto> subtasks = List.of(
                new SubtaskDto("sub-1", "Read the docs", false),
                new SubtaskDto("sub-2", "Build a sample", false));

        RoadmapGeneratedEvent event = new RoadmapGeneratedEvent(
                userId,
                roadmapId,
                sessionId,
                "Path to Backend Developer | Topic: Spring Boot",
                List.of(new RoadmapGeneratedEvent.CheckpointPayload(
                        checkpointId,
                        "Build a REST controller",
                        "Create a controller with validation.",
                        (short) 1,
                        dueDate,
                        TaskPriority.HIGH,
                        subtasks)));

        when(todoRepository.findExistingRelatedEntityIds(userId, List.of(checkpointId)))
                .thenReturn(Set.of());
        when(todoRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        todoService.onRoadmapGenerated(event);

        ArgumentCaptor<List<Todo>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoRepository).saveAll(captor.capture());

        List<Todo> saved = captor.getValue();
        assertThat(saved).hasSize(1);

        Todo todo = saved.get(0);
        assertThat(todo.getUserId()).isEqualTo(userId);
        assertThat(todo.getTitle()).isEqualTo("Learn: Build a REST controller");
        assertThat(todo.getDescription()).isEqualTo("Create a controller with validation.");
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.PENDING);
        assertThat(todo.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(todo.getDueDate()).isEqualTo(dueDate);
        assertThat(todo.getRelatedEntityId()).isEqualTo(checkpointId);
        assertThat(todo.getRoadmapSessionId()).isEqualTo(sessionId);
        assertThat(todo.getSourceTitle())
                .isEqualTo("Roadmap: Path to Backend Developer | Topic: Spring Boot");
        assertThat(todo.getSubtasks()).containsExactlyElementsOf(subtasks);
    }

    // TC-TODO-002: Re-delivered events must not create duplicate tasks.
    @Test
    void onRoadmapGenerated_skipsExistingCheckpoints() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        RoadmapGeneratedEvent event = new RoadmapGeneratedEvent(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Roadmap",
                List.of(new RoadmapGeneratedEvent.CheckpointPayload(
                        checkpointId, "Task", "Description", (short) 1,
                        Instant.now(), TaskPriority.MEDIUM, List.of())));

        when(todoRepository.findExistingRelatedEntityIds(userId, List.of(checkpointId)))
                .thenReturn(Set.of(checkpointId));

        todoService.onRoadmapGenerated(event);

        verify(todoRepository, never()).saveAll(anyList());
    }

    // TC-TODO-003: Only new checkpoints are saved when an event is partially replayed.
    @Test
    void onRoadmapGenerated_savesOnlyNewCheckpoints() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        RoadmapGeneratedEvent event = new RoadmapGeneratedEvent(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Roadmap",
                List.of(
                        new RoadmapGeneratedEvent.CheckpointPayload(
                                existingId, "Existing", "Already saved", (short) 1,
                                Instant.now(), TaskPriority.MEDIUM, List.of()),
                        new RoadmapGeneratedEvent.CheckpointPayload(
                                newId, "New", "Not saved yet", (short) 2,
                                Instant.now(), TaskPriority.HIGH, List.of())));

        when(todoRepository.findExistingRelatedEntityIds(userId, List.of(existingId, newId)))
                .thenReturn(Set.of(existingId));
        when(todoRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        todoService.onRoadmapGenerated(event);

        ArgumentCaptor<List<Todo>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRelatedEntityId()).isEqualTo(newId);
    }

    // TC-TODO-004: Completing a task publishes the event that advances the roadmap.
    @Test
    void updateStatus_completed_publishesTodoCompletedEvent() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID todoId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Todo todo = Todo.builder()
                .id(todoId)
                .userId(userId)
                .title("Task")
                .status(TodoStatus.PENDING)
                .relatedEntityId(checkpointId)
                .build();

        when(todoRepository.findByIdAndUserId(todoId, userId)).thenReturn(java.util.Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodoResponse response = todoService.updateStatus(userId, todoId, TodoStatus.COMPLETED);

        assertThat(response.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(todo.getCompletedAt()).isNotNull();

        ArgumentCaptor<TodoCompletedEvent> captor = ArgumentCaptor.forClass(TodoCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().todoId()).isEqualTo(todoId);
        assertThat(captor.getValue().relatedEntityId()).isEqualTo(checkpointId);
    }

    // TC-TODO-005: Re-opening a task clears completedAt and does not publish.
    @Test
    void updateStatus_pending_clearsCompletedAtAndDoesNotPublish() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID todoId = UUID.randomUUID();
        Todo todo = Todo.builder()
                .id(todoId)
                .userId(userId)
                .title("Task")
                .status(TodoStatus.COMPLETED)
                .completedAt(Instant.now())
                .relatedEntityId(UUID.randomUUID())
                .build();

        when(todoRepository.findByIdAndUserId(todoId, userId)).thenReturn(java.util.Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodoResponse response = todoService.updateStatus(userId, todoId, TodoStatus.PENDING);

        assertThat(response.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(todo.getCompletedAt()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    // TC-TODO-006: Subtask toggling persists the new state.
    @Test
    void toggleSubtask_togglesCompletedState() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID todoId = UUID.randomUUID();
        SubtaskDto subtask = new SubtaskDto("sub-1", "Read the docs", false);
        Todo todo = Todo.builder()
                .id(todoId)
                .userId(userId)
                .title("Task")
                .subtasks(List.of(subtask))
                .build();

        when(todoRepository.findByIdAndUserId(todoId, userId)).thenReturn(java.util.Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TodoResponse response = todoService.toggleSubtask(userId, todoId, "sub-1");

        assertThat(response.subtasks()).hasSize(1);
        assertThat(response.subtasks().get(0).isCompleted()).isTrue();
        assertThat(todo.getSubtasks().get(0).isCompleted()).isTrue();
    }

    // TC-TODO-007: The task list can be scoped to a single roadmap session.
    @Test
    void getUserTodos_filtersBySessionAndStatus() {
        setUp();

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Todo todo = Todo.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .roadmapSessionId(sessionId)
                .title("Task")
                .build();
        Page<Todo> page = new PageImpl<>(List.of(todo), pageable, 1);

        when(todoRepository.findAllByUserIdAndRoadmapSessionIdAndStatus(
                userId, sessionId, TodoStatus.PENDING, pageable)).thenReturn(page);

        Page<TodoResponse> result = todoService.getUserTodos(userId, sessionId, TodoStatus.PENDING, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).roadmapSessionId()).isEqualTo(sessionId);
        verify(todoRepository).findAllByUserIdAndRoadmapSessionIdAndStatus(
                userId, sessionId, TodoStatus.PENDING, pageable);
    }

    // TC-TODO-008: Without a session filter the global task query is used.
    @Test
    void getUserTodos_withoutSession_filtersOnlyByStatus() {
        setUp();

        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(), pageable, 0);

        when(todoRepository.findAllByUserIdAndStatus(userId, TodoStatus.PENDING, pageable))
                .thenReturn(page);

        todoService.getUserTodos(userId, null, TodoStatus.PENDING, pageable);

        verify(todoRepository).findAllByUserIdAndStatus(eq(userId), eq(TodoStatus.PENDING), eq(pageable));
        verify(todoRepository, never()).findAllByUserIdAndRoadmapSessionId(
                any(), any(), any());
    }
}
