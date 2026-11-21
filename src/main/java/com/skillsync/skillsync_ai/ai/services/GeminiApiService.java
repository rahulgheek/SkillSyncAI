package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.gemini.GeminiResponse;
import com.skillsync.skillsync_ai.common.events.AiSkill;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiApiService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public Mono<GeminiResponse> analyzeResume(String rawResumeText) {
        String prompt = """
            You are an expert technical recruiter AI. Analyze the following resume text. 
            Extract technical and soft skills, and provide a short summary of the candidate.
            Return the output EXACTLY in this JSON structure:
            {
              "summary": "Short 2 sentence summary",
              "skills": [
                 { "skillName": "Java", "confidence": "HIGH", "inferredLevel": "ADVANCED" }
              ]
            }
            
            Resume Text:
            """ + rawResumeText;

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "resume analysis");
    }

    public Map<String, Object> parseJsonToMap(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini JSON output: {}", e.getMessage());
            throw new RuntimeException("AI response was not valid JSON format", e);
        }
    }

    public Mono<GeminiResponse> analyzeSkillGap(List<AiSkill> studentSkills, String projectRequirements) {

        String formattedSkills = studentSkills.stream()
                .map(s -> s.name() + " (" + s.level() + ")")
                .collect(Collectors.joining(", "));

        String prompt = String.format("""
            You are an expert technical AI recruiter. Compare the candidate's skills with the project requirements.
            Return the output EXACTLY in this JSON structure:
            {
              "matchPercentage": 85.5,
              "rationale": "Short 2 sentence explanation of why they are a good/bad fit.",
              "matchedSkills": [
                 { "skillName": "Java", "importance": "CRITICAL" }
              ],
              "missingSkills": [
                 { "skillName": "AWS", "importance": "NICE_TO_HAVE" }
              ]
            }
            
            Candidate Skills: %s
            Project Requirements: %s
            """, formattedSkills, projectRequirements);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "skill gap");
    }

    public Mono<GeminiResponse> generateRoadmap(String missingSkills) {
        String prompt = String.format("""
            You are an expert technical mentor. A student is missing the following skills required for a project: %s.
            Generate a step-by-step learning roadmap for them to acquire these skills.

            CRITICAL REQUIREMENTS:
            Generate 3 to 5 tasks.
            For EVERY task you MUST provide a "subtasks" array containing EXACTLY 3 to 5 actionable subtasks.
            For EVERY task you MUST also provide a "learningResources" array containing 2 to 4 learning resources.
            - Include at least one popular YouTube video from a reputable, widely watched channel
              (for example freeCodeCamp, Programming with Mosh, Traversy Media, Fireship, Net Ninja, CS50).
            - Prefer videos that are genuinely popular and highly rated for this exact topic.
            - Only use a direct YouTube URL when you are confident the video exists.
              If you are not certain, use a YouTube search URL such as
              "https://www.youtube.com/results?search_query=spring+boot+testing".
            - You may add official documentation as an extra resource with "type": "DOCUMENTATION".

            Return the output EXACTLY in this JSON array structure (do NOT wrap in any other object):
            [
              {
                "title": "Understand the Basics",
                "description": "Short explanation of what to learn first.",
                "subtasks": [
                  "Read the official documentation overview",
                  "Complete a beginner tutorial",
                  "Write a small example from scratch"
                ],
                "learningResources": [
                  {
                    "title": "Spring Boot Tutorial for Beginners",
                    "channel": "freeCodeCamp.org",
                    "url": "https://www.youtube.com/watch?v=example",
                    "type": "YOUTUBE_VIDEO",
                    "description": "A popular full-length introduction to the topic.",
                    "duration": "1h 20m"
                  }
                ]
              }
            ]
            """, missingSkills);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "roadmap");
    }

    // 1. ADD THIS: The method to call Gemini for a batch of students (Recommendations)
    public Mono<GeminiResponse> analyzeBatchSkillGap(String prompt) {
        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "batch skill gap analysis");
    }

    public Mono<GeminiResponse> analyzeChatHistoryForRoadmap(String chatTranscript) {
        String prompt = String.format("""
            You are an AI career advisor. Analyze the user's past chat history to identify technical topics they struggled with, asked questions about, or showed interest in.
            Summarize these insights concisely in a few sentences.
            Return ONLY a strict JSON object in this format:
            { "insights": "Your summary here." }
            
            Chat History:
            %s
            """, chatTranscript);
            
        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "chat history analysis");
    }

    public Mono<GeminiResponse> generateCurriculum(String targetRole, String profileContext, String chatInsights, String knowledgeGaps, String completedTopics) {
        String prompt = String.format("""
            You are an expert technical mentor and career advisor.
            Target Goal / Role: %s
            
            Here is their existing experience (Projects and Skills, and Self-Reported Knowledge):
            %s
            
            Here are insights from their past chats (topics they struggled with or want to learn):
            %s
            
            Here are their self-reported knowledge gaps:
            %s
            
            Here are the topics they have ALREADY completed in this roadmap (DO NOT include these again):
            %s
            
            If the Target Goal is vague (like "Recommend a path for me"), analyze their experience and gaps to invent a highly relevant, promising target role for them, and then generate the curriculum for it.
            
            Generate a high-level curriculum (a list of major topics or milestones) for them to achieve the target role, avoiding anything they have already completed.
            
            Return the output EXACTLY in this JSON structure (do NOT wrap in any other object):
            {
              "recommendedRole": "The precise title of the role they should aim for",
              "curriculum": ["Topic 1", "Topic 2", "Topic 3"]
            }
            """, targetRole, profileContext, chatInsights, knowledgeGaps, completedTopics);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "curriculum generation");
    }

    public Mono<GeminiResponse> generateTopicTasks(String currentTopic, String targetRole, String completedTopics, String profileContext) {
        String prompt = String.format("""
            You are an elite, highly detailed technical mentor. The student is aiming for the role of: %s.
            They have already mastered these topics in this roadmap: %s.
            
            Here is their existing background (Skills, Projects, etc.):
            %s
            
            Their CURRENT focus topic is: %s.
            
            Your job is to break this single topic down into simple, easy-to-understand tasks, tailored specifically to their existing background. Avoid teaching them things they already know from their background.
            
            For each task:
            1. Title: Must be a clear, simple task to accomplish.
            2. Description: A brief, easy-to-understand explanation of what to do.
            
            CRITICAL REQUIREMENTS:
            You MUST break the topic into 3 to 5 tasks.
            For EVERY task, you MUST provide a "subtasks" array containing EXACTLY 3 to 5 actionable subtasks.
            For EVERY task, you MUST also provide a "learningResources" array containing 2 to 4 learning resources.
            - Include at least one popular YouTube video from a reputable, widely watched channel
              (for example freeCodeCamp, Programming with Mosh, Traversy Media, Fireship, Net Ninja, CS50).
            - Prefer videos that are genuinely popular and highly rated for this exact task.
            - Only use a direct YouTube URL when you are confident the video exists.
              If you are not certain, use a YouTube search URL such as
              "https://www.youtube.com/results?search_query=react+useeffect+cleanup".
            - You may add official documentation as an extra resource with "type": "DOCUMENTATION".
            
            Return the output EXACTLY in this JSON array structure (do NOT wrap in any other object, just the raw array):
            [
              { 
                "title": "Master React useEffect Dependency Array", 
                "description": "Concepts to cover: Mount/Unmount lifecycle, infinite loop pitfalls, cleanup functions. Exercise: Build a component that subscribes to a mock WebSocket on mount and unsubscribes on unmount.",
                "subtasks": [
                  "Understand the component lifecycle (mount, update, unmount)",
                  "Learn what the dependency array is and how it controls execution",
                  "Identify and fix a common infinite loop caused by missing dependencies",
                  "Build a timer component that cleans up its interval on unmount"
                ],
                "learningResources": [
                  {
                    "title": "Learn React useEffect in 15 Minutes",
                    "channel": "Programming with Mosh",
                    "url": "https://www.youtube.com/watch?v=example",
                    "type": "YOUTUBE_VIDEO",
                    "description": "A popular, practical explanation of effects and cleanup.",
                    "duration": "15 min"
                  }
                ]
              }
            ]
            """, targetRole, completedTopics, profileContext, currentTopic);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "topic tasks generation");
    }

    /**
     * Generates materials for a checkpoint that was created before learning
     * resources were part of the roadmap prompt.
     */
    public Mono<GeminiResponse> generateLearningResources(
            String topic, String taskTitle, String taskDescription, String targetRole) {

        String prompt = String.format("""
            You are an expert technical mentor curating learning materials for one task.
            Target role: %s
            Current topic: %s
            Task: %s
            Task details: %s

            Recommend 2 to 4 high-quality learning resources for this exact task.
            - Include at least one popular YouTube video from a reputable, widely watched channel
              (for example freeCodeCamp, Programming with Mosh, Traversy Media, Fireship, Net Ninja, CS50).
            - Prefer videos that are genuinely popular and highly rated for this exact topic.
            - Only use a direct YouTube URL when you are confident the video exists.
              If you are not certain, use a YouTube search URL such as
              "https://www.youtube.com/results?search_query=java+streams+tutorial".
            - You may add official documentation with "type": "DOCUMENTATION".

            Return ONLY a JSON array (do NOT wrap it in an object):
            [
              {
                "title": "Java Streams Tutorial",
                "channel": "Programming with Mosh",
                "url": "https://www.youtube.com/watch?v=example",
                "type": "YOUTUBE_VIDEO",
                "description": "Why this material helps with the task.",
                "duration": "20 min"
              }
            ]
            """, targetRole, topic, taskTitle, taskDescription);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "learning materials generation");
    }

    public Mono<GeminiResponse> chatWithRoadmap(String context, String message) {
        String prompt = String.format("""
            You are a helpful AI mentor guiding a student through their roadmap.
            
            IMPORTANT RULES:
            1. You are a CONVERSATIONAL assistant. You CANNOT manually generate, modify, or add tasks to the user's To-Do list.
            2. If the user asks you to "generate a new todo list", "give me more tasks", or similar, inform them that the system AUTOMATICALLY generates the next set of tasks in the background once they check off all their current tasks in the widget. Tell them to wait a few seconds and the tasks will appear automatically.
            3. Do NOT output a list of tasks in your response text claiming it's their new to-do list. Only answer conceptual questions, provide explanations, or give advice.
            
            Current Context:
            %s
            
            Student says: "%s"
            
            Provide a helpful, encouraging, and technically accurate response. Keep it concise.
            Return the output EXACTLY in this JSON structure (do NOT wrap in any other object):
            { "reply": "Your response here" }
            """, context, message);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "roadmap chat");
    }

    // 1b. ADD THIS: The method to call Gemini for a batch of students (Project Applicant/Global Gap Analysis)
    public Mono<GeminiResponse> analyzeSkillGapForBatch(String prompt) {
        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "project batch skill gap analysis");
    }

    // 2. ADD THIS: The parser to handle a JSON Array (List) instead of a JSON Object (Map)
    public List<Map<String, Object>> parseJsonToList(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse Gemini JSON output to List: {}", e.getMessage());
            throw new RuntimeException("AI response was not valid JSON array format", e);
        }
    }

    public List<Map<String, Object>> parseRoadmapSteps(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Failed to parse Gemini Roadmap JSON: {}", e.getMessage());
            throw new RuntimeException("AI roadmap response was invalid", e);
        }
    }

    /**
     * Parses the learning-materials response. Some models wrap the array in an
     * object despite the prompt, so both shapes are accepted.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseLearningResources(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return List.of();
        }

        String trimmed = jsonString.trim();
        try {
            if (trimmed.startsWith("{")) {
                Map<String, Object> wrapper = parseJsonToMap(trimmed);
                Object resources = wrapper.get("learningResources");
                if (resources == null) resources = wrapper.get("learning_resources");
                if (resources == null) resources = wrapper.get("resources");
                if (resources instanceof List<?> list) {
                    return list.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> (Map<String, Object>) item)
                            .toList();
                }
                return List.of();
            }

            return objectMapper.readValue(trimmed, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Failed to parse Gemini learning materials JSON: {}", e.getMessage());
            return List.of();
        }
    }

    public Mono<GeminiResponse> summarizeNote(String content) {
        String prompt = String.format("""
            You are a highly intelligent study assistant. Read the following student note and provide a concise, 
            2-3 sentence summary of the key takeaways. Do not use conversational filler.
            Return ONLY a strict JSON object in this format:
            { "summary": "Your 2-3 sentence summary here." }
            
            Note Content:
            %s
            """, content);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "note summary");
    }

    // ==========================================
    // CHAT MODULE INTEGRATION
    // ==========================================

    public Mono<GeminiResponse> generateProfileInsights(String profileContext) {
        String prompt = String.format("""
            You are an expert AI career advisor and tech mentor. Analyze the user's profile context (skills, projects, roadmap progress).
            Generate insightful career analysis.
            Return ONLY a strict JSON object matching this schema EXACTLY:
            {
              "careerSummary": "A strong 2-3 sentence summary of their trajectory",
              "strengths": ["Strength 1", "Strength 2", "Strength 3"],
              "weaknesses": ["Weakness 1", "Weakness 2"],
              "skillGaps": ["Gap 1", "Gap 2"],
              "suggestedTechnologies": ["Tech 1", "Tech 2", "Tech 3"],
              "suggestedProjects": ["Project Idea 1", "Project Idea 2"],
              "recommendedRoles": ["Role 1", "Role 2"],
              "confidenceScore": 88,
              "learningRoadmap": [
                { "title": "Step 1", "description": "What to learn", "dueDate": "Timeframe" },
                { "title": "Step 2", "description": "What to learn next", "dueDate": "Timeframe" }
              ]
            }

            Profile Context:
            %s
            """, profileContext);

        GeminiRequest request = buildJsonRequest(prompt);
        return executeCall(request, "profile insights generation");
    }

    public Mono<GeminiResponse> executeRawChat(GeminiRequest request) {
        return executeCall(request, "raw chat");
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private GeminiRequest buildJsonRequest(String prompt) {
        return GeminiRequest.builder()
                .contents(List.of(GeminiRequest.Content.builder()
                        .role("user")
                        .parts(List.of(GeminiRequest.Part.builder().text(prompt).build()))
                        .build()))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .responseMimeType("application/json") // Forces strict JSON output
                        .build())
                .build();
    }

    private Mono<GeminiResponse> executeCall(GeminiRequest request, String context) {
        String finalUrl = geminiApiUrl + "?key=" + geminiApiKey;
        org.springframework.web.util.DefaultUriBuilderFactory factory = new org.springframework.web.util.DefaultUriBuilderFactory();
        factory.setEncodingMode(org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode.NONE);
        
        log.info("Before WebClient execution for context: {}", context);
        return webClientBuilder.uriBuilderFactory(factory)
                .build()
                .post()
                .uri(finalUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .timeout(Duration.ofSeconds(60)) // Increased timeout to 60s for detailed generation
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)) // Retry twice on 5xx errors
                        .filter(throwable -> throwable instanceof WebClientResponseException &&
                                ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
                .doOnSuccess(res -> log.info("After WebClient execution - successfully received Gemini response for {}", context))
                .doOnError(e -> log.error("Failed to call Gemini API for {}: {}", context, e.getMessage()));
    }

    @Component
    public static class RecommendationPromptFactory {

        public static final String CURRENT_VERSION = "v1.0-batch-skills";

        public String buildBatchMatchPrompt(String requiredSkills, List<CandidateProfileForAi> candidates) {
            String candidateData = candidates.stream()
                    .map(c -> "Candidate ID: " + c.userId() + " | Skills: " + formatSkills(c.skills()))
                    .collect(Collectors.joining("\n"));

            return String.format("""
                You are an expert technical recruiter AI. 
                Evaluate the semantic match between the project's required skills and the candidates' skills.
                
                Project Required Skills: %s
                
                Candidates:
                %s
                
                Return the evaluation EXACTLY as a JSON array using this schema:
                [
                  {
                    "userId": "UUID string",
                    "semanticScore": 85.5,
                    "confidence": "HIGH",
                    "matchedSkills": ["Java", "Spring Boot"],
                    "rationale": "2-sentence explanation of the skill overlap."
                  }
                ]
                """, requiredSkills, candidateData);
        }

        private String formatSkills(List<AiSkill> skills) {
            return skills.stream().map(s -> s.name() + "(" + s.level() + ")").collect(Collectors.joining(", "));
        }

        public String buildProjectSkillGapBatchPrompt(String requiredSkills, List<CandidateProfileForAi> candidates) {
            String candidateData = candidates.stream()
                    .map(c -> "Candidate ID: " + c.userId() + " | Skills: " + formatSkills(c.skills()))
                    .collect(Collectors.joining("\n"));

            return String.format("""
                You are an expert technical AI recruiter. Compare each candidate's skills with the project requirements.
                
                Project Required Skills: %s
                
                Candidates:
                %s
                
                Return the output EXACTLY as a JSON array using this structure:
                [
                  {
                    "userId": "UUID string",
                    "matchPercentage": 85.5,
                    "rationale": "Short 2 sentence explanation of why they are a good/bad fit.",
                    "matchedSkills": [
                       { "skillName": "Java", "importance": "CRITICAL" }
                    ],
                    "missingSkills": [
                       { "skillName": "AWS", "importance": "NICE_TO_HAVE" }
                    ]
                  }
                ]
                """, requiredSkills, candidateData);
        }
    }
}