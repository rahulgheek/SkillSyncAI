package com.skillsync.skillsync_ai.authentication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class WebCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Specify Allowed Origins explicitly (Do NOT use "*" when allowCredentials is true)
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",       // Vite standard dev port
                "http://localhost:5173",       // Vite / React standard dev port
                "http://127.0.0.1:5173",       // Alternate local Vite port
                "https://unaired-maroon-cradling.ngrok-free.dev"  // Lovable Cloud Preview domain
        ));

        // 2. Expose essential HTTP Methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 3. Allow necessary security and content headers (Using "*" is safe and easier for custom headers)
        configuration.setAllowedHeaders(List.of("*"));

        // 4. Allow browser to expose headers to the frontend javascript if needed
        configuration.setExposedHeaders(List.of("Authorization"));

        // 5. CRITICAL FOR WEBSOCKETS & JWT: Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // 6. Max age for browser caching of the pre-flight OPTIONS request (1 hour)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this configuration to all paths including API and WebSockets
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}