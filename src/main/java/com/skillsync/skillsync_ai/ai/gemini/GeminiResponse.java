package com.skillsync.skillsync_ai.ai.gemini;

import lombok.Data;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class GeminiResponse {
    private List<Candidate> candidates;
    private PromptFeedback promptFeedback;

    @Data
    public static class Candidate {
        private GeminiRequest.Content content;
        private String finishReason;
    }

    @Data
    public static class PromptFeedback {
        private String blockReason;
        private List<SafetyRating> safetyRatings;
    }

    @Data
    public static class SafetyRating {
        private String category;
        private String probability;
        private Boolean blocked;
    }

    // --- Helper Methods to easily extract what Gemini wants ---

    /**
     * Returns the visible text of the first candidate, or {@code null} when
     * Gemini returned no usable text. Internal "thought" parts are skipped so
     * a reasoning trace can never be mistaken for the JSON answer.
     */
    public String getExtractedText() {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate candidate = candidates.get(0);
        if (candidate == null || candidate.getContent() == null) {
            return null;
        }
        var parts = candidate.getContent().getParts();
        if (parts == null || parts.isEmpty()) {
            return null;
        }

        String text = parts.stream()
                .filter(Objects::nonNull)
                .filter(part -> !Boolean.TRUE.equals(part.getThought()))
                .map(GeminiRequest.Part::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());

        return text.isBlank() ? null : text.trim();
    }

    public GeminiRequest.FunctionCall getFunctionCall() {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate candidate = candidates.get(0);
        if (candidate == null || candidate.getContent() == null) {
            return null;
        }
        var parts = candidate.getContent().getParts();
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        return parts.stream()
                .filter(Objects::nonNull)
                .map(GeminiRequest.Part::getFunctionCall)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public String getFinishReason() {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate candidate = candidates.get(0);
        return candidate == null ? null : candidate.getFinishReason();
    }

    public String getBlockReason() {
        return promptFeedback == null ? null : promptFeedback.getBlockReason();
    }
}
