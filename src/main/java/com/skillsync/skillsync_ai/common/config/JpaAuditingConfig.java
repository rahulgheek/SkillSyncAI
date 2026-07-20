package com.skillsync.skillsync_ai.common.config;

import com.skillsync.skillsync_ai.authentication.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            // Grab the active security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // If no one is logged in (or it's an internal system call), return empty
            if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
                return Optional.empty();
            }

            // Extract the User object and return their UUID!
            User currentUser = (User) authentication.getPrincipal();
            return Optional.of(currentUser.getId());
        };
    }
}