package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.todo.TodoGateway; // Cross-module legal import!
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TodoToolHandler implements ToolHandler {

    private final TodoGateway todoGateway;

    @Override
    public String getName() {
        return "get_user_todos";
    }

    @Override
    public GeminiRequest.FunctionDeclaration getDeclaration() {
        return GeminiRequest.FunctionDeclaration.builder()
                .name(getName())
                .description("Fetch the user's current pending tasks/todos.")
                .parameters(Map.of("type", "OBJECT", "properties", Map.of()))
                .build();
    }

    @Override
    public ToolResult execute(UUID userId) {
        Object data = Map.of(
                "todos",
                todoGateway.getActiveTodosForAi(userId)
        );

        return ToolResult.success(data);
    }
}