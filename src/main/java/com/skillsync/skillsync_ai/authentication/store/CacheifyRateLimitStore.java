package com.skillsync.skillsync_ai.authentication.store;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RateLimitStore over Cacheify's HTTP/JSON API. Instead of a Bucket4j token
 * bucket this uses the classic Redis fixed-window counter pattern, made safe by
 * Cacheify's atomic INCR command:
 *
 * <pre>
 * INCR  key            -> atomic count for this window
 * PTTL  key            -> remaining window TTL
 * PEXPIRE key window   -> only armed when the TTL is missing/longer than the window
 * </pre>
 *
 * The re-arm happens once when the counter is first created (Cacheify assigns a
 * default 24h TTL to new keys) or if the TTL was ever lost &mdash; it is stable
 * afterwards, so the window is fixed, not sliding. Selected with
 * app.kv.store=cacheify (KV_STORE=cacheify).
 */
@Component
@ConditionalOnProperty(name = "app.kv.store", havingValue = "cacheify")
public class CacheifyRateLimitStore implements RateLimitStore {

    private static final String PREFIX = "skillsync:rate-limit:ip:";

    private final CacheifyClient client;

    public CacheifyRateLimitStore(CacheifyClient client) {
        this.client = client;
    }

    @Override
    public TryConsumeResult tryConsume(String identity, long limit, Duration window) {
        String key = PREFIX + identity;

        Long count = client.incr(key); // atomic server-side; missing TTL falls back to the 24h default
        if (count == null) {
            // Defensive: command returned nil. Fail open rather than lock the endpoint out.
            return TryConsumeResult.ok();
        }

        Long pttl = client.pttl(key);
        if (pttl == null || pttl <= 0 || pttl > window.toMillis()) {
            client.pexpire(key, window.toMillis());
            pttl = window.toMillis();
        }

        if (count > limit) {
            return TryConsumeResult.rejected((long) Math.ceil(pttl / 1000d));
        }
        return TryConsumeResult.ok();
    }
}
