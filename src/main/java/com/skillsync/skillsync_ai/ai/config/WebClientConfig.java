package com.skillsync.skillsync_ai.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper; // Add this import
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // Add this new bean to satisfy GeminiApiService
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}