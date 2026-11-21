package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.RoadmapSession;
import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.entity.SkillGapItem;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.gemini.GeminiResponse;
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapSessionRepository;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import com.skillsync.skillsync_ai.chat.ChatGateway;
import com.skillsync.skillsync_ai.common.events.RoadmapDeactivatedEvent;
import com.skillsync.skillsync_ai.common.events.RoadmapGeneratedEvent;
import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private CheckpointRepository checkpointRepository;

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private SkillGapAnalysisRepository skillGapRepository;

    @Mock
    private GeminiApiService geminiApiService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private UserProfileGateway userProfileGateway;

    @Mock
    private ChatGateway chatGateway;

    private RoadmapService roadmapService;

    @BeforeEach
    void setUp() {
        roadmapService = new RoadmapService(
                roadmapRepository,
                checkpointRepository,
                roadmapSessionRepository,
                skillGapRepository,
                geminiApiService,
                eventPublisher,
                transactionTemplate,
                userProfileGateway,
                chatGateway);
    }

    // TC-ROADMAP-001: A skill-gap session persists the session, roadmap,
    // checkpoint and subtasks, and emits the task-creation event.
    @Test
    void generateRoadmapSessionFromGap_createsSessionAndSubtasks() {
        UUID userId = UUID.randomUUID();
        UUID gapId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        SkillGapAnalysis gap = SkillGapAnalysis.builder()
                .id(gapId)
                .userId(userId)
                .studentProfileId(profileId)
                .targetRole("Backend Developer")
                .missingSkills(List.of(new SkillGapItem("Docker", "CRITICAL")))
                .build();

        AtomicReference<RoadmapSession> sessionRef = new AtomicReference<>();
        AtomicReference<Roadmap> roadmapRef = new AtomicReference<>();

        when(skillGapRepository.findById(gapId)).thenReturn(Optional.of(gap));
        when(roadmapSessionRepository.save(any(RoadmapSession.class))).thenAnswer(invocation -> {
            RoadmapSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            sessionRef.set(session);
            return session;
        });
        when(roadmapRepository.save(any(Roadmap.class))).thenAnswer(invocation -> {
            Roadmap roadmap = invocation.getArgument(0);
            if (roadmap.getId() == null) roadmap.setId(UUID.randomUUID());
            roadmapRef.set(roadmap);
            return roadmap;
        });
        when(roadmapRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(roadmapRef.get()));
        when(roadmapSessionRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionRef.get()));
        when(checkpointRepository.save(any(Checkpoint.class))).thenAnswer(invocation -> {
            Checkpoint checkpoint = invocation.getArgument(0);
            if (checkpoint.getId() == null) checkpoint.setId(UUID.randomUUID());
            return checkpoint;
        });

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executeWithoutResultImmediately();

        when(geminiApiService.generateRoadmap(anyString()))
                .thenReturn(Mono.just(geminiResponse("[]")));
        when(geminiApiService.parseRoadmapSteps(anyString())).thenReturn(List.of(Map.of(
                "title", "Build a REST controller",
                "description", "Create a controller with validation.",
                "subtasks", List.of("Read the docs", "Build a sample", "Add validation"))));

        RoadmapSession session = roadmapService.generateRoadmapSessionFromGap(userId, gapId);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getTitle()).isEqualTo("Upskilling for Backend Developer");
        assertThat(session.getStatus()).isEqualTo(RoadmapSessionStatus.ACTIVE);
        assertThat(session.getLastViewedAt()).isNotNull();

        Roadmap roadmap = roadmapRef.get();
        assertThat(roadmap).isNotNull();
        assertThat(roadmap.getSession()).isEqualTo(session);
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.ACTIVE);

        ArgumentCaptor<Checkpoint> checkpointCaptor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointRepository).save(checkpointCaptor.capture());
        Checkpoint checkpoint = checkpointCaptor.getValue();
        assertThat(checkpoint.getSubtasks()).hasSize(3);
        assertThat(checkpoint.getSubtasks()).extracting("title")
                .containsExactly("Read the docs", "Build a sample", "Add validation");

        ArgumentCaptor<RoadmapGeneratedEvent> eventCaptor =
                ArgumentCaptor.forClass(RoadmapGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RoadmapGeneratedEvent event = eventCaptor.getValue();
        assertThat(event.sessionId()).isEqualTo(session.getId());
        assertThat(event.roadmapId()).isEqualTo(roadmap.getId());
        assertThat(event.checkpoints()).hasSize(1);
        assertThat(event.checkpoints().get(0).subtasks()).hasSize(3);
    }

    // TC-ROADMAP-002: A failed AI call marks both the roadmap and the session FAILED.
    @Test
    void generateRoadmapSessionFromGap_aiFailure_marksSessionFailed() {
        UUID userId = UUID.randomUUID();
        UUID gapId = UUID.randomUUID();

        SkillGapAnalysis gap = SkillGapAnalysis.builder()
                .id(gapId)
                .userId(userId)
                .studentProfileId(UUID.randomUUID())
                .targetRole("Backend Developer")
                .missingSkills(List.of(new SkillGapItem("Docker", "CRITICAL")))
                .build();

        AtomicReference<RoadmapSession> sessionRef = new AtomicReference<>();
        AtomicReference<Roadmap> roadmapRef = new AtomicReference<>();

        when(skillGapRepository.findById(gapId)).thenReturn(Optional.of(gap));
        when(roadmapSessionRepository.save(any(RoadmapSession.class))).thenAnswer(invocation -> {
            RoadmapSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            sessionRef.set(session);
            return session;
        });
        when(roadmapRepository.save(any(Roadmap.class))).thenAnswer(invocation -> {
            Roadmap roadmap = invocation.getArgument(0);
            if (roadmap.getId() == null) roadmap.setId(UUID.randomUUID());
            roadmapRef.set(roadmap);
            return roadmap;
        });
        when(roadmapRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(roadmapRef.get()));
        when(roadmapSessionRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionRef.get()));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executeWithoutResultImmediately();

        when(geminiApiService.generateRoadmap(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Gemini unavailable")));

        RoadmapSession session = roadmapService.generateRoadmapSessionFromGap(userId, gapId);

        assertThat(session.getStatus()).isEqualTo(RoadmapSessionStatus.FAILED);
        assertThat(roadmapRef.get().getStatus()).isEqualTo(RoadmapStatus.FAILED);
    }

    // TC-ROADMAP-003: A custom session stores the AI-recommended role and
    // attaches the generated roadmap and event to that session.
    @Test
    void generateCustomRoadmapSession_createsSessionWithRecommendedRole() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        AtomicReference<RoadmapSession> sessionRef = new AtomicReference<>();
        AtomicReference<Roadmap> roadmapRef = new AtomicReference<>();

        when(userProfileGateway.getUserProfile(userId))
                .thenReturn(Optional.of(new UserProfileGateway.UserProfileContext(userId, "Student", null)));
        when(userProfileGateway.getUserWorkContext(userId))
                .thenReturn(Optional.of(new UserProfileGateway.UserProfileWorkContext(
                        profileId, List.of("Java", "SQL"), List.of("Library API"))));
        when(chatGateway.getRecentChatHistory(userId, 50)).thenReturn(List.of("How do I test Spring Boot?"));

        when(geminiApiService.analyzeChatHistoryForRoadmap(anyString()))
                .thenReturn(Mono.just(geminiResponse("{\"insights\":\"Testing interest\"}")));
        when(geminiApiService.generateCurriculum(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(geminiResponse("{\"recommendedRole\":\"Backend Developer\",\"curriculum\":[\"Topic 1\",\"Topic 2\"]}")));
        when(geminiApiService.generateTopicTasks(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(geminiResponse("[]")));

        when(geminiApiService.parseJsonToMap(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            if (json.contains("insights")) {
                return Map.of("insights", "Testing interest");
            }
            return Map.of(
                    "recommendedRole", "Backend Developer",
                    "curriculum", List.of("Topic 1", "Topic 2"));
        });
        when(geminiApiService.parseRoadmapSteps(anyString())).thenReturn(List.of(Map.of(
                "title", "Learn Spring Boot testing",
                "description", "Write a slice test.",
                "subtasks", List.of("Read the testing docs", "Write one test"))));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executeWithoutResultImmediately();

        when(roadmapSessionRepository.save(any(RoadmapSession.class))).thenAnswer(invocation -> {
            RoadmapSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            sessionRef.set(session);
            return session;
        });
        when(roadmapRepository.save(any(Roadmap.class))).thenAnswer(invocation -> {
            Roadmap roadmap = invocation.getArgument(0);
            if (roadmap.getId() == null) roadmap.setId(UUID.randomUUID());
            roadmapRef.set(roadmap);
            return roadmap;
        });
        when(roadmapSessionRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionRef.get()));
        when(checkpointRepository.save(any(Checkpoint.class))).thenAnswer(invocation -> {
            Checkpoint checkpoint = invocation.getArgument(0);
            if (checkpoint.getId() == null) checkpoint.setId(UUID.randomUUID());
            return checkpoint;
        });

        CreateRoadmapSessionRequest request =
                new CreateRoadmapSessionRequest(null, "Backend Developer", "Java", "Testing", null);

        RoadmapSession session = roadmapService.generateCustomRoadmapSession(userId, request);

        assertThat(session.getTitle()).isEqualTo("Path to Backend Developer");
        assertThat(session.getTargetRole()).isEqualTo("Backend Developer");
        assertThat(session.getStudentProfileId()).isEqualTo(profileId);

        Roadmap roadmap = roadmapRef.get();
        assertThat(roadmap).isNotNull();
        assertThat(roadmap.getSession()).isEqualTo(session);
        assertThat(roadmap.getCurrentTopic()).isEqualTo("Topic 1");
        assertThat(roadmap.getCurriculum()).contains("Topic 1");

        ArgumentCaptor<RoadmapGeneratedEvent> eventCaptor =
                ArgumentCaptor.forClass(RoadmapGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(session.getId());
    }

    // TC-ROADMAP-004: Creating a second session leaves the first session and
    // its roadmap untouched, so users can keep multiple AI roadmaps.
    @Test
    void generateCustomRoadmapSession_createsMultipleSessionsWithoutDeactivatingPrevious() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        Map<UUID, RoadmapSession> sessionsById = new HashMap<>();
        List<Roadmap> savedRoadmaps = new ArrayList<>();

        when(userProfileGateway.getUserProfile(userId))
                .thenReturn(Optional.of(new UserProfileGateway.UserProfileContext(userId, "Student", null)));
        when(userProfileGateway.getUserWorkContext(userId))
                .thenReturn(Optional.of(new UserProfileGateway.UserProfileWorkContext(
                        profileId, List.of("Java"), List.of("Library API"))));
        when(chatGateway.getRecentChatHistory(userId, 50)).thenReturn(List.of());

        when(geminiApiService.generateCurriculum(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(geminiResponse(
                        "{\"recommendedRole\":\"Backend Developer\",\"curriculum\":[\"Topic 1\",\"Topic 2\"]}")));
        when(geminiApiService.generateTopicTasks(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(geminiResponse("[]")));
        when(geminiApiService.parseJsonToMap(anyString())).thenReturn(Map.of(
                "recommendedRole", "Backend Developer",
                "curriculum", List.of("Topic 1", "Topic 2")));
        when(geminiApiService.parseRoadmapSteps(anyString())).thenReturn(List.of(Map.of(
                "title", "Task",
                "description", "Description",
                "subtasks", List.of("Subtask"))));

        when(roadmapSessionRepository.save(any(RoadmapSession.class))).thenAnswer(invocation -> {
            RoadmapSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            sessionsById.put(session.getId(), session);
            return session;
        });
        when(roadmapSessionRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionsById.get(invocation.getArgument(0))));
        when(roadmapRepository.save(any(Roadmap.class))).thenAnswer(invocation -> {
            Roadmap roadmap = invocation.getArgument(0);
            if (roadmap.getId() == null) roadmap.setId(UUID.randomUUID());
            savedRoadmaps.add(roadmap);
            return roadmap;
        });
        when(checkpointRepository.save(any(Checkpoint.class))).thenAnswer(invocation -> {
            Checkpoint checkpoint = invocation.getArgument(0);
            if (checkpoint.getId() == null) checkpoint.setId(UUID.randomUUID());
            return checkpoint;
        });

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executeWithoutResultImmediately();

        CreateRoadmapSessionRequest request =
                new CreateRoadmapSessionRequest("My journey", "Backend Developer", "Java", "Testing", null);

        RoadmapSession first = roadmapService.generateCustomRoadmapSession(userId, request);
        RoadmapSession second = roadmapService.generateCustomRoadmapSession(userId, request);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(sessionsById).hasSize(2);
        assertThat(first.getStatus()).isEqualTo(RoadmapSessionStatus.ACTIVE);
        assertThat(second.getStatus()).isEqualTo(RoadmapSessionStatus.ACTIVE);

        assertThat(savedRoadmaps).hasSize(2);
        assertThat(savedRoadmaps).extracting(Roadmap::getSession)
                .containsExactly(first, second);
        verify(eventPublisher, never()).publishEvent(any(RoadmapDeactivatedEvent.class));
    }

    private void executeWithoutResultImmediately() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private GeminiResponse geminiResponse(String text) {
        GeminiResponse response = new GeminiResponse();
        GeminiRequest.Content content = GeminiRequest.Content.builder()
                .role("model")
                .parts(List.of(GeminiRequest.Part.builder().text(text).build()))
                .build();
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate();
        candidate.setContent(content);
        response.setCandidates(List.of(candidate));
        return response;
    }
}
