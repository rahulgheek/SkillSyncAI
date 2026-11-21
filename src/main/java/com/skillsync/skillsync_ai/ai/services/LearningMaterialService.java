package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.common.dto.LearningResourceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Normalises the learning materials returned by Gemini. The model is asked
 * for popular YouTube videos, but model-provided video URLs are not guaranteed
 * to exist. Every video therefore goes through one of two safe paths:
 *
 * <ol>
 *   <li>a valid YouTube URL is kept as-is;</li>
 *   <li>anything missing/invalid becomes a YouTube search URL built from the
 *       task title and topic, which always resolves.</li>
 * </ol>
 *
 * This keeps the feature useful without scraping YouTube or requiring an extra
 * API key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningMaterialService {

    static final String YOUTUBE_VIDEO = "YOUTUBE_VIDEO";
    static final String DOCUMENTATION = "DOCUMENTATION";
    static final String ARTICLE = "ARTICLE";
    static final String COURSE = "COURSE";
    static final String OTHER = "OTHER";

    private static final int MAX_RESOURCES = 4;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CHANNEL_LENGTH = 120;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_DURATION_LENGTH = 40;

    private final GeminiApiService geminiApiService;

    /**
     * Extracts resources from a task object returned by the roadmap prompts.
     * Accepts a few common key variants so a small model deviation does not
     * drop the whole recommendation list.
     */
    public List<LearningResourceDto> fromAiStep(Map<String, Object> step, String topic, String targetRole) {
        if (step == null) {
            return fallback(topic);
        }

        Object raw = firstNonNull(
                step.get("learningResources"),
                step.get("learning_resources"),
                step.get("resources"),
                step.get("learningMaterials"));

        List<LearningResourceDto> parsed = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                LearningResourceDto resource = parseItem(item);
                if (resource != null) {
                    parsed.add(resource);
                }
            }
        }

        return normalize(parsed, topic, targetRole);
    }

    /**
     * Asks the AI for materials when a checkpoint was created before this
     * feature existed, or when the user explicitly refreshes them.
     */
    public List<LearningResourceDto> generateForTask(
            String topic, String taskTitle, String taskDescription, String targetRole) {

        try {
            var response = geminiApiService
                    .generateLearningResources(topic, taskTitle, taskDescription, targetRole)
                    .block();

            if (response != null && response.getExtractedText() != null) {
                List<LearningResourceDto> parsed = new ArrayList<>();
                for (Map<String, Object> item : geminiApiService.parseLearningResources(response.getExtractedText())) {
                    LearningResourceDto resource = parseItem(item);
                    if (resource != null) {
                        parsed.add(resource);
                    }
                }
                return normalize(parsed, topic, targetRole);
            }
        } catch (Exception e) {
            log.warn("Failed to generate AI learning materials for '{}': {}", taskTitle, e.getMessage());
        }

        return fallback(topic);
    }

    /**
     * Validates, de-duplicates and caps the resource list. A YouTube fallback is
     * inserted when the AI produced no usable video.
     */
    public List<LearningResourceDto> normalize(
            List<LearningResourceDto> input, String topic, String targetRole) {

        Map<String, LearningResourceDto> unique = new LinkedHashMap<>();
        if (input != null) {
            for (LearningResourceDto raw : input) {
                if (raw == null) {
                    continue;
                }
                LearningResourceDto resource = sanitize(raw, topic);
                if (resource != null) {
                    unique.putIfAbsent(resource.url(), resource);
                }
                if (unique.size() >= MAX_RESOURCES) {
                    break;
                }
            }
        }

        List<LearningResourceDto> result = new ArrayList<>(unique.values());
        boolean hasVideo = result.stream().anyMatch(resource -> YOUTUBE_VIDEO.equals(resource.type()));
        if (!hasVideo) {
            result.add(0, youtubeSearch(topic, targetRole));
        }

        return result.stream().limit(MAX_RESOURCES).toList();
    }

    private LearningResourceDto parseItem(Object item) {
        if (item instanceof String title) {
            return new LearningResourceDto(clean(title), "YouTube", null, YOUTUBE_VIDEO, null, null);
        }
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }

        String title = clean(asString(map.get("title")));
        String url = firstNonBlank(
                asString(map.get("url")),
                asString(map.get("link")),
                asString(map.get("href")));
        if (title == null && url == null) {
            return null;
        }

        return new LearningResourceDto(
                title,
                firstNonBlank(
                        asString(map.get("channel")),
                        asString(map.get("author")),
                        asString(map.get("creator")),
                        asString(map.get("source"))),
                url,
                normalizeType(asString(map.get("type"))),
                firstNonBlank(
                        asString(map.get("description")),
                        asString(map.get("reason")),
                        asString(map.get("why"))),
                asString(map.get("duration")));
    }

    private LearningResourceDto sanitize(LearningResourceDto raw, String topic) {
        String title = clean(raw.title());
        String channel = clean(raw.channel());
        String description = clean(raw.description());
        String duration = clean(raw.duration());
        String type = normalizeType(raw.type());
        String url = clean(raw.url());

        boolean youtube = isYouTubeUrl(url);
        if (youtube) {
            type = YOUTUBE_VIDEO;
        }

        // A "video" that does not point at YouTube is not useful for this
        // feature, and a malformed URL would be a broken link. Replace both
        // with a YouTube search that always works.
        if (url == null || !isHttpUrl(url) || (YOUTUBE_VIDEO.equals(type) && !youtube)) {
            String query = title != null ? title : topic;
            url = youtubeSearchUrl(joinQuery(query, topic));
            type = YOUTUBE_VIDEO;
            if (title == null) {
                title = "Popular YouTube videos for " + safeTopic(topic);
            }
            if (channel == null) {
                channel = "YouTube";
            }
        }

        if (title == null) {
            title = "Learning resource";
        }

        return new LearningResourceDto(
                limit(title, MAX_TITLE_LENGTH),
                limit(channel, MAX_CHANNEL_LENGTH),
                limit(url, MAX_URL_LENGTH),
                type,
                limit(description, MAX_DESCRIPTION_LENGTH),
                limit(duration, MAX_DURATION_LENGTH));
    }

    private List<LearningResourceDto> fallback(String topic) {
        return List.of(youtubeSearch(topic, null));
    }

    private LearningResourceDto youtubeSearch(String topic, String targetRole) {
        String subject = safeTopic(topic);
        String query = targetRole != null && !targetRole.isBlank()
                ? subject + " " + targetRole
                : subject;

        return new LearningResourceDto(
                "Popular YouTube videos: " + subject,
                "YouTube",
                youtubeSearchUrl(query),
                YOUTUBE_VIDEO,
                "AI-curated search for highly rated videos on this topic.",
                null);
    }

    private String youtubeSearchUrl(String query) {
        return "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private String joinQuery(String first, String second) {
        if (first == null || first.isBlank()) {
            return safeTopic(second);
        }
        if (second == null || second.isBlank() || first.toLowerCase(Locale.ROOT).contains(second.toLowerCase(Locale.ROOT))) {
            return first;
        }
        return first + " " + second;
    }

    private boolean isYouTubeUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("youtube.com")
                    || host.endsWith(".youtube.com")
                    || host.equals("youtu.be");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (value) {
            case "YOUTUBE", "YOUTUBE_VIDEO", "VIDEO", "TUTORIAL_VIDEO" -> YOUTUBE_VIDEO;
            case "DOCUMENTATION", "DOCS", "DOC", "OFFICIAL_DOCS" -> DOCUMENTATION;
            case "ARTICLE", "BLOG", "BLOG_POST" -> ARTICLE;
            case "COURSE", "ONLINE_COURSE" -> COURSE;
            default -> OTHER;
        };
    }

    private static String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeTopic(String topic) {
        return topic == null || topic.isBlank() ? "this topic" : topic.trim();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
