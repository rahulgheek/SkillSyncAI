package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.services.ToolHandler;
import com.skillsync.skillsync_ai.ai.services.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeminiToolExecutor {

    private final Map<String, ToolHandler> handlers;
    private final List<GeminiRequest.Tool> availableTools;

    public GeminiToolExecutor(List<ToolHandler> toolHandlers) {
        this.handlers = toolHandlers.stream().collect(Collectors.toMap(ToolHandler::getName, h -> h));

        List<GeminiRequest.FunctionDeclaration> declarations = toolHandlers.stream()
                .map(ToolHandler::getDeclaration).toList();

        this.availableTools = List.of(GeminiRequest.Tool.builder().functionDeclarations(declarations).build());
    }

    public List<GeminiRequest.Tool> getAvailableTools() {
        return availableTools;
    }

    public ToolResult execute(String toolName, UUID userId) {
        long startTime = System.currentTimeMillis();
        try {
            ToolHandler handler = handlers.get(toolName);
            if (handler == null) {
                log.warn("User: {} | Tool: {} | Status: NOT_FOUND", userId, toolName);
                return ToolResult.error("Tool not found.");
            }

            ToolResult result = handler.execute(userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("User: {} | Tool: {} | Duration: {}ms | Status: SUCCESS", userId, toolName, duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("User: {} | Tool: {} | Duration: {}ms | Status: FAILED | Error: {}", userId, toolName, duration, e.getMessage());
            return ToolResult.error("Execution failed.");
        }
    }
}