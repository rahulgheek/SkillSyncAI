package com.skillsync.skillsync_ai.messaging.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // Ensures execution before HTTP Security filters block it
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final AuthChannelInterceptor authChannelInterceptor;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String configuredFrontend = frontendUrl == null
                ? ""
                : frontendUrl.trim().replaceAll("/+$", "");

        java.util.List<String> originPatterns = new java.util.ArrayList<>(java.util.List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.vercel.app",
                "https://*.onrender.com"));
        // Custom production domain from FRONTEND_URL. The Origin header never
        // has a trailing slash, so it is stripped above.
        if (!configuredFrontend.isEmpty()) {
            originPatterns.add(configuredFrontend);
        }

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic for broadcasting to subscribers (e.g., /topic/projects/{id})
        // Queue for direct user messages (e.g., /user/queue/messages,
        // /user/queue/notifications)
        registry.enableSimpleBroker("/topic", "/queue")
                // Heartbeats keep idle connections alive through Render's proxy
                // and free-tier idle timeouts. Clients must answer them.
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        // Prefix for inbound messages from clients (e.g., /app/chat.sendMessage)
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix used for sending to specific users
        registry.setUserDestinationPrefix("/user");
        // Chat messages and notifications must arrive in the order they were
        // published, even when multiple server threads produce them.
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    /**
     * Dedicated single-thread scheduler for STOMP heartbeats. Keeping it
     * separate from the general {@code taskScheduler} prevents a slow
     * background job from delaying heartbeats and causing reconnects.
     */
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        // Initialization happens through afterPropertiesSet() when Spring
        // creates the bean.
        return scheduler;
    }
}
