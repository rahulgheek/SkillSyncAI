package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroqApiService {

    private final WebClient.Builder webClientBuilder;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    public Mono<String> analyzeBatchMatch(String prompt) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return Mono.error(new IllegalStateException("GROQ_API_KEY is not configured"));
        }

        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.1-70b-versatile",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        return webClientBuilder.build()
                .post()
                .uri(groqApiUrl)
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message != null && message.get("content") != null) {
                            return message.get("content").toString();
                        }
                    }
                    return "";
                })
                .timeout(Duration.ofSeconds(60))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.error("Failed to call Groq API: {}", e.getMessage()));
    }
}
