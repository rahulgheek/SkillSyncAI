package com.skillsync.skillsync_ai.common.utils;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;

public final class RecommendationConfidenceParser {

    private RecommendationConfidenceParser() {} // Prevent instantiation

    public static RecommendationConfidence parse(String text) {
        if (text == null || text.isBlank()) {
            return RecommendationConfidence.MEDIUM; // Safe fallback
        }

        String normalized = text.trim().toUpperCase();
        if (normalized.contains("HIGH")) return RecommendationConfidence.HIGH;
        if (normalized.contains("LOW")) return RecommendationConfidence.LOW;

        return RecommendationConfidence.MEDIUM;
    }
}