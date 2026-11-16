package com.skillsync.skillsync_ai.authentication.filters;

import com.skillsync.skillsync_ai.authentication.store.RateLimitStore;
import com.skillsync.skillsync_ai.authentication.store.RateLimitStore.TryConsumeResult;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limits /api/auth/** through the pluggable RateLimitStore:
 * Bucket4j + Redis (token bucket) by default, or Cacheify's atomic INCR
 * (fixed window) when app.kv.store=cacheify.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStore rateLimitStore;

    private static final long RATE_LIMIT = 5; // requests...
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1); // ...per minute

    /**
     * Health checks must not consume the login/register rate-limit budget, and
     * they should stay usable even when a shared IP has hit the auth limit.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/auth/ping".equals(path)
                || "/health".equals(path)
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/")) {
            String clientIp = getClientIp(request);

            TryConsumeResult result = rateLimitStore.tryConsume(clientIp, RATE_LIMIT, RATE_LIMIT_WINDOW);

            if (!result.allowed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/problem+json");
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));

                response.getWriter().write("""
                    {
                        "type": "about:blank",
                        "title": "Too Many Requests",
                        "status": 429,
                        "detail": "You have exceeded the rate limit of 5 requests per minute. Please try again later."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
