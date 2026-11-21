package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.dto.CreateRoadmapSessionRequest;
import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.RoadmapSession;
import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.entity.SkillGapItem;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapSessionRepository;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import com.skillsync.skillsync_ai.chat.ChatGateway;
import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.events.ProfileUpdatedEvent;
import com.skillsync.skillsync_ai.common.events.RoadmapGeneratedEvent;
import com.skillsync.skillsync_ai.common.events.TodoCompletedEvent;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.common.exception.UnauthorizedActionException;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final CheckpointRepository checkpointRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final SkillGapAnalysisRepository skillGapRepository;
    private final GeminiApiService geminiApiService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final UserProfileGateway userProfileGateway;
    private final ChatGateway chatGateway;

    /**
     * Creates a roadmap session from an existing skill-gap analysis. All Gemini
     * calls happen between short database transactions so a Neon connection is
     * never held for the duration of the AI request.
     */
    public RoadmapSession generateRoadmapSessionFromGap(UUID userId, UUID skillGapId) {
        RoadmapInit init = transactionTemplate.execute(status -> {
            SkillGapAnalysis gap = skillGapRepository.findById(skillGapId)
                    .orElseThrow(() -> new ResourceNotFoundException("Skill gap analysis not found"));

            if (!gap.getUserId().equals(userId)) {
                throw new UnauthorizedActionException("You do not have access to this skill gap analysis");
            }

            UUID studentProfileId = gap.getStudentProfileId();
            if (studentProfileId == null) {
                studentProfileId = userProfileGateway.getUserWorkContext(userId)
                        .map(UserProfileGateway.UserProfileWorkContext::profileId)
                        .orElseThrow(() -> new ResourceNotFoundException("User profile not found"));
            }

            String targetRole = gap.getTargetRole() != null && !gap.getTargetRole().isBlank()
                    ? gap.getTargetRole().trim()
                    : "Project";

            RoadmapSession session = roadmapSessionRepository.save(RoadmapSession.builder()
                    .userId(userId)
                    .studentProfileId(studentProfileId)
                    .title("Upskilling for " + targetRole)
                    .targetRole(targetRole)
                    .description("AI-generated roadmap based on your skill gap.")
                    .status(RoadmapSessionStatus.ACTIVE)
                    .lastViewedAt(Instant.now())
                    .build());

            Roadmap roadmap = roadmapRepository.save(Roadmap.builder()
                    .userId(userId)
                    .studentProfileId(studentProfileId)
                    .session(session)
                    .skillGapAnalysis(gap)
                    .title("Upskilling for " + targetRole)
                    .description("AI-generated roadmap based on your skill gap.")
                    .status(RoadmapStatus.ACTIVE)
                    .build());

            String missingSkillsStr = gap.getMissingSkills() == null ? "" : gap.getMissingSkills().stream()
                    .map(SkillGapItem::skillName)
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            return new RoadmapInit(session.getId(), roadmap.getId(), missingSkillsStr);
        });

        try {
            var response = geminiApiService.generateRoadmap(init.missingSkills()).block();

            if (response == null || response.getExtractedText() == null) {
                throw new RuntimeException("No response from Gemini");
            }

            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(response.getExtractedText());

            transactionTemplate.executeWithoutResult(status -> {
                Roadmap roadmap = roadmapRepository.findById(init.roadmapId())
                        .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

                List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads =
                        saveCheckpoints(roadmap, steps, (short) 1);

                eventPublisher.publishEvent(new RoadmapGeneratedEvent(
                        userId,
                        roadmap.getId(),
                        init.sessionId(),
                        roadmap.getTitle() + " | Initial Setup",
                        eventPayloads));
            });
        } catch (Exception e) {
            log.error("Failed to generate checkpoints for roadmap session {}", init.sessionId(), e);
            markSessionFailed(init.sessionId(), init.roadmapId());
        }

        return roadmapSessionRepository.findById(init.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap session not found"));
    }

    /**
     * Backwards-compatible wrapper used by the original roadmap endpoint.
     */
    public Roadmap generateRoadmapFromGap(UUID userId, UUID skillGapId) {
        RoadmapSession session = generateRoadmapSessionFromGap(userId, skillGapId);
        return roadmapRepository.findFirstBySessionIdOrderByCreatedAtAsc(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
    }

    /**
     * Creates a brand-new AI roadmap session. Previous sessions are left
     * untouched, which is the core difference from the old single-roadmap
     * behaviour.
     */
    public RoadmapSession generateCustomRoadmapSession(UUID userId, CreateRoadmapSessionRequest request) {
        String requestedRole = normalize(request.targetRole(), "Recommend a path for me");

        // 1. Fetch profile context (each gateway call is its own read transaction).
        userProfileGateway.getUserProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found"));
        var workContext = userProfileGateway.getUserWorkContext(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found"));

        String profileContext = "Platform Skills: " + String.join(", ", workContext.skills())
                + "\nPlatform Projects: " + String.join(" | ", workContext.projects());

        if (request.currentKnowledge() != null && !request.currentKnowledge().isBlank()) {
            profileContext += "\nUser's self-reported existing knowledge: " + request.currentKnowledge();
        }

        // 2. Fetch chat history (last 50 messages) and summarise it.
        List<String> recentChats = chatGateway.getRecentChatHistory(userId, 50);
        String chatTranscript = String.join("\n", recentChats);

        String chatInsights = "No recent chats.";
        if (!recentChats.isEmpty()) {
            var chatAnalysisRes = geminiApiService.analyzeChatHistoryForRoadmap(chatTranscript).block();
            if (chatAnalysisRes != null && chatAnalysisRes.getExtractedText() != null) {
                try {
                    var parsed = geminiApiService.parseJsonToMap(chatAnalysisRes.getExtractedText());
                    chatInsights = (String) parsed.getOrDefault("insights", chatInsights);
                } catch (Exception e) {
                    log.warn("Failed to parse chat insights JSON", e);
                }
            }
        }

        String knowledgeGaps = request.knowledgeGaps() != null && !request.knowledgeGaps().isBlank()
                ? request.knowledgeGaps()
                : "Unknown";

        // 3. Persist the session before the long-running AI calls so a failure
        //    is still visible to the user as a failed session.
        RoadmapSession session = transactionTemplate.execute(status -> roadmapSessionRepository.save(
                RoadmapSession.builder()
                        .userId(userId)
                        .studentProfileId(workContext.profileId())
                        .title(normalize(request.title(), "Path to " + requestedRole))
                        .targetRole(request.targetRole())
                        .description("AI-generated custom roadmap based on your profile and chats.")
                        .status(RoadmapSessionStatus.ACTIVE)
                        .lastViewedAt(Instant.now())
                        .build()));

        try {
            var currResponse = geminiApiService
                    .generateCurriculum(requestedRole, profileContext, chatInsights, knowledgeGaps, "[]")
                    .block();
            if (currResponse == null || currResponse.getExtractedText() == null) {
                throw new RuntimeException("No response from Gemini");
            }

            var currParsed = geminiApiService.parseJsonToMap(currResponse.getExtractedText());
            String recRole = (String) currParsed.getOrDefault("recommendedRole", requestedRole);
            List<String> curriculumList = (List<String>) currParsed.get("curriculum");

            if (curriculumList == null || curriculumList.isEmpty()) {
                throw new RuntimeException("AI failed to generate curriculum");
            }

            ObjectMapper mapper = new ObjectMapper();
            String curriculumJson = mapper.writeValueAsString(curriculumList);

            // Generate tasks for the FIRST topic. Later topics are generated
            // automatically as the user completes the current checkpoints.
            var tasksResponse = geminiApiService
                    .generateTopicTasks(curriculumList.get(0), recRole, "[]", profileContext)
                    .block();
            if (tasksResponse == null || tasksResponse.getExtractedText() == null) {
                throw new RuntimeException("No task response from Gemini");
            }
            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

            transactionTemplate.executeWithoutResult(status -> {
                RoadmapSession managedSession = roadmapSessionRepository.findById(session.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Roadmap session not found"));

                if (request.title() == null || request.title().isBlank()) {
                    managedSession.setTitle("Path to " + recRole);
                }
                managedSession.setTargetRole(recRole);
                roadmapSessionRepository.save(managedSession);

                Roadmap roadmap = Roadmap.builder()
                        .userId(userId)
                        .studentProfileId(workContext.profileId())
                        .session(managedSession)
                        .title("Path to " + recRole)
                        .description("AI-generated custom roadmap based on your profile and chats.")
                        .status(RoadmapStatus.ACTIVE)
                        .curriculum(curriculumJson)
                        .completedTopics("[]")
                        .currentTopic(curriculumList.get(0))
                        .build();

                Roadmap savedRoadmap = roadmapRepository.save(roadmap);
                List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads =
                        saveCheckpoints(savedRoadmap, steps, (short) 1);

                // Transactional listener in TodoService creates the tasks.
                eventPublisher.publishEvent(new RoadmapGeneratedEvent(
                        userId,
                        savedRoadmap.getId(),
                        managedSession.getId(),
                        savedRoadmap.getTitle() + " | Topic: " + curriculumList.get(0),
                        eventPayloads));
            });
        } catch (Exception e) {
            log.error("Failed to generate custom roadmap session for user {}", userId, e);
            saveFailedCustomRoadmap(session, requestedRole);
        }

        return roadmapSessionRepository.findById(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap session not found"));
    }

    /**
     * Backwards-compatible wrapper used by the original custom-roadmap endpoint.
     */
    public Roadmap generateCustomRoadmap(UUID userId, CreateRoadmapSessionRequest request) {
        RoadmapSession session = generateCustomRoadmapSession(userId, request);
        return roadmapRepository.findFirstBySessionIdOrderByCreatedAtAsc(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
    }

    @Transactional(readOnly = true)
    public Roadmap getActiveRoadmap(UUID userId) {
        return roadmapRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, RoadmapStatus.ACTIVE)
                .orElse(null);
    }

    @Transactional
    public void updateCheckpointStatus(UUID userId, UUID checkpointId, CheckpointStatus status) {
        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (!checkpoint.getRoadmap().getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You do not have access to this checkpoint");
        }

        checkpoint.setStatus(status);
        if (status == CheckpointStatus.COMPLETED) {
            checkpoint.setCompletedAt(Instant.now());
            eventPublisher.publishEvent(new ProfileUpdatedEvent(userId));
        }
        checkpointRepository.save(checkpoint);
    }

    public String chatWithRoadmapAI(UUID userId, UUID roadmapId, String message) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        if (!roadmap.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You do not have access to this roadmap");
        }

        String context = String.format("Target Role: %s\nCurrent Topic: %s\nCompleted Topics: %s",
                roadmap.getTitle(), roadmap.getCurrentTopic(), roadmap.getCompletedTopics());

        try {
            var response = geminiApiService.chatWithRoadmap(context, message).block();
            if (response != null && response.getExtractedText() != null) {
                var parsed = geminiApiService.parseJsonToMap(response.getExtractedText());
                return (String) parsed.getOrDefault("reply", "I'm sorry, I didn't understand that.");
            }
        } catch (Exception e) {
            log.error("Failed to chat with AI", e);
        }
        return "I'm having trouble connecting right now.";
    }

    // Deliberately NOT @ApplicationModuleListener: that annotation is itself
    // @Transactional(REQUIRES_NEW), which would hold a Neon connection for the
    // whole Gemini call. @TransactionalEventListener still runs only after the
    // TodoCompletedEvent transaction commits, but starts no transaction here.
    @Async
    @TransactionalEventListener
    public void onTodoCompleted(TodoCompletedEvent event) {
        if (event.relatedEntityId() == null) return;

        // Phase 1: update the checkpoint/roadmap in a short transaction.
        CompletionState state = transactionTemplate.execute(status -> {
            Optional<Checkpoint> cpOpt = checkpointRepository.findById(event.relatedEntityId());
            if (cpOpt.isEmpty()) return null;

            Checkpoint checkpoint = cpOpt.get();
            checkpoint.setStatus(CheckpointStatus.COMPLETED);
            checkpoint.setCompletedAt(Instant.now());
            checkpointRepository.save(checkpoint);

            Roadmap roadmap = checkpoint.getRoadmap();
            UUID sessionId = roadmap.getSession() != null ? roadmap.getSession().getId() : null;

            eventPublisher.publishEvent(new ProfileUpdatedEvent(roadmap.getUserId()));

            if (roadmap.getStatus() != RoadmapStatus.ACTIVE) {
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), sessionId,
                        roadmap.getTitle(), null, 0, null);
            }

            // Check if ALL checkpoints for this roadmap are completed
            List<Checkpoint> allCheckpoints = checkpointRepository.findByRoadmapId(roadmap.getId());
            boolean allCompleted = allCheckpoints.stream().allMatch(c -> c.getStatus() == CheckpointStatus.COMPLETED);

            if (!allCompleted) {
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), sessionId, roadmap.getTitle(), null,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            }

            // Skill-gap roadmaps have no curriculum; completing every checkpoint
            // completes the roadmap and potentially the whole session.
            if (roadmap.getCurriculum() == null || roadmap.getCurriculum().isBlank()) {
                roadmap.setStatus(RoadmapStatus.COMPLETED);
                roadmapRepository.save(roadmap);
                markSessionCompletedIfDone(sessionId);
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), sessionId, roadmap.getTitle(), null,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                List<String> curriculum = mapper.readValue(roadmap.getCurriculum(),
                        new TypeReference<List<String>>() {});
                List<String> completedTopics = roadmap.getCompletedTopics() == null || roadmap.getCompletedTopics().isBlank()
                        ? new ArrayList<>()
                        : mapper.readValue(roadmap.getCompletedTopics(), new TypeReference<List<String>>() {});

                if (roadmap.getCurrentTopic() != null && !completedTopics.contains(roadmap.getCurrentTopic())) {
                    completedTopics.add(roadmap.getCurrentTopic());
                }

                String nextTopic = null;
                for (String topic : curriculum) {
                    if (!completedTopics.contains(topic)) {
                        nextTopic = topic;
                        break;
                    }
                }

                roadmap.setCompletedTopics(mapper.writeValueAsString(completedTopics));

                if (nextTopic != null) {
                    roadmap.setCurrentTopic(nextTopic);
                    roadmapRepository.save(roadmap);
                    log.info("Generating tasks for NEXT topic: {} for Roadmap {}", nextTopic, roadmap.getId());
                } else {
                    roadmap.setStatus(RoadmapStatus.COMPLETED);
                    roadmapRepository.save(roadmap);
                    markSessionCompletedIfDone(sessionId);
                    log.info("Roadmap {} fully completed!", roadmap.getId());
                }

                return new CompletionState(roadmap.getId(), roadmap.getUserId(), sessionId, roadmap.getTitle(),
                        nextTopic, allCheckpoints.size(), roadmap.getCompletedTopics());
            } catch (Exception e) {
                log.error("Failed to process next topic for Roadmap {}", roadmap.getId(), e);
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), sessionId, roadmap.getTitle(), null,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            }
        });

        if (state == null || state.nextTopic() == null) return;

        // Phase 2: call Gemini without holding a transaction, then persist the
        // newly generated checkpoints in one short transaction.
        try {
            var workContext = userProfileGateway.getUserWorkContext(state.userId()).orElseThrow();
            String profileContext = "Platform Skills: " + String.join(", ", workContext.skills())
                    + "\nPlatform Projects: " + String.join(" | ", workContext.projects());

            var tasksResponse = geminiApiService
                    .generateTopicTasks(state.nextTopic(), state.roadmapTitle(), state.completedTopics(), profileContext)
                    .block();
            if (tasksResponse == null || tasksResponse.getExtractedText() == null) {
                throw new RuntimeException("No task response from Gemini");
            }
            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

            transactionTemplate.executeWithoutResult(status -> {
                Roadmap roadmap = roadmapRepository.findById(state.roadmapId())
                        .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
                List<RoadmapGeneratedEvent.CheckpointPayload> payloads =
                        saveCheckpoints(roadmap, steps, (short) (state.checkpointCount() + 1));

                // Transactional listener in TodoService creates the tasks.
                eventPublisher.publishEvent(new RoadmapGeneratedEvent(
                        state.userId(),
                        state.roadmapId(),
                        state.sessionId(),
                        state.roadmapTitle() + " | Topic: " + state.nextTopic(),
                        payloads));
            });
        } catch (Exception e) {
            log.error("Failed to generate next topic for Roadmap {}", state.roadmapId(), e);
        }
    }

    private List<RoadmapGeneratedEvent.CheckpointPayload> saveCheckpoints(
            Roadmap roadmap, List<Map<String, Object>> steps, short startOrder) {
        short order = startOrder;
        int daysOffset = 1;
        List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = new ArrayList<>();

        for (Map<String, Object> step : steps) {
            List<SubtaskDto> subtasks = buildSubtasks(step);

            Checkpoint cp = checkpointRepository.save(Checkpoint.builder()
                    .roadmap(roadmap)
                    .title((String) step.get("title"))
                    .description((String) step.get("description"))
                    .orderIndex(order)
                    .status(CheckpointStatus.PENDING)
                    .subtasks(subtasks)
                    .build());

            eventPayloads.add(new RoadmapGeneratedEvent.CheckpointPayload(
                    cp.getId(), cp.getTitle(), cp.getDescription(), order,
                    Instant.now().plus(daysOffset, ChronoUnit.DAYS), TaskPriority.HIGH,
                    cp.getSubtasks()
            ));
            order++;
            daysOffset += 2;
        }
        return eventPayloads;
    }

    private List<SubtaskDto> buildSubtasks(Map<String, Object> step) {
        List<SubtaskDto> subtasks = new ArrayList<>();
        Object rawSubtasks = step.get("subtasks");

        if (rawSubtasks instanceof List<?> list) {
            for (Object item : list) {
                String title = null;
                if (item instanceof String value) {
                    title = value;
                } else if (item instanceof Map<?, ?> map && map.get("title") instanceof String value) {
                    title = value;
                }

                if (title != null && !title.isBlank()) {
                    subtasks.add(new SubtaskDto(UUID.randomUUID().toString(), title.trim(), false));
                }
            }
        }
        return subtasks;
    }

    private void markSessionFailed(UUID sessionId, UUID roadmapId) {
        transactionTemplate.executeWithoutResult(status -> {
            roadmapRepository.findById(roadmapId).ifPresent(roadmap -> {
                roadmap.setStatus(RoadmapStatus.FAILED);
                roadmapRepository.save(roadmap);
            });
            roadmapSessionRepository.findById(sessionId).ifPresent(session -> {
                session.setStatus(RoadmapSessionStatus.FAILED);
                roadmapSessionRepository.save(session);
            });
        });
    }

    private void markSessionCompletedIfDone(UUID sessionId) {
        if (sessionId == null) return;

        List<Roadmap> roadmaps = roadmapRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
        if (roadmaps.isEmpty()) return;

        boolean allCompleted = roadmaps.stream().allMatch(r -> r.getStatus() == RoadmapStatus.COMPLETED);
        if (!allCompleted) return;

        roadmapSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == RoadmapSessionStatus.ACTIVE) {
                session.setStatus(RoadmapSessionStatus.COMPLETED);
                roadmapSessionRepository.save(session);
                log.info("Roadmap session {} fully completed", sessionId);
            }
        });
    }

    private void saveFailedCustomRoadmap(RoadmapSession session, String targetRole) {
        transactionTemplate.executeWithoutResult(status -> {
            RoadmapSession managedSession = roadmapSessionRepository.findById(session.getId()).orElse(null);
            if (managedSession == null) return;

            managedSession.setStatus(RoadmapSessionStatus.FAILED);
            roadmapSessionRepository.save(managedSession);

            roadmapRepository.save(Roadmap.builder()
                    .userId(managedSession.getUserId())
                    .studentProfileId(managedSession.getStudentProfileId())
                    .session(managedSession)
                    .title(managedSession.getTitle())
                    .description(managedSession.getDescription())
                    .status(RoadmapStatus.FAILED)
                    .build());
        });
    }

    private static String normalize(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private record RoadmapInit(UUID sessionId, UUID roadmapId, String missingSkills) {
    }

    private record CompletionState(UUID roadmapId, UUID userId, UUID sessionId, String roadmapTitle,
                                   String nextTopic, int checkpointCount, String completedTopics) {
    }
}
