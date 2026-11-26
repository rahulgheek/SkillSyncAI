package com.skillsync.skillsync_ai.ai.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    @Value("${groq.api.model:llama-3.1-70b-versatile}")
    private String groqModel;

    @Value("${groq.api.timeout-seconds:20}")
    private int timeoutSeconds;

    /**
     * Executes exactly one HTTP request. Retries are deliberately not used so
     * a failed call is reported immediately instead of being sent twice.
     */
    public Mono<String> analyzeBatchMatch(String prompt) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return Mono.error(new IllegalStateException("GROQ_API_KEY is not configured"));
        }

        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
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
                .map(this::extractContent)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .doOnError(e -> log.error("Failed to call Groq API: {}", e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return "";
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            return "";
        }
        Object content = message.get("content");
        return content == null ? "" : content.toString();
    }
}
