package com.skillsync.skillsync_ai.ai.controller;

import com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionDetailResponse;
import com.skillsync.skillsync_ai.ai.dto.RoadmapSessionSummaryResponse;
import com.skillsync.skillsync_ai.ai.dto.UpdateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.services.RoadmapSessionService;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.exception.GlobalExceptionHandler;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoadmapSessionControllerTest {

    private RoadmapSessionService service;
    private MockMvc mockMvc;
    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        service = mock(RoadmapSessionService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new RoadmapSessionController(service))
                .setCustomArgumentResolvers(
                        new CurrentUserArgumentResolver(user),
                        new PageableHandlerMethodArgumentResolver(),
                        new SortHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // TC-API-001: Creating a session returns 201 and the session payload.
    @Test
    void createSession_returnsCreated() throws Exception {
        when(service.createSession(eq(userId), any(CreateRoadmapSessionRequest.class)))
                .thenReturn(detailResponse());

        mockMvc.perform(post("/api/v1/roadmaps/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Developer",
                                  "currentKnowledge": "Java",
                                  "knowledgeGaps": "Testing"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.roadmaps[0].checkpoints[0].subtasks[0].title")
                        .value("Read the docs"))
                .andExpect(jsonPath("$.data.tasks[0].subtasks[0].title")
                        .value("Read the docs"));
    }

    // TC-API-002: Bean validation rejects an over-long title before the service runs.
    @Test
    void createSession_validationFailure_returnsBadRequest() throws Exception {
        String longTitle = "x".repeat(201);

        mockMvc.perform(post("/api/v1/roadmaps/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.title").value("Title must be at most 200 characters"));

        verifyNoInteractions(service);
    }

    // TC-API-003: The dashboard endpoint returns the most recently viewed session.
    @Test
    void getRecent_returnsSummary() throws Exception {
        when(service.getMostRecentViewedSession(userId)).thenReturn(summaryResponse());

        mockMvc.perform(get("/api/v1/roadmaps/sessions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.progressPercentage").value(0));
    }

    // TC-API-004: No sessions produces a successful response with null data.
    @Test
    void getRecent_noSession_returnsNullData() throws Exception {
        when(service.getMostRecentViewedSession(userId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/roadmaps/sessions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("No roadmap sessions found"));
    }

    // TC-API-005: Opening a session returns its roadmaps, checkpoints and tasks.
    @Test
    void getSession_returnsDetail() throws Exception {
        when(service.getSessionDetail(userId, sessionId)).thenReturn(detailResponse());

        mockMvc.perform(get("/api/v1/roadmaps/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.roadmaps[0].currentTopic").value("Topic 1"))
                .andExpect(jsonPath("$.data.tasks[0].roadmapSessionId")
                        .value(sessionId.toString()));
    }

    // TC-API-006: A missing or foreign session returns 404.
    @Test
    void getSession_notFound_returnsNotFound() throws Exception {
        when(service.getSessionDetail(userId, sessionId))
                .thenThrow(new ResourceNotFoundException("Roadmap session not found"));

        mockMvc.perform(get("/api/v1/roadmaps/sessions/{sessionId}", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Roadmap session not found"));
    }

    // TC-API-007: The explicit view endpoint returns the updated summary.
    @Test
    void markViewed_returnsSummary() throws Exception {
        when(service.markViewed(userId, sessionId)).thenReturn(summaryResponse());

        mockMvc.perform(post("/api/v1/roadmaps/sessions/{sessionId}/view", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()));
    }

    // TC-API-008: Session tasks include their subtasks.
    @Test
    void getSessionTasks_returnsTasksWithSubtasks() throws Exception {
        when(service.getSessionTasks(userId, sessionId, TodoStatus.PENDING))
                .thenReturn(List.of(taskResponse()));

        mockMvc.perform(get("/api/v1/roadmaps/sessions/{sessionId}/tasks", sessionId)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Learn: Task"))
                .andExpect(jsonPath("$.data[0].subtasks[0].title").value("Read the docs"));
    }

    // TC-API-009: Title and status can be patched.
    @Test
    void updateSession_returnsUpdatedSummary() throws Exception {
        RoadmapSessionSummaryResponse updated = new RoadmapSessionSummaryResponse(
                sessionId, "New title", "Backend Developer", RoadmapSessionStatus.ARCHIVED,
                Instant.now(), Instant.now(), Instant.now(),
                1, 1, 0, 1, 0, 0, UUID.randomUUID(), "Topic 1");

        when(service.updateSession(eq(userId), eq(sessionId), any(UpdateRoadmapSessionRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/roadmaps/sessions/{sessionId}", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New title\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New title"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    // TC-API-010: The session list is paginated.
    @Test
    void getSessions_returnsPage() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(service.getSessions(eq(userId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summaryResponse()), pageable, 1));

        mockMvc.perform(get("/api/v1/roadmaps/sessions")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ---------------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------------

    private RoadmapSessionDetailResponse detailResponse() {
        UUID roadmapId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Instant now = Instant.now();

        RoadmapSessionDetailResponse.CheckpointDetail checkpoint =
                new RoadmapSessionDetailResponse.CheckpointDetail(
                        checkpointId, "Learn: Task", "Task description", (short) 1,
                        CheckpointStatus.PENDING, LocalDate.now().plusDays(1), null,
                        List.of(new SubtaskDto("sub-1", "Read the docs", false)), List.of());

        RoadmapSessionDetailResponse.RoadmapDetail roadmap =
                new RoadmapSessionDetailResponse.RoadmapDetail(
                        roadmapId, "Path to Backend Developer", "Roadmap description",
                        RoadmapStatus.ACTIVE, "Topic 1", List.of(), List.of("Topic 1", "Topic 2"),
                        now, List.of(checkpoint));

        return new RoadmapSessionDetailResponse(
                sessionId, "Path to Backend Developer", "Backend Developer", "Description",
                RoadmapSessionStatus.ACTIVE, now, now, now,
                new RoadmapSessionDetailResponse.SessionProgress(1, 1, 0, 1, 0, 0),
                List.of(roadmap),
                List.of(taskResponse()));
    }

    private RoadmapSessionSummaryResponse summaryResponse() {
        Instant now = Instant.now();
        return new RoadmapSessionSummaryResponse(
                sessionId, "Path to Backend Developer", "Backend Developer",
                RoadmapSessionStatus.ACTIVE, now, now, now,
                1, 1, 0, 1, 0, 0, UUID.randomUUID(), "Topic 1");
    }

    private TodoResponse taskResponse() {
        Instant now = Instant.now();
        return new TodoResponse(
                UUID.randomUUID(), "Learn: Task", "Task description",
                TodoStatus.PENDING, TaskPriority.HIGH, now, null,
                UUID.randomUUID(), sessionId, "Roadmap: Path to Backend Developer",
                now, List.of(new SubtaskDto("sub-1", "Read the docs", false)), List.of());
    }

    private static final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

        private final User user;

        private CurrentUserArgumentResolver(User user) {
            this.user = user;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            return user;
        }
    }
}
