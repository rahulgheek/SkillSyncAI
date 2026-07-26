package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.gemini.GeminiRequest;
import com.skillsync.skillsync_ai.common.dto.ChatTurn;
import com.skillsync.skillsync_ai.common.enums.MessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiPayloadBuilder {

    private final PromptFactory promptFactory;

    public GeminiRequest buildChatRequest(
            List<ChatTurn> chatHistory, // CHANGED: Now expects ChatTurn
            String newUserMessage,
            List<GeminiRequest.Tool> availableTools) {

        List<GeminiRequest.Content> contents = new ArrayList<>();

        for (ChatTurn turn : chatHistory) {
            if (turn.role() == MessageRole.USER || turn.role() == MessageRole.MODEL) {
                contents.add(GeminiRequest.Content.builder()
                        .role(turn.role().name().toLowerCase())
                        .parts(List.of(GeminiRequest.Part.builder().text(turn.content()).build()))
                        .build());
            }
        }

        contents.add(GeminiRequest.Content.builder()
                .role("user")
                .parts(List.of(GeminiRequest.Part.builder().text(newUserMessage).build()))
                .build());

        GeminiRequest.SystemInstruction systemContext = GeminiRequest.SystemInstruction.builder()
                .parts(List.of(GeminiRequest.Part.builder()
                        .text(promptFactory.getMentorSystemPrompt()) // NOW USING THE FACTORY
                        .build()))
                .build();

        return GeminiRequest.builder()
                .contents(contents)
                .systemInstruction(systemContext)
                .tools(availableTools)
                .build();
    }
}