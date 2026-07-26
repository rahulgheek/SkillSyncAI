package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import java.util.Map;
import java.util.UUID;

public interface ToolHandler {
    String getName();
    GeminiRequest.FunctionDeclaration getDeclaration();
    ToolResult execute(UUID userId); // Changed from Object to ToolResult
}