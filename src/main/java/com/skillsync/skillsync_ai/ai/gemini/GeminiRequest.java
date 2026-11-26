package com.skillsync.skillsync_ai.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiRequest {
    private List<Content> contents;
    private SystemInstruction systemInstruction;
    private List<Tool> tools;
    private GenerationConfig generationConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String role; // "user" or "model"
        private List<Part> parts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Part {
        private String text;
        private FunctionCall functionCall;
        private FunctionResponse functionResponse;
        /**
         * Set by Gemini on internal reasoning parts. Request payloads leave it
         * null, so the field is omitted from the outgoing JSON.
         */
        private Boolean thought;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemInstruction {
        private List<Part> parts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private List<FunctionDeclaration> functionDeclarations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDeclaration {
        private String name;
        private String description;
        private Map<String, Object> parameters; // JSON Schema for arguments
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private Map<String, Object> args;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionResponse {
        private String name;
        private Object response; // CHANGED: Now accepts ToolResult (or any object)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GenerationConfig {
        private String responseMimeType;
        private Double temperature;
        private Integer maxOutputTokens;
        private ThinkingConfig thinkingConfig;
    }

    /**
     * Gemini 2.5 Flash enables "thinking" by default. Thinking tokens share the
     * output budget, which can truncate structured JSON and leave the app with
     * unparseable responses. Setting the budget to zero keeps these requests
     * deterministic and fast.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingConfig {
        private Integer thinkingBudget;
    }
}