package com.skillsync.skillsync_ai.authentication.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bucket4j + Lettuce state for the Redis rate-limit backend. Only created
 * when app.kv.store=redis (default) so the Cacheify backend never spins up
 * a Redis client against its HTTPS endpoint &mdash; and vice versa.
 */
@Configuration
@ConditionalOnProperty(name = "app.kv.store", havingValue = "redis", matchIfMissing = true)
public class Bucket4jConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisClient lettuceRedisClient() {
        return RedisClient.create(redisUrl);
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisClient lettuceRedisClient) {
        return LettuceBasedProxyManager.builderFor(lettuceRedisClient)
                // Automatically clean up Redis keys when a bucket has sat full and unused
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }
}