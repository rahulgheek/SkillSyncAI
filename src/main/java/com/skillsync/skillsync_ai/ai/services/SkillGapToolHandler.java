package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.repo.SkillGapAnalysisRepository;
import com.skillsync.skillsync_ai.ai.services.ToolHandler;
import com.skillsync.skillsync_ai.ai.services.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SkillGapToolHandler implements ToolHandler {

    private final SkillGapAnalysisRepository skillGapRepository;

    @Override
    public String getName() {
        return "get_latest_skill_gap";
    }

    @Override
    public GeminiRequest.FunctionDeclaration getDeclaration() {
        return GeminiRequest.FunctionDeclaration.builder()
                .name(getName())
                .description("Fetch the user's most recent skill gap analysis.")
                .parameters(Map.of("type", "OBJECT", "properties", Map.of()))
                .build();
    }

    @Override
    public ToolResult execute(UUID userId) {
        // Extract the data map first
        Object data = skillGapRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(gap -> Map.of(
                        "matchPercentage", gap.getMatchPercentage(),
                        "missingSkills", gap.getMissingSkills()
                )).orElse(Map.of("message", "No skill gaps found."));

        // Wrap it in the new ToolResult!
        return ToolResult.success(data);
    }
}