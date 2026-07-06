package com.skillsync.skillsync_ai.authentication.filters;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
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

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<byte[]> proxyManager;
    private static final String RATE_LIMIT_PREFIX = "rate_limit:ip:";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/")) {
            String clientIp = getClientIp(request);
            String redisKey = RATE_LIMIT_PREFIX + clientIp;

            // Define the Token Bucket limit: 5 requests per minute
            BucketConfiguration configuration = BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1))
                            .build())
                    .build();

            // Fetch or create the bucket in Redis for this specific IP
            var bucket = proxyManager.builder().build(redisKey.getBytes(), configuration);

            // Attempt to consume 1 token
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/problem+json");

                // Add standard Retry-After header
                long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
                response.setHeader("Retry-After", String.valueOf(waitForRefill));

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