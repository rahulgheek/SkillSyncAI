package com.skillsync.skillsync_ai.analytics.services;

import com.skillsync.skillsync_ai.ai.AiGateway;
import com.skillsync.skillsync_ai.analytics.dto.DashboardAnalyticsResponse;
import com.skillsync.skillsync_ai.chat.ChatGateway;
import com.skillsync.skillsync_ai.todo.TodoGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TodoGateway todoGateway;
    private final AiGateway aiGateway;
    private final ChatGateway chatGateway;

    @Cacheable(value = "dashboardAnalytics", key = "#userId")
    public DashboardAnalyticsResponse getDashboardData(UUID userId) {

        var todo = todoGateway.getTodoMetrics(userId);
        var ai = aiGateway.getDashboardMetrics(userId);
        var chat = chatGateway.getChatMetrics(userId);

        return new DashboardAnalyticsResponse(
                new DashboardAnalyticsResponse.TaskMetrics(todo.total(), todo.completed(), todo.completionPercentage()),
                new DashboardAnalyticsResponse.SkillMetrics(ai.latestMatchPercentage(), ai.topMissingSkills()),
                new DashboardAnalyticsResponse.EngagementMetrics(chat.totalConversations())
        );
    }
}