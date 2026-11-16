package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.entity.Checkpoint;
import com.skillsync.skillsync_ai.ai.entity.Roadmap;
import com.skillsync.skillsync_ai.ai.entity.SkillGapAnalysis;
import com.skillsync.skillsync_ai.ai.entity.enums.CheckpointStatus;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import com.skillsync.skillsync_ai.ai.repo.CheckpointRepository;
import com.skillsync.skillsync_ai.ai.repo.RoadmapRepository;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import com.skillsync.skillsync_ai.common.enums.TaskPriority;
import com.skillsync.skillsync_ai.common.events.RoadmapGeneratedEvent;
import com.skillsync.skillsync_ai.todo.dto.SubtaskDto;
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
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final CheckpointRepository checkpointRepository;
    private final SkillGapAnalysisRepository skillGapRepository;
    private final GeminiApiService geminiApiService;
    private final ApplicationEventPublisher eventPublisher; // ADDED: To trigger the Todo module!
    private final TransactionTemplate transactionTemplate;

    private final com.skillsync.skillsync_ai.userprofile.UserProfileGateway userProfileGateway;
    private final com.skillsync.skillsync_ai.chat.ChatGateway chatGateway;

    public Roadmap generateRoadmapFromGap(UUID userId, UUID skillGapId) {
        RoadmapInit init = transactionTemplate.execute(status -> {
            SkillGapAnalysis gap = skillGapRepository.findById(skillGapId).orElseThrow();
            if (!gap.getUserId().equals(userId)) throw new SecurityException("Unauthorized");

            Roadmap roadmap = Roadmap.builder()
                    .userId(userId)
                    // FIXED: Pulling the cross-module ID directly from the gap analysis
                    .studentProfileId(gap.getStudentProfileId())
                    .skillGapAnalysis(gap)
                    .title("Upskilling for " + (gap.getTargetRole() != null ? gap.getTargetRole() : "Project"))
                    .description("AI-generated roadmap based on your skill gap.")
                    .status(RoadmapStatus.ACTIVE)
                    .build();

            Roadmap savedRoadmap = roadmapRepository.save(roadmap);

            String missingSkillsStr = gap.getMissingSkills().stream()
                    .map(com.skillsync.skillsync_ai.ai.entity.SkillGapItem::skillName)
                    .reduce((a, b) -> a + ", " + b).orElse("");

            return new RoadmapInit(savedRoadmap.getId(), missingSkillsStr);
        });

        try {
            var response = geminiApiService.generateRoadmap(init.missingSkills()).block();

            if (response == null || response.getExtractedText() == null) {
                throw new RuntimeException("No response from Gemini");
            }

            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(response.getExtractedText());

            GeneratedRoadmap generated = transactionTemplate.execute(status -> {
                Roadmap savedRoadmap = roadmapRepository.findById(init.roadmapId()).orElseThrow();
                List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = saveCheckpoints(savedRoadmap, steps, (short) 1);

                // Publish inside the transaction: TodoService.onRoadmapGenerated
                // uses a transactional event listener and would otherwise be skipped.
                eventPublisher.publishEvent(new RoadmapGeneratedEvent(
                        userId, savedRoadmap.getId(), savedRoadmap.getTitle() + " | Initial Setup", eventPayloads));

                return new GeneratedRoadmap(savedRoadmap.getId(), savedRoadmap.getTitle(), eventPayloads);
            });

            return roadmapRepository.findById(generated.roadmapId()).orElseThrow();
        } catch (Exception e) {
            // FIXED: Logging the exact Roadmap ID that failed
            log.error("Failed to generate checkpoints for roadmap {}", init.roadmapId(), e);
            transactionTemplate.executeWithoutResult(status -> roadmapRepository.findById(init.roadmapId())
                    .ifPresent(roadmap -> {
                        roadmap.setStatus(RoadmapStatus.FAILED);
                        roadmapRepository.save(roadmap);
                    }));
            return roadmapRepository.findById(init.roadmapId()).orElseThrow();
        }
    }

    /**
     * All Gemini calls happen between short database transactions. The old
     * implementation kept one transaction open for the entire AI generation,
     * which could hold a Neon connection for minutes.
     */
    public Roadmap generateCustomRoadmap(UUID userId, com.skillsync.skillsync_ai.ai.controller.RoadmapController.CustomRoadmapRequest request) {
        String targetRole = request.targetRole() != null && !request.targetRole().isBlank() ? request.targetRole() : "Recommend a path for me";

        // 1. Fetch Profile Context (each gateway call is its own read transaction)
        userProfileGateway.getUserProfile(userId).orElseThrow(() -> new RuntimeException("User profile not found"));
        var workContext = userProfileGateway.getUserWorkContext(userId).orElseThrow();
        String profileContext = "Platform Skills: " + String.join(", ", workContext.skills()) + "\nPlatform Projects: " + String.join(" | ", workContext.projects());

        if (request.currentKnowledge() != null && !request.currentKnowledge().isBlank()) {
            profileContext += "\nUser's self-reported existing knowledge: " + request.currentKnowledge();
        }

        // 2. Fetch Chat History (last 50 messages)
        List<String> recentChats = chatGateway.getRecentChatHistory(userId, 50);
        String chatTranscript = String.join("\n", recentChats);

        // 3. Summarize Chat
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

        String knowledgeGaps = request.knowledgeGaps() != null && !request.knowledgeGaps().isBlank() ? request.knowledgeGaps() : "Unknown";

        try {
            var currResponse = geminiApiService.generateCurriculum(targetRole, profileContext, chatInsights, knowledgeGaps, "[]").block();
            if (currResponse == null || currResponse.getExtractedText() == null) throw new RuntimeException("No response from Gemini");

            var currParsed = geminiApiService.parseJsonToMap(currResponse.getExtractedText());
            String recRole = (String) currParsed.getOrDefault("recommendedRole", targetRole);
            List<String> curriculumList = (List<String>) currParsed.get("curriculum");

            if (curriculumList == null || curriculumList.isEmpty()) {
                throw new RuntimeException("AI failed to generate curriculum");
            }

            // Convert list to JSON string
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String curriculumJson = mapper.writeValueAsString(curriculumList);

            // Generate tasks for the FIRST topic
            var tasksResponse = geminiApiService.generateTopicTasks(curriculumList.get(0), recRole, "[]", profileContext).block();
            if (tasksResponse == null || tasksResponse.getExtractedText() == null) {
                throw new RuntimeException("No task response from Gemini");
            }
            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

            // Deactivate old roadmaps and persist the new roadmap in one short write.
            transactionTemplate.executeWithoutResult(status -> {
                for (Roadmap r : roadmapRepository.findAllByUserIdAndStatus(userId, RoadmapStatus.ACTIVE)) {
                    r.setStatus(RoadmapStatus.COMPLETED); // Or INACTIVE
                    roadmapRepository.save(r);
                    List<UUID> cpIds = checkpointRepository.findByRoadmapId(r.getId()).stream().map(Checkpoint::getId).toList();

                    // Transactional listener in TodoService archives the old tasks.
                    eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.RoadmapDeactivatedEvent(
                            userId, r.getId(), cpIds));
                }
            });

            GeneratedRoadmap generated = transactionTemplate.execute(status -> {
                Roadmap roadmap = Roadmap.builder()
                        .userId(userId)
                        .studentProfileId(workContext.profileId()) // Using profileId from workContext
                        .title("Path to " + recRole)
                        .description("AI-generated custom roadmap based on your profile and chats.")
                        .status(RoadmapStatus.ACTIVE)
                        .curriculum(curriculumJson)
                        .completedTopics("[]")
                        .currentTopic(curriculumList.get(0))
                        .build();
                Roadmap savedRoadmap = roadmapRepository.save(roadmap);
                List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = saveCheckpoints(savedRoadmap, steps, (short) 1);

                // Transactional listener in TodoService creates the tasks.
                eventPublisher.publishEvent(new RoadmapGeneratedEvent(userId, savedRoadmap.getId(),
                        savedRoadmap.getTitle() + " | Topic: " + curriculumList.get(0), eventPayloads));

                return new GeneratedRoadmap(savedRoadmap.getId(), savedRoadmap.getTitle(), eventPayloads);
            });

            return roadmapRepository.findById(generated.roadmapId()).orElseThrow();
        } catch (Exception e) {
            log.error("Failed to generate custom roadmap for user {}", userId, e);
            return saveFailedCustomRoadmap(userId, workContext.profileId(), targetRole);
        }
    }

    @Transactional(readOnly = true)
    public Roadmap getActiveRoadmap(UUID userId) {
        return roadmapRepository.findAllByUserIdAndStatus(userId, RoadmapStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void updateCheckpointStatus(UUID userId, UUID checkpointId, CheckpointStatus status) {
        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        // FIXED: Replaced getStudentProfileId() with getUserId()
        if (!checkpoint.getRoadmap().getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized");
        }

        checkpoint.setStatus(status);
        if (status == CheckpointStatus.COMPLETED) {
            checkpoint.setCompletedAt(Instant.now());
            eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProfileUpdatedEvent(userId));
        }
        checkpointRepository.save(checkpoint);
    }

    public String chatWithRoadmapAI(UUID userId, UUID roadmapId, String message) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new IllegalArgumentException("Roadmap not found"));

        if (!roadmap.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized");
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
    public void onTodoCompleted(com.skillsync.skillsync_ai.common.events.TodoCompletedEvent event) {
        if (event.relatedEntityId() == null) return;

        // Phase 1: update the checkpoint/roadmap in a short transaction.
        CompletionState state = transactionTemplate.execute(status -> {
            Optional<Checkpoint> cpOpt = checkpointRepository.findById(event.relatedEntityId());
            if (cpOpt.isEmpty()) return null;

            Checkpoint checkpoint = cpOpt.get();
            checkpoint.setStatus(CheckpointStatus.COMPLETED);
            checkpoint.setCompletedAt(Instant.now());
            checkpointRepository.save(checkpoint);

            eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProfileUpdatedEvent(checkpoint.getRoadmap().getUserId()));

            Roadmap roadmap = checkpoint.getRoadmap();
            if (roadmap.getStatus() != RoadmapStatus.ACTIVE) {
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), roadmap.getTitle(), null, 0, null);
            }

            // Check if ALL checkpoints for this roadmap are completed
            List<Checkpoint> allCheckpoints = checkpointRepository.findByRoadmapId(roadmap.getId());
            boolean allCompleted = allCheckpoints.stream().allMatch(c -> c.getStatus() == CheckpointStatus.COMPLETED);

            if (!allCompleted || roadmap.getCurriculum() == null) {
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), roadmap.getTitle(), null,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            }

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<String> curriculum = mapper.readValue(roadmap.getCurriculum(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                List<String> completedTopics = roadmap.getCompletedTopics() == null || roadmap.getCompletedTopics().isBlank()
                        ? new ArrayList<>()
                        : mapper.readValue(roadmap.getCompletedTopics(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});

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
                    log.info("Roadmap {} fully completed!", roadmap.getId());
                }

                return new CompletionState(roadmap.getId(), roadmap.getUserId(), roadmap.getTitle(), nextTopic,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            } catch (Exception e) {
                log.error("Failed to process next topic for Roadmap {}", roadmap.getId(), e);
                return new CompletionState(roadmap.getId(), roadmap.getUserId(), roadmap.getTitle(), null,
                        allCheckpoints.size(), roadmap.getCompletedTopics());
            }
        });

        if (state == null || state.nextTopic() == null) return;

        // Phase 2: call Gemini without holding a transaction, then persist the
        // newly generated checkpoints in one short transaction.
        try {
            var workContext = userProfileGateway.getUserWorkContext(state.userId()).orElseThrow();
            String profileContext = "Platform Skills: " + String.join(", ", workContext.skills()) + "\nPlatform Projects: " + String.join(" | ", workContext.projects());

            var tasksResponse = geminiApiService.generateTopicTasks(state.nextTopic(), state.roadmapTitle(), state.completedTopics(), profileContext).block();
            if (tasksResponse == null || tasksResponse.getExtractedText() == null) {
                throw new RuntimeException("No task response from Gemini");
            }
            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

            transactionTemplate.executeWithoutResult(status -> {
                Roadmap roadmap = roadmapRepository.findById(state.roadmapId()).orElseThrow();
                List<RoadmapGeneratedEvent.CheckpointPayload> payloads = saveCheckpoints(roadmap, steps, (short) (state.checkpointCount() + 1));

                // Transactional listener in TodoService creates the tasks.
                eventPublisher.publishEvent(new RoadmapGeneratedEvent(state.userId(), state.roadmapId(),
                        state.roadmapTitle() + " | Topic: " + state.nextTopic(), payloads));
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
        if (step.containsKey("subtasks") && step.get("subtasks") instanceof List) {
            List<String> stList = (List<String>) step.get("subtasks");
            for (String stTitle : stList) {
                subtasks.add(new SubtaskDto(UUID.randomUUID().toString(), stTitle, false));
            }
        }
        return subtasks;
    }

    private Roadmap saveFailedCustomRoadmap(UUID userId, UUID studentProfileId, String targetRole) {
        return transactionTemplate.execute(status -> roadmapRepository.save(Roadmap.builder()
                .userId(userId)
                .studentProfileId(studentProfileId)
                .title("Path to " + targetRole)
                .description("AI-generated custom roadmap based on your profile and chats.")
                .status(RoadmapStatus.FAILED)
                .build()));
    }

    private record RoadmapInit(UUID roadmapId, String missingSkills) {
    }

    private record GeneratedRoadmap(UUID roadmapId, String roadmapTitle, List<RoadmapGeneratedEvent.CheckpointPayload> checkpoints) {
    }

    private record CompletionState(UUID roadmapId, UUID userId, String roadmapTitle, String nextTopic,
                                   int checkpointCount, String completedTopics) {
    }
}