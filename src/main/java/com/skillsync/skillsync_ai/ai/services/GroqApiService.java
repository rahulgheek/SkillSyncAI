package com.skillsync.skillsync_ai.ai.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

    @Value("${groq.api.model:llama-3.1-70b-versatile}")
    private String groqModel;

    @Value("${groq.api.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${groq.api.max-retries:1}")
    private int maxRetries;

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

        Mono<String> call = webClientBuilder.build()
                .post()
                .uri(groqApiUrl)
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContent)
                // The timeout is per attempt. With one retry the worst case is
                // roughly 2 x timeout, which stays well below the HTTP client
                // and reverse-proxy limits.
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .doOnError(e -> log.error("Failed to call Groq API: {}", e.getMessage()));

        if (maxRetries <= 0) {
            return call;
        }

        return call.retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(5))
                .jitter(0.5)
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying Groq call ({}/{}) after: {}",
                        signal.totalRetries() + 1, maxRetries, signal.failure().getMessage())));
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

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof IllegalStateException) {
            return false;
        }
        if (throwable instanceof WebClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.is5xxServerError() || status.value() == 429;
        }
        // Timeouts and transient network failures are worth one more attempt.
        return true;
    }
}
