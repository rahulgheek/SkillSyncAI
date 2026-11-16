package com.skillsync.skillsync_ai.authentication.store;

import java.time.Duration;

/**
 * Persistence contract for distributed rate limiting. One implementation speaks
 * the Redis protocol through Bucket4j (token bucket), the other uses Cacheify's
 * atomic INCR command (fixed window). Both back the same /api/auth/** filter.
 */
public interface RateLimitStore {

    /**
     * Consumes one unit for the bucket belonging to the given identity (e.g. a client IP).
     *
     * @param identity identity of the caller, already normalized by the filter
     * @param limit    max consumes per window
     * @param window   window length
     * @return allowed (retryAfterSeconds = 0) or rejected with the seconds to wait
     */
    TryConsumeResult tryConsume(String identity, long limit, Duration window);

    /** Same shape as Bucket4j's ConsumptionProbe for the filter's 429 + Retry-After response. */
    record TryConsumeResult(boolean allowed, long retryAfterSeconds) {

        /** Shorthand for a consumed request (allowed = true, no waiting). */
        public static TryConsumeResult ok() {
            return new TryConsumeResult(true, 0);
        }

        /** Shorthand for a rejected request with the seconds the caller must wait. */
        public static TryConsumeResult rejected(long retryAfterSeconds) {
            return new TryConsumeResult(false, Math.max(0, retryAfterSeconds));
        }
    }
}
