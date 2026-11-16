package com.skillsync.skillsync_ai.authentication.store;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * ExchangeCodeStore over the Redis protocol. Default backend; disable in favour
 * of Cacheify with app.kv.store=cacheify (KV_STORE=cacheify).
 */
@Repository
@ConditionalOnProperty(name = "app.kv.store", havingValue = "redis", matchIfMissing = true)
public class RedisExchangeCodeStore implements ExchangeCodeStore {

    private static final String PREFIX = "oauth2_exchange:";

    private final StringRedisTemplate redisTemplate;

    public RedisExchangeCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String code, String token) {
        redisTemplate.opsForValue().set(PREFIX + code, token, ExchangeCodeStore.EXCHANGE_TTL);
    }

    @Override
    public Optional<String> consume(String code) {
        String key = PREFIX + code;
        String token = redisTemplate.opsForValue().get(key);
        if (token == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key); // best-effort single-use (GETDEL is not available)
        return Optional.of(token);
    }
}
