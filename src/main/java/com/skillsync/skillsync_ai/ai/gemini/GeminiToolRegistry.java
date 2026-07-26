package com.skillsync.skillsync_ai.ai.gemini;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GeminiToolRegistry {

    // This returns the exact JSON schema Gemini needs to understand our tools
    public List<GeminiRequest.Tool> getAvailableTools() {
        GeminiRequest.FunctionDeclaration getTodosTool = GeminiRequest.FunctionDeclaration.builder()
                .name("get_user_todos")
                .description("Fetch the user's current pending and in-progress tasks/todos from their learning roadmap.")
                // Empty parameters object because this tool doesn't need arguments (it just fetches for the logged-in user)
                .parameters(Map.of("type", "OBJECT", "properties", Map.of()))
                .build();

        GeminiRequest.FunctionDeclaration getSkillGapTool = GeminiRequest.FunctionDeclaration.builder()
                .name("get_latest_skill_gap")
                .description("Fetch the user's most recent skill gap analysis to understand what skills they are missing.")
                .parameters(Map.of("type", "OBJECT", "properties", Map.of()))
                .build();

        return List.of(GeminiRequest.Tool.builder()
                .functionDeclarations(List.of(getTodosTool, getSkillGapTool))
                .build());
    }
}