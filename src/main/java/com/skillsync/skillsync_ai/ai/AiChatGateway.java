package com.skillsync.skillsync_ai.ai;

import com.skillsync.skillsync_ai.common.dto.ChatTurn;
import java.util.List;
import java.util.UUID;

public interface AiChatGateway {
    String processChatTurn(UUID userId, List<ChatTurn> history,String newMessage);
}