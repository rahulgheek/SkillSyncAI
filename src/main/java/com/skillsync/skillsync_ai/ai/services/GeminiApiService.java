package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.dto.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.dto.gemini.GeminiResponse;
import com.skillsync.skillsync_ai.common.events.AiSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(List.of(new GeminiRequest.Part(prompt)))),
                Map.of("responseMimeType", "application/json") // Forces strict JSON output
        );

        WebClient webClient = webClientBuilder
                .defaultHeader("x-goog-api-key", geminiApiKey)
                .build();

        return webClient.post()
                .uri(geminiApiUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnError(e -> log.error("Failed to call Gemini API: {}", e.getMessage()));
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
                .collect(java.util.stream.Collectors.joining(", "));

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

        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(List.of(new GeminiRequest.Part(prompt)))),
                Map.of("responseMimeType", "application/json") // Forces strict JSON output
        );

        WebClient webClient = webClientBuilder
                .defaultHeader("x-goog-api-key", geminiApiKey)
                .build();

        return webClient.post()
                .uri(geminiApiUrl)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnError(e -> log.error("Failed to call Gemini API for skill gap: {}", e.getMessage()));
    }

    public Mono<GeminiResponse> generateRoadmap(String missingSkills) {
        String prompt = String.format("""
            You are an expert technical mentor. A student is missing the following skills required for a project: %s.
            Generate a step-by-step learning roadmap for them to acquire these skills.
            Return the output EXACTLY in this JSON array structure:
            [
              { "title": "Understand the Basics", "description": "Short explanation of what to learn first." },
              { "title": "Build a small project", "description": "Hands-on implementation details." }
            ]
            """, missingSkills);

        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(List.of(new GeminiRequest.Part(prompt)))),
                Map.of("responseMimeType", "application/json")
        );

        return webClientBuilder.defaultHeader("x-goog-api-key", geminiApiKey).build()
                .post().uri(geminiApiUrl).bodyValue(request).retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnError(e -> log.error("Failed to call Gemini API for roadmap: {}", e.getMessage()));
    }

    public List<Map<String, String>> parseRoadmapSteps(String jsonString) {
        try {
            // Jackson can parse a JSON Array directly into a List of Maps
            return objectMapper.readValue(jsonString, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.error("Failed to parse Gemini Roadmap JSON: {}", e.getMessage());
            throw new RuntimeException("AI roadmap response was invalid", e);
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

        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(List.of(new GeminiRequest.Part(prompt)))),
                Map.of("responseMimeType", "application/json")
        );

        return webClientBuilder.defaultHeader("x-goog-api-key", geminiApiKey).build()
                .post().uri(geminiApiUrl).bodyValue(request).retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnError(e -> log.error("Failed to call Gemini API for note summary: {}", e.getMessage()));
    }
}