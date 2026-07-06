package com.skillsync.skillsync_ai.authentication.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Bucket4jConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedisClient lettuceRedisClient() {
        return RedisClient.create("redis://" + redisHost + ":" + redisPort);
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisClient lettuceRedisClient) {
        return LettuceBasedProxyManager.builderFor(lettuceRedisClient)
                // Automatically clean up Redis keys when a bucket has sat full and unused
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }
}