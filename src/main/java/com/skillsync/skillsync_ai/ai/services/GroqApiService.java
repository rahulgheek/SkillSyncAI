package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.exception.AiServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
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

    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final String DECOMMISSIONED_MODEL = "llama-3.1-70b-versatile";

    private final WebClient.Builder webClientBuilder;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.model:" + DEFAULT_MODEL + "}")
    private String groqModel;

    @Value("${groq.api.timeout-seconds:20}")
    private int timeoutSeconds;

    @PostConstruct
    void resolveModel() {
        if (groqModel == null || groqModel.isBlank() || DECOMMISSIONED_MODEL.equals(groqModel)) {
            log.warn("groq.api.model={} is not available from Groq. Using {} instead. "
                            + "Update or remove GROQ_API_MODEL in .env.properties.",
                    groqModel, DEFAULT_MODEL);
            groqModel = DEFAULT_MODEL;
        } else {
            log.info("Using Groq model {}", groqModel);
        }
    }

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
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> {
                            int status = response.statusCode().value();
                            log.error("Groq API returned {} for model {}: {}",
                                    status, groqModel, abbreviate(body, 1000));
                            return new AiServiceException(userFacingError(status));
                        }))
                .bodyToMono(Map.class)
                .map(this::extractContent)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .doOnError(e -> log.error("Failed to call Groq API: {}", e.getMessage()));
    }

    private String userFacingError(int status) {
        return switch (status) {
            case 400, 404 -> "The configured AI model is unavailable. Please try again later.";
            case 401, 403 -> "The AI provider rejected the request. Please try again later.";
            case 429 -> "The AI provider is rate-limiting requests. Please try again shortly.";
            default -> "The AI provider is temporarily unavailable. Please try again later.";
        };
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            log.warn("Groq response contained no choices: {}", abbreviate(String.valueOf(response), 500));
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            log.warn("Groq response contained an unexpected choice: {}",
                    abbreviate(String.valueOf(first), 500));
            return "";
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            log.warn("Groq response contained no message: {}", abbreviate(String.valueOf(choice), 500));
            return "";
        }
        Object content = message.get("content");
        if (content == null) {
            log.warn("Groq message contained no content: {}", abbreviate(String.valueOf(message), 500));
            return "";
        }
        return content.toString();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
