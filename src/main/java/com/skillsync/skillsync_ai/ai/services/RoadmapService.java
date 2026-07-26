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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private final com.skillsync.skillsync_ai.userprofile.UserProfileGateway userProfileGateway;
    private final com.skillsync.skillsync_ai.chat.ChatGateway chatGateway;

    @Transactional
    public Roadmap generateRoadmapFromGap(UUID userId, UUID skillGapId) {
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

        try {
            String missingSkillsStr = gap.getMissingSkills().stream()
                    .map(com.skillsync.skillsync_ai.ai.entity.SkillGapItem::skillName)
                    .reduce((a, b) -> a + ", " + b).orElse("");

            var response = geminiApiService.generateRoadmap(missingSkillsStr).block();

            if (response == null || response.getExtractedText() == null) {
                throw new RuntimeException("No response from Gemini");
            }

            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(response.getExtractedText());

            short order = 1;
            int daysOffset = 1;
            List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = new ArrayList<>();

            for (Map<String, Object> step : steps) {
                // Ensure subtasks are handled even for initial roadmap generation
                List<SubtaskDto> subtasks = new ArrayList<>();
                if (step.containsKey("subtasks") && step.get("subtasks") instanceof List) {
                    List<String> stList = (List<String>) step.get("subtasks");
                    for (String stTitle : stList) {
                        subtasks.add(new SubtaskDto(UUID.randomUUID().toString(), stTitle, false));
                    }
                }

                Checkpoint cp = checkpointRepository.save(Checkpoint.builder()
                        .roadmap(savedRoadmap)
                        .title((String) step.get("title"))
                        .description((String) step.get("description"))
                        .orderIndex(order)
                        .status(CheckpointStatus.PENDING)
                        .subtasks(subtasks)
                        .build());

                eventPayloads.add(new RoadmapGeneratedEvent.CheckpointPayload(
                        cp.getId(),
                        cp.getTitle(),
                        cp.getDescription(),
                        order,
                        Instant.now().plus(daysOffset, ChronoUnit.DAYS),
                        TaskPriority.HIGH,
                        cp.getSubtasks()
                ));
                order++;
                daysOffset += 2;
            }

            // FIXED: Linking the generated tasks to the Roadmap ID, not the User ID
            eventPublisher.publishEvent(new RoadmapGeneratedEvent(
                    userId, savedRoadmap.getId(), savedRoadmap.getTitle() + " | Initial Setup", eventPayloads));

        } catch (Exception e) {
            // FIXED: Logging the exact Roadmap ID that failed
            log.error("Failed to generate checkpoints for roadmap {}", savedRoadmap.getId(), e);
            savedRoadmap.setStatus(RoadmapStatus.FAILED);
            roadmapRepository.save(savedRoadmap);
        }

        return savedRoadmap;
    }

    @Transactional
    public Roadmap generateCustomRoadmap(UUID userId, com.skillsync.skillsync_ai.ai.controller.RoadmapController.CustomRoadmapRequest request) {
        String targetRole = request.targetRole() != null && !request.targetRole().isBlank() ? request.targetRole() : "Recommend a path for me";
        // 1. Deactivate existing active roadmaps for this user
        List<Roadmap> existingRoadmaps = roadmapRepository.findAll(); // Simplified for now, should find by userId and status=ACTIVE
        for (Roadmap r : existingRoadmaps) {
            if (r.getUserId().equals(userId) && r.getStatus() == RoadmapStatus.ACTIVE) {
                r.setStatus(RoadmapStatus.COMPLETED); // Or INACTIVE
                roadmapRepository.save(r);
                List<UUID> cpIds = checkpointRepository.findByRoadmapId(r.getId()).stream().map(Checkpoint::getId).toList();
                eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.RoadmapDeactivatedEvent(userId, r.getId(), cpIds));
            }
        }

        // 2. Fetch Profile Context
        var profile = userProfileGateway.getUserProfile(userId).orElseThrow(() -> new RuntimeException("User profile not found"));
        var workContext = userProfileGateway.getUserWorkContext(userId).orElseThrow();
        String profileContext = "Platform Skills: " + String.join(", ", workContext.skills()) + "\nPlatform Projects: " + String.join(" | ", workContext.projects());
        
        if (request.currentKnowledge() != null && !request.currentKnowledge().isBlank()) {
            profileContext += "\nUser's self-reported existing knowledge: " + request.currentKnowledge();
        }

        // 3. Fetch Chat History (last 50 messages)
        List<String> recentChats = chatGateway.getRecentChatHistory(userId, 50);
        String chatTranscript = String.join("\n", recentChats);
        
        // 4. Summarize Chat
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

        // 5. Generate Roadmap
        Roadmap roadmap = Roadmap.builder()
                .userId(userId)
                .studentProfileId(workContext.profileId()) // Using profileId from workContext
                .title("Path to " + targetRole)
                .description("AI-generated custom roadmap based on your profile and chats.")
                .status(RoadmapStatus.ACTIVE)
                .build();
        Roadmap savedRoadmap = roadmapRepository.save(roadmap);

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

            roadmap.setTitle("Path to " + recRole);
            roadmap.setCurriculum(curriculumJson);
            roadmap.setCompletedTopics("[]");
            roadmap.setCurrentTopic(curriculumList.get(0));
            savedRoadmap = roadmapRepository.save(roadmap);

            // Generate tasks for the FIRST topic
            var tasksResponse = geminiApiService.generateTopicTasks(roadmap.getCurrentTopic(), recRole, "[]", profileContext).block();
            List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

            short order = 1;
            int daysOffset = 1;
            List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = new ArrayList<>();

            for (Map<String, Object> step : steps) {
                List<SubtaskDto> subtasks = new ArrayList<>();
                if (step.containsKey("subtasks") && step.get("subtasks") instanceof List) {
                    List<String> stList = (List<String>) step.get("subtasks");
                    for (String stTitle : stList) {
                        subtasks.add(new SubtaskDto(UUID.randomUUID().toString(), stTitle, false));
                    }
                }

                Checkpoint cp = checkpointRepository.save(Checkpoint.builder()
                        .roadmap(savedRoadmap)
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

            eventPublisher.publishEvent(new RoadmapGeneratedEvent(userId, savedRoadmap.getId(), savedRoadmap.getTitle() + " | Topic: " + roadmap.getCurrentTopic(), eventPayloads));
            return savedRoadmap;
        } catch (Exception e) {
            log.error("Failed to generate custom checkpoints for roadmap {}", roadmap.getId(), e);
            roadmap.setStatus(RoadmapStatus.FAILED);
            roadmapRepository.save(roadmap);
            return roadmap;
        }
    }

    @Transactional(readOnly = true)
    public Roadmap getActiveRoadmap(UUID userId) {
        List<Roadmap> roadmaps = roadmapRepository.findAll(); // Simplified for now
        return roadmaps.stream()
                .filter(r -> r.getUserId().equals(userId) && r.getStatus() == RoadmapStatus.ACTIVE)
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

    @org.springframework.modulith.events.ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTodoCompleted(com.skillsync.skillsync_ai.common.events.TodoCompletedEvent event) {
        if (event.relatedEntityId() == null) return;
        
        Optional<Checkpoint> cpOpt = checkpointRepository.findById(event.relatedEntityId());
        if (cpOpt.isEmpty()) return;
        
        Checkpoint checkpoint = cpOpt.get();
        checkpoint.setStatus(CheckpointStatus.COMPLETED);
        checkpoint.setCompletedAt(Instant.now());
        checkpointRepository.save(checkpoint);
        
        eventPublisher.publishEvent(new com.skillsync.skillsync_ai.common.events.ProfileUpdatedEvent(checkpoint.getRoadmap().getUserId()));

        Roadmap roadmap = checkpoint.getRoadmap();
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE) return;

        // Check if ALL checkpoints for this roadmap are completed
        List<Checkpoint> allCheckpoints = checkpointRepository.findByRoadmapId(roadmap.getId());
        boolean allCompleted = allCheckpoints.stream().allMatch(c -> c.getStatus() == CheckpointStatus.COMPLETED);

        if (allCompleted && roadmap.getCurriculum() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<String> curriculum = mapper.readValue(roadmap.getCurriculum(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                List<String> completedTopics = mapper.readValue(roadmap.getCompletedTopics(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                
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
                    
                    var workContext = userProfileGateway.getUserWorkContext(roadmap.getUserId()).orElseThrow();
                    String profileContext = "Platform Skills: " + String.join(", ", workContext.skills()) + "\nPlatform Projects: " + String.join(" | ", workContext.projects());

                    var tasksResponse = geminiApiService.generateTopicTasks(nextTopic, roadmap.getTitle(), roadmap.getCompletedTopics(), profileContext).block();
                    List<Map<String, Object>> steps = geminiApiService.parseRoadmapSteps(tasksResponse.getExtractedText());

                    short order = (short) (allCheckpoints.size() + 1);
                    int daysOffset = 1;
                    List<RoadmapGeneratedEvent.CheckpointPayload> eventPayloads = new ArrayList<>();

                    for (Map<String, Object> step : steps) {
                        List<SubtaskDto> subtasks = new ArrayList<>();
                        if (step.containsKey("subtasks") && step.get("subtasks") instanceof List) {
                            List<String> stList = (List<String>) step.get("subtasks");
                            for (String stTitle : stList) {
                                subtasks.add(new SubtaskDto(UUID.randomUUID().toString(), stTitle, false));
                            }
                        }

                        Checkpoint newCp = checkpointRepository.save(Checkpoint.builder()
                                .roadmap(roadmap)
                                .title((String) step.get("title"))
                                .description((String) step.get("description"))
                                .orderIndex(order)
                                .status(CheckpointStatus.PENDING)
                                .subtasks(subtasks)
                                .build());

                        eventPayloads.add(new RoadmapGeneratedEvent.CheckpointPayload(
                                newCp.getId(), newCp.getTitle(), newCp.getDescription(), order,
                                Instant.now().plus(daysOffset, ChronoUnit.DAYS), TaskPriority.HIGH,
                                newCp.getSubtasks()
                        ));
                        order++;
                        daysOffset += 2;
                    }
                    eventPublisher.publishEvent(new RoadmapGeneratedEvent(roadmap.getUserId(), roadmap.getId(), roadmap.getTitle() + " | Topic: " + nextTopic, eventPayloads));
                } else {
                    roadmap.setStatus(RoadmapStatus.COMPLETED);
                    roadmapRepository.save(roadmap);
                    log.info("Roadmap {} fully completed!", roadmap.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process next topic for Roadmap {}", roadmap.getId(), e);
            }
        }
    }
}