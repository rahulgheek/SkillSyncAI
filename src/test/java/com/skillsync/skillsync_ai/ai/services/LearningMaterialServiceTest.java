package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.gemini.GeminiResponse;
import com.skillsync.skillsync_ai.common.dto.LearningResourceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningMaterialServiceTest {

    @Mock
    private GeminiApiService geminiApiService;

    private LearningMaterialService service;

    @BeforeEach
    void setUp() {
        service = new LearningMaterialService(geminiApiService);
    }

    // TC-LEARN-001: A valid YouTube URL from the AI is kept as-is.
    @Test
    void fromAiStep_keepsValidYouTubeVideo() {
        Map<String, Object> step = Map.of("learningResources", List.of(Map.of(
                "title", "Spring Boot Tutorial",
                "channel", "freeCodeCamp.org",
                "url", "https://www.youtube.com/watch?v=abc123",
                "type", "video",
                "description", "Popular full course",
                "duration", "1h 20m")));

        List<LearningResourceDto> resources = service.fromAiStep(step, "Spring Boot", "Backend Developer");

        assertThat(resources).hasSize(1);
        LearningResourceDto resource = resources.get(0);
        assertThat(resource.type()).isEqualTo(LearningMaterialService.YOUTUBE_VIDEO);
        assertThat(resource.url()).isEqualTo("https://www.youtube.com/watch?v=abc123");
        assertThat(resource.channel()).isEqualTo("freeCodeCamp.org");
    }

    // TC-LEARN-002: A video link that is not YouTube is replaced with a working search link.
    @Test
    void fromAiStep_replacesInvalidVideoUrlWithYouTubeSearch() {
        Map<String, Object> step = Map.of("learningResources", List.of(Map.of(
                "title", "Learn Java Streams",
                "url", "https://example.com/not-a-video",
                "type", "YOUTUBE_VIDEO")));

        List<LearningResourceDto> resources = service.fromAiStep(step, "Java Streams", "Backend Developer");

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).type()).isEqualTo(LearningMaterialService.YOUTUBE_VIDEO);
        assertThat(resources.get(0).url()).startsWith("https://www.youtube.com/results?search_query=");
    }

    // TC-LEARN-003: When the AI returns nothing, a YouTube search fallback is still provided.
    @Test
    void fromAiStep_withoutResources_addsFallbackVideo() {
        List<LearningResourceDto> resources = service.fromAiStep(Map.of(), "Docker", "Backend Developer");

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).type()).isEqualTo(LearningMaterialService.YOUTUBE_VIDEO);
        assertThat(resources.get(0).url()).contains("youtube.com/results?search_query=");
        assertThat(resources.get(0).title()).contains("Docker");
    }

    // TC-LEARN-004: Resource lists are de-duplicated and capped.
    @Test
    void normalize_deduplicatesAndCapsResources() {
        List<LearningResourceDto> input = List.of(
                video("One", "https://www.youtube.com/watch?v=one"),
                video("One duplicate", "https://www.youtube.com/watch?v=one"),
                video("Two", "https://www.youtube.com/watch?v=two"),
                video("Three", "https://www.youtube.com/watch?v=three"),
                video("Four", "https://www.youtube.com/watch?v=four"),
                video("Five", "https://www.youtube.com/watch?v=five"));

        List<LearningResourceDto> resources = service.normalize(input, "Topic", "Role");

        assertThat(resources).hasSize(4);
        assertThat(resources).extracting(LearningResourceDto::url).doesNotHaveDuplicates();
    }

    // TC-LEARN-005: The on-demand generator uses the AI response.
    @Test
    void generateForTask_usesAiResponse() {
        when(geminiApiService.generateLearningResources(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(geminiResponse("[]")));
        when(geminiApiService.parseLearningResources(anyString())).thenReturn(List.of(Map.of(
                "title", "Docker in 100 Seconds",
                "channel", "Fireship",
                "url", "https://www.youtube.com/watch?v=abc123",
                "type", "YOUTUBE_VIDEO")));

        List<LearningResourceDto> resources = service.generateForTask(
                "Docker", "Learn containers", "Build a small image.", "Backend Developer");

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).channel()).isEqualTo("Fireship");
    }

    private LearningResourceDto video(String title, String url) {
        return new LearningResourceDto(title, "Channel", url, "YOUTUBE_VIDEO", null, null);
    }

    private GeminiResponse geminiResponse(String text) {
        GeminiResponse response = new GeminiResponse();
        GeminiRequest.Content content = GeminiRequest.Content.builder()
                .role("model")
                .parts(List.of(GeminiRequest.Part.builder().text(text).build()))
                .build();
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate();
        candidate.setContent(content);
        response.setCandidates(List.of(candidate));
        return response;
    }
}
