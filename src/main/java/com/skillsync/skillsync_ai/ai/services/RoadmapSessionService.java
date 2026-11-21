package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapSessionRepository;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.todo.TodoGateway;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapSessionService {

    private final RoadmapSessionRepository sessionRepository;
    private final RoadmapRepository roadmapRepository;
    private final CheckpointRepository checkpointRepository;
    private final TodoGateway todoGateway;
    private final RoadmapService roadmapService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates a session (custom or from a skill gap) and immediately returns
     * the full workspace. No long-running AI work happens inside a database
     * transaction.
     */
    public RoadmapSessionDetailResponse createSession(UUID userId, CreateRoadmapSessionRequest request) {
        RoadmapSession session = request.skillGapId() != null
                ? roadmapService.generateRoadmapSessionFromGap(userId, request.skillGapId())
                : roadmapService.generateCustomRoadmapSession(userId, request);

        // Self-invocation would bypass the @Transactional proxy, so use the
        // template explicitly for the post-generation read.
        return transactionTemplate.execute(status -> loadDetailAndMarkViewed(userId, session.getId()));
    }

    /**
     * Opening a session updates {@code lastViewedAt}, which is what the
     * dashboard uses to decide which session to show.
     */
    @Transactional
    public RoadmapSessionDetailResponse getSessionDetail(UUID userId, UUID sessionId) {
        return loadDetailAndMarkViewed(userId, sessionId);
    }

    @Transactional
    public RoadmapSessionSummaryResponse markViewed(UUID userId, UUID sessionId) {
        RoadmapSession session = getOwnedSession(userId, sessionId);
        session.setLastViewedAt(Instant.now());
        session = sessionRepository.save(session);

        List<Roadmap> roadmaps = loadRoadmaps(session.getId());
        Map<UUID, TodoGateway.SessionTaskMetrics> metrics =
                todoGateway.getTaskMetricsForSessions(userId, List.of(session.getId()));

        return toSummary(session, roadmaps, metrics == null ? null : metrics.get(session.getId()));
    }

    /**
     * Dashboard query: the most recently viewed session, falling back to the
     * most recently created one for accounts that have never opened a session.
     */
    @Transactional(readOnly = true)
    public RoadmapSessionSummaryResponse getMostRecentViewedSession(UUID userId) {
        Optional<RoadmapSession> recent =
                sessionRepository.findFirstByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(userId);

        if (recent.isEmpty()) {
            recent = sessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        }

        if (recent.isEmpty()) {
            return null;
        }

        RoadmapSession session = recent.get();
        List<Roadmap> roadmaps = loadRoadmaps(session.getId());
        Map<UUID, TodoGateway.SessionTaskMetrics> metrics =
                todoGateway.getTaskMetricsForSessions(userId, List.of(session.getId()));

        return toSummary(session, roadmaps, metrics == null ? null : metrics.get(session.getId()));
    }

    @Transactional(readOnly = true)
    public Page<RoadmapSessionSummaryResponse> getSessions(
            UUID userId, RoadmapSessionStatus status, Pageable pageable) {

        Page<RoadmapSession> sessions = status == null
                ? sessionRepository.findAllByUserId(userId, pageable)
                : sessionRepository.findAllByUserIdAndStatus(userId, status, pageable);

        List<UUID> sessionIds = sessions.getContent().stream().map(RoadmapSession::getId).toList();
        Map<UUID, List<Roadmap>> roadmapsBySession = loadRoadmapsBySession(sessionIds);
        Map<UUID, TodoGateway.SessionTaskMetrics> metrics =
                todoGateway.getTaskMetricsForSessions(userId, sessionIds);

        return sessions.map(session -> toSummary(
                session,
                roadmapsBySession.getOrDefault(session.getId(), List.of()),
                metrics == null ? null : metrics.get(session.getId())));
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> getSessionTasks(UUID userId, UUID sessionId, TodoStatus status) {
        getOwnedSession(userId, sessionId);
        return todoGateway.getTasksForSession(userId, sessionId, status);
    }

    @Transactional
    public RoadmapSessionSummaryResponse updateSession(
            UUID userId, UUID sessionId, UpdateRoadmapSessionRequest request) {

        RoadmapSession session = getOwnedSession(userId, sessionId);

        if (request.title() != null && !request.title().isBlank()) {
            session.setTitle(request.title().trim());
        }
        if (request.status() != null) {
            session.setStatus(request.status());
        }

        session = sessionRepository.save(session);
        List<Roadmap> roadmaps = loadRoadmaps(session.getId());
        Map<UUID, TodoGateway.SessionTaskMetrics> metrics =
                todoGateway.getTaskMetricsForSessions(userId, List.of(session.getId()));

        return toSummary(session, roadmaps, metrics == null ? null : metrics.get(session.getId()));
    }

    private RoadmapSessionDetailResponse loadDetailAndMarkViewed(UUID userId, UUID sessionId) {
        RoadmapSession session = getOwnedSession(userId, sessionId);
        session.setLastViewedAt(Instant.now());
        session = sessionRepository.save(session);

        List<Roadmap> roadmaps = loadRoadmaps(session.getId());
        List<RoadmapSessionDetailResponse.RoadmapDetail> roadmapDetails = roadmaps.stream()
                .map(this::toRoadmapDetail)
                .toList();

        List<TodoResponse> tasks = todoGateway.getTasksForSession(userId, session.getId(), null);

        int checkpointCount = roadmapDetails.stream()
                .mapToInt(detail -> detail.checkpoints().size())
                .sum();
        int completedCheckpoints = roadmapDetails.stream()
                .flatMap(detail -> detail.checkpoints().stream())
                .mapToInt(checkpoint -> checkpoint.status() == CheckpointStatus.COMPLETED ? 1 : 0)
                .sum();
        int completedTasks = (int) tasks.stream()
                .filter(task -> task.status() == TodoStatus.COMPLETED)
                .count();
        int progressPercentage = checkpointCount == 0
                ? 0
                : Math.round(completedCheckpoints * 100f / checkpointCount);

        RoadmapSessionDetailResponse.SessionProgress progress =
                new RoadmapSessionDetailResponse.SessionProgress(
                        roadmapDetails.size(),
                        checkpointCount,
                        completedCheckpoints,
                        tasks.size(),
                        completedTasks,
                        progressPercentage);

        return new RoadmapSessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getTargetRole(),
                session.getDescription(),
                session.getStatus(),
                session.getLastViewedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                progress,
                roadmapDetails,
                tasks);
    }

    private RoadmapSessionDetailResponse.RoadmapDetail toRoadmapDetail(Roadmap roadmap) {
        List<Checkpoint> checkpoints =
                checkpointRepository.findAllByRoadmapIdOrderByOrderIndexAsc(roadmap.getId());

        List<RoadmapSessionDetailResponse.CheckpointDetail> checkpointDetails = checkpoints.stream()
                .map(checkpoint -> new RoadmapSessionDetailResponse.CheckpointDetail(
                        checkpoint.getId(),
                        checkpoint.getTitle(),
                        checkpoint.getDescription(),
                        checkpoint.getOrderIndex(),
                        checkpoint.getStatus(),
                        checkpoint.getDueDate(),
                        checkpoint.getCompletedAt(),
                        checkpoint.getSubtasks() == null ? List.of() : checkpoint.getSubtasks()))
                .toList();

        return new RoadmapSessionDetailResponse.RoadmapDetail(
                roadmap.getId(),
                roadmap.getTitle(),
                roadmap.getDescription(),
                roadmap.getStatus(),
                roadmap.getCurrentTopic(),
                parseStringList(roadmap.getCompletedTopics()),
                parseStringList(roadmap.getCurriculum()),
                roadmap.getCreatedAt(),
                checkpointDetails);
    }

    private RoadmapSessionSummaryResponse toSummary(
            RoadmapSession session,
            List<Roadmap> roadmaps,
            TodoGateway.SessionTaskMetrics metrics) {

        int checkpointCount = 0;
        int completedCheckpoints = 0;

        for (Roadmap roadmap : roadmaps) {
            if (roadmap.getCheckpoints() == null) continue;
            checkpointCount += roadmap.getCheckpoints().size();
            completedCheckpoints += (int) roadmap.getCheckpoints().stream()
                    .filter(checkpoint -> checkpoint.getStatus() == CheckpointStatus.COMPLETED)
                    .count();
        }

        Roadmap latestRoadmap = roadmaps.stream()
                .max(Comparator.comparing(
                        Roadmap::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        int taskCount = metrics == null ? 0 : (int) metrics.total();
        int completedTasks = metrics == null ? 0 : (int) metrics.completed();
        int progressPercentage = checkpointCount == 0
                ? 0
                : Math.round(completedCheckpoints * 100f / checkpointCount);

        return new RoadmapSessionSummaryResponse(
                session.getId(),
                session.getTitle(),
                session.getTargetRole(),
                session.getStatus(),
                session.getLastViewedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                roadmaps.size(),
                checkpointCount,
                completedCheckpoints,
                taskCount,
                completedTasks,
                progressPercentage,
                latestRoadmap != null ? latestRoadmap.getId() : null,
                latestRoadmap != null ? latestRoadmap.getCurrentTopic() : null);
    }

    private RoadmapSession getOwnedSession(UUID userId, UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap session not found"));
    }

    private List<Roadmap> loadRoadmaps(UUID sessionId) {
        return roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private Map<UUID, List<Roadmap>> loadRoadmapsBySession(Collection<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return roadmapRepository.findAllBySessionIdIn(sessionIds).stream()
                .collect(Collectors.groupingBy(roadmap -> roadmap.getSession().getId()));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse roadmap JSON list: {}", e.getMessage());
            return List.of();
        }
    }
}
