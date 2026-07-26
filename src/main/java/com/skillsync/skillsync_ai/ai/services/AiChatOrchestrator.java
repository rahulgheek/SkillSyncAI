package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.AiChatGateway;
import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.ai.services.ToolResult;
import com.skillsync.skillsync_ai.common.dto.ChatTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatOrchestrator implements AiChatGateway {

    private final GeminiApiService geminiApiService;
    private final GeminiPayloadBuilder payloadBuilder;
    private final GeminiToolExecutor toolExecutor;

    private static final int MAX_AGENT_ITERATIONS = 5;
    private static final long MAX_WALL_CLOCK_TIME_MS = 25000; // 25 Seconds

    @Override
    public String processChatTurn(UUID userId, List<ChatTurn> history, String newMessage) {
        GeminiRequest request = payloadBuilder.buildChatRequest(history, newMessage, toolExecutor.getAvailableTools());

        int iterations = 0;
        long startTime = System.currentTimeMillis();
        String lastToolCalled = null;

        while (iterations < MAX_AGENT_ITERATIONS) {
            // 1. Wall-Clock Timeout Check
            if (System.currentTimeMillis() - startTime > MAX_WALL_CLOCK_TIME_MS) {
                log.warn("Agent timed out for User: {}", userId);
                return "I'm taking too long to think about this. Let's try breaking it down.";
            }

            iterations++;
            var response = geminiApiService.executeRawChat(request).block();
            if (response == null) return "I'm offline right now.";

            if (response.getFunctionCall() == null) {
                return response.getExtractedText() != null ? response.getExtractedText() : "I couldn't process that.";
            }

            String toolName = response.getFunctionCall().getName();

            // 2. Hallucination Safeguard (Prevent infinite loops of the same tool)
            if (toolName.equals(lastToolCalled)) {
                log.warn("Agent hallucination loop detected on tool: {}", toolName);
                return "I'm having trouble retrieving that information properly right now.";
            }
            lastToolCalled = toolName;

            ToolResult toolResult = toolExecutor.execute(toolName, userId);

            request.getContents().add(GeminiRequest.Content.builder()
                    .role("user")
                    .parts(List.of(GeminiRequest.Part.builder()
                            .functionResponse(GeminiRequest.FunctionResponse.builder()
                                    .name(toolName)
                                    .response(toolResult)
                                    .build())
                            .build()))
                    .build());
        }

        return "I had to stop thinking about that, it was getting too complex!";
    }
}