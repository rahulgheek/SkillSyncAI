package com.skillsync.skillsync_ai.common.dto;

import java.io.Serializable;

/**
 * One AI-curated learning material attached to a roadmap checkpoint/task.
 * YouTube resources are stored as {@code YOUTUBE_VIDEO}; when the model cannot
 * provide a verified video URL the service falls back to a YouTube search URL
 * so the link always works.
 */
public record LearningResourceDto(
        String title,
        String channel,
        String url,
        String type,
        String description,
        String duration
) implements Serializable {
}
