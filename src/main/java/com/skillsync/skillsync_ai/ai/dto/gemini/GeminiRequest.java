package com.skillsync.skillsync_ai.ai.dto.gemini;

import java.util.List;
import java.util.Map;

public record GeminiRequest(
        List<Content> contents,
        Map<String, String> generationConfig
) {
    public record Content(List<Part> parts) {}
    public record Part(String text) {}
}