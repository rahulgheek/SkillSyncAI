package com.skillsync.skillsync_ai.ai.services;

// Gemini will automatically serialize this into the exact JSON object it needs
public record ToolResult(
        boolean success,
        Object data,
        String errorMessage
) {
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null);
    }
    public static ToolResult error(String message) {
        return new ToolResult(false, null, message);
    }
}