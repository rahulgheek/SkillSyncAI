package com.skillsync.skillsync_ai.ai.dto.gemini;

import java.util.List;

public record GeminiResponse(
        List<Candidate> candidates
) {
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public String getResponseText() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0).content().parts().get(0).text();
        }
        return "";
    }
}