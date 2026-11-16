package com.skillsync.skillsync_ai.authentication.store;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * RateLimitStore over the Redis protocol via Bucket4j + Lettuce. Default backend;
 * token bucket: limit refills greedily over the window. Disable by switching to
 * app.kv.store=cacheify.
 */
@Component
@ConditionalOnProperty(name = "app.kv.store", havingValue = "redis", matchIfMissing = true)
public class Bucket4jRateLimitStore implements RateLimitStore {

    private static final String PREFIX = "rate_limit:ip:";

    private final ProxyManager<byte[]> proxyManager;

    public Bucket4jRateLimitStore(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public TryConsumeResult tryConsume(String identity, long limit, Duration window) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, window)
                        .build())
                .build();

        var bucket = proxyManager.builder().build((PREFIX + identity).getBytes(StandardCharsets.UTF_8), configuration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return TryConsumeResult.ok();
        }
        return TryConsumeResult.rejected(probe.getNanosToWaitForRefill() / 1_000_000_000);
    }
}
