package com.skillsync.skillsync_ai.ai.gemini;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GeminiResponse {
    private List<Candidate> candidates;

    @Data
    public static class Candidate {
        private GeminiRequest.Content content;
        private String finishReason;
    }

    // --- Helper Methods to easily extract what Gemini wants ---

    public String getExtractedText() {
        if (candidates == null || candidates.isEmpty()) return null;
        var parts = candidates.get(0).getContent().getParts();
        if (parts == null || parts.isEmpty()) return null;
        return parts.get(0).getText();
    }

    public GeminiRequest.FunctionCall getFunctionCall() {
        if (candidates == null || candidates.isEmpty()) return null;
        var parts = candidates.get(0).getContent().getParts();
        if (parts == null || parts.isEmpty()) return null;
        return parts.get(0).getFunctionCall();
    }
}