package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionDetailResponse;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionSummaryResponse;
import com.skillsync.skillsync_ai.ai.dto.UpdateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.RoadmapSession;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapSessionRepository;
import com.skillsync.skillsync_ai.common.dto.LearningResourceDto;
import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.todo.TodoGateway;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapSessionServiceTest {

    @Mock
    private RoadmapSessionRepository sessionRepository;

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private CheckpointRepository checkpointRepository;

    @Mock
    private TodoGateway todoGateway;

    @Mock
    private RoadmapService roadmapService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private RoadmapSessionService service;

    @BeforeEach
    void setUp() {
        service = new RoadmapSessionService(
                sessionRepository,
                roadmapRepository,
                checkpointRepository,
                todoGateway,
                roadmapService,
                transactionTemplate,
                new ObjectMapper());
    }

    // TC-SESSION-001: Creating a custom session returns the complete workspace.
    @Test
    void createCustomSession_returnsFullWorkspace() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();

        RoadmapSession session = session(sessionId, userId, "Path to Backend Developer", null);
        Roadmap roadmap = roadmap(session, roadmapId, List.of());
        Checkpoint checkpoint = checkpoint(checkpointId, CheckpointStatus.PENDING);
        TodoResponse task = task(sessionId, checkpointId, TodoStatus.PENDING);

        CreateRoadmapSessionRequest request =
                new CreateRoadmapSessionRequest("My plan", "Backend Developer", "Java", "Spring", null);

        when(roadmapService.generateCustomRoadmapSession(userId, request)).thenReturn(session);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(roadmap));
        when(checkpointRepository.findAllByRoadmapIdOrderByOrderIndexAsc(roadmapId))
                .thenReturn(List.of(checkpoint));
        when(todoGateway.getTasksForSession(userId, sessionId, null)).thenReturn(List.of(task));

        RoadmapSessionDetailResponse response = service.createSession(userId, request);

        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.title()).isEqualTo("Path to Backend Developer");
        assertThat(response.roadmaps()).hasSize(1);
        assertThat(response.roadmaps().get(0).checkpoints()).hasSize(1);
        assertThat(response.tasks()).hasSize(1);
        assertThat(response.progress().checkpointCount()).isEqualTo(1);
        assertThat(response.progress().completedCheckpoints()).isZero();
        assertThat(response.progress().taskCount()).isEqualTo(1);
        assertThat(response.lastViewedAt()).isNotNull();
    }

    // TC-SESSION-002: A skill-gap request is routed to the gap generator.
    @Test
    void createSession_withSkillGap_delegatesToGapGenerator() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID skillGapId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Upskilling for Backend Developer", null);

        CreateRoadmapSessionRequest request =
                new CreateRoadmapSessionRequest(null, null, null, null, skillGapId);

        when(roadmapService.generateRoadmapSessionFromGap(userId, skillGapId)).thenReturn(session);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(todoGateway.getTasksForSession(userId, sessionId, null)).thenReturn(List.of());

        RoadmapSessionDetailResponse response = service.createSession(userId, request);

        assertThat(response.id()).isEqualTo(sessionId);
        verify(roadmapService).generateRoadmapSessionFromGap(userId, skillGapId);
    }

    // TC-SESSION-003: Opening a session updates lastViewedAt and returns subtasks.
    @Test
    void getSessionDetail_marksViewedAndIncludesSubtasks() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Instant oldView = Instant.now().minusSeconds(3600);

        RoadmapSession session = session(sessionId, userId, "Path to Backend Developer", oldView);
        Checkpoint checkpoint = checkpoint(checkpointId, CheckpointStatus.PENDING);
        Roadmap roadmap = roadmap(session, roadmapId, List.of(checkpoint));
        TodoResponse task = task(sessionId, checkpointId, TodoStatus.PENDING);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(roadmap));
        when(checkpointRepository.findAllByRoadmapIdOrderByOrderIndexAsc(roadmapId))
                .thenReturn(List.of(checkpoint));
        when(todoGateway.getTasksForSession(userId, sessionId, null)).thenReturn(List.of(task));

        RoadmapSessionDetailResponse response = service.getSessionDetail(userId, sessionId);

        assertThat(response.lastViewedAt()).isAfter(oldView);
        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().get(0).subtasks()).hasSize(1);
        assertThat(response.tasks().get(0).subtasks().get(0).getTitle()).isEqualTo("Read the docs");
        assertThat(response.roadmaps().get(0).checkpoints().get(0).learningResources()).hasSize(1);
        assertThat(response.progress().progressPercentage()).isZero();
    }

    // TC-SESSION-004: A session owned by another user is reported as not found.
    @Test
    void getSessionDetail_missingSession_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionDetail(userId, sessionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Roadmap session not found");
    }

    // TC-SESSION-005: The dashboard uses the most recently viewed session.
    @Test
    void getMostRecentViewedSession_usesLatestViewed() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Latest viewed", Instant.now());

        when(sessionRepository.findFirstByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(userId))
                .thenReturn(Optional.of(session));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(todoGateway.getTaskMetricsForSessions(userId, List.of(sessionId)))
                .thenReturn(Map.of(sessionId, new TodoGateway.SessionTaskMetrics(2, 1)));

        RoadmapSessionSummaryResponse response = service.getMostRecentViewedSession(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.taskCount()).isEqualTo(2);
        assertThat(response.completedTasks()).isEqualTo(1);
    }

    // TC-SESSION-006: Accounts without a viewed session fall back to the newest session.
    @Test
    void getMostRecentViewedSession_fallsBackToLatestCreated() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Newest created", null);

        when(sessionRepository.findFirstByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(userId))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(session));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(todoGateway.getTaskMetricsForSessions(userId, List.of(sessionId))).thenReturn(Map.of());

        RoadmapSessionSummaryResponse response = service.getMostRecentViewedSession(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(sessionId);
    }

    // TC-SESSION-007: No sessions returns null so the dashboard can render an empty state.
    @Test
    void getMostRecentViewedSession_noSessions_returnsNull() {
        UUID userId = UUID.randomUUID();

        when(sessionRepository.findFirstByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(userId))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.empty());

        assertThat(service.getMostRecentViewedSession(userId)).isNull();
    }

    // TC-SESSION-008: The session list calculates progress from checkpoints.
    @Test
    void getSessions_mapsProgressAndTaskMetrics() {
        UUID userId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        RoadmapSession first = session(firstId, userId, "First", null);
        RoadmapSession second = session(secondId, userId, "Second", null);

        Roadmap firstRoadmap = roadmap(first, UUID.randomUUID(), List.of(
                checkpoint(UUID.randomUUID(), CheckpointStatus.COMPLETED),
                checkpoint(UUID.randomUUID(), CheckpointStatus.PENDING)));
        Roadmap secondRoadmap = roadmap(second, UUID.randomUUID(), List.of(
                checkpoint(UUID.randomUUID(), CheckpointStatus.COMPLETED)));

        Page<RoadmapSession> page = new PageImpl<>(List.of(first, second), pageable, 2);

        when(sessionRepository.findAllByUserId(userId, pageable)).thenReturn(page);
        when(roadmapRepository.findAllBySessionIdIn(List.of(firstId, secondId)))
                .thenReturn(List.of(firstRoadmap, secondRoadmap));
        when(todoGateway.getTaskMetricsForSessions(userId, List.of(firstId, secondId)))
                .thenReturn(Map.of(
                        firstId, new TodoGateway.SessionTaskMetrics(2, 1),
                        secondId, new TodoGateway.SessionTaskMetrics(1, 1)));

        Page<RoadmapSessionSummaryResponse> response = service.getSessions(userId, null, pageable);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent()).hasSize(2);

        RoadmapSessionSummaryResponse firstSummary = response.getContent().get(0);
        assertThat(firstSummary.progressPercentage()).isEqualTo(50);
        assertThat(firstSummary.checkpointCount()).isEqualTo(2);
        assertThat(firstSummary.completedCheckpoints()).isEqualTo(1);
        assertThat(firstSummary.taskCount()).isEqualTo(2);
        assertThat(firstSummary.completedTasks()).isEqualTo(1);

        assertThat(response.getContent().get(1).progressPercentage()).isEqualTo(100);
    }

    // TC-SESSION-009: Tasks can be filtered by status for a single session.
    @Test
    void getSessionTasks_filtersByStatus() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Session", null);
        TodoResponse task = task(sessionId, UUID.randomUUID(), TodoStatus.PENDING);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(todoGateway.getTasksForSession(userId, sessionId, TodoStatus.PENDING))
                .thenReturn(List.of(task));

        List<TodoResponse> response = service.getSessionTasks(userId, sessionId, TodoStatus.PENDING);

        assertThat(response).hasSize(1);
        verify(todoGateway).getTasksForSession(userId, sessionId, TodoStatus.PENDING);
    }

    // TC-SESSION-010: Title and status can be updated.
    @Test
    void updateSession_updatesTitleAndStatus() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Old title", null);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(todoGateway.getTaskMetricsForSessions(userId, List.of(sessionId))).thenReturn(Map.of());

        RoadmapSessionSummaryResponse response = service.updateSession(
                userId, sessionId, new UpdateRoadmapSessionRequest("New title", RoadmapSessionStatus.ARCHIVED));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.status()).isEqualTo(RoadmapSessionStatus.ARCHIVED);
    }

    // TC-SESSION-011: A blank title is ignored rather than overwriting the session.
    @Test
    void updateSession_blankTitle_keepsOriginal() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Original title", null);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(todoGateway.getTaskMetricsForSessions(userId, List.of(sessionId))).thenReturn(Map.of());

        RoadmapSessionSummaryResponse response = service.updateSession(
                userId, sessionId, new UpdateRoadmapSessionRequest("   ", null));

        assertThat(response.title()).isEqualTo("Original title");
    }

    // TC-SESSION-012: Invalid curriculum JSON degrades to an empty list instead of failing.
    @Test
    void getSessionDetail_invalidCurriculumJson_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        RoadmapSession session = session(sessionId, userId, "Session", null);
        Roadmap roadmap = roadmap(session, roadmapId, List.of());
        roadmap.setCurriculum("not-json");

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RoadmapSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(roadmap));
        when(checkpointRepository.findAllByRoadmapIdOrderByOrderIndexAsc(roadmapId)).thenReturn(List.of());
        when(todoGateway.getTasksForSession(userId, sessionId, null)).thenReturn(List.of());

        RoadmapSessionDetailResponse response = service.getSessionDetail(userId, sessionId);

        assertThat(response.roadmaps().get(0).curriculum()).isEmpty();
        assertThat(response.roadmaps().get(0).completedTopics()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------------

    private RoadmapSession session(UUID id, UUID userId, String title, Instant lastViewedAt) {
        return RoadmapSession.builder()
                .id(id)
                .userId(userId)
                .studentProfileId(UUID.randomUUID())
                .title(title)
                .targetRole("Backend Developer")
                .description("Description")
                .status(RoadmapSessionStatus.ACTIVE)
                .lastViewedAt(lastViewedAt)
                .createdAt(Instant.now().minusSeconds(120))
                .updatedAt(Instant.now().minusSeconds(60))
                .build();
    }

    private Roadmap roadmap(RoadmapSession session, UUID id, List<Checkpoint> checkpoints) {
        Roadmap roadmap = Roadmap.builder()
                .id(id)
                .userId(session.getUserId())
                .studentProfileId(session.getStudentProfileId())
                .session(session)
                .title("Path to Backend Developer")
                .description("Roadmap description")
                .status(RoadmapStatus.ACTIVE)
                .currentTopic("Topic 1")
                .curriculum("[\"Topic 1\", \"Topic 2\"]")
                .completedTopics("[]")
                .createdAt(Instant.now().minusSeconds(90))
                .checkpoints(checkpoints)
                .build();
        checkpoints.forEach(checkpoint -> checkpoint.setRoadmap(roadmap));
        return roadmap;
    }

    private Checkpoint checkpoint(UUID id, CheckpointStatus status) {
        return Checkpoint.builder()
                .id(id)
                .title("Checkpoint")
                .description("Checkpoint description")
                .orderIndex((short) 1)
                .status(status)
                .dueDate(LocalDate.now().plusDays(1))
                .subtasks(List.of(new SubtaskDto("sub-1", "Read the docs", false)))
                .learningResources(List.of(new LearningResourceDto(
                        "Spring Boot Tutorial", "freeCodeCamp.org",
                        "https://www.youtube.com/watch?v=abc123", "YOUTUBE_VIDEO",
                        "Popular full course", "1h 20m")))
                .build();
    }

    private TodoResponse task(UUID sessionId, UUID checkpointId, TodoStatus status) {
        return new TodoResponse(
                UUID.randomUUID(),
                "Learn: Checkpoint",
                "Task description",
                status,
                TaskPriority.HIGH,
                Instant.now().plusSeconds(86_400),
                null,
                checkpointId,
                sessionId,
                "Roadmap: Path to Backend Developer",
                Instant.now(),
                List.of(new SubtaskDto("sub-1", "Read the docs", false)),
                List.of());
    }
}
