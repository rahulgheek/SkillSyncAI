package com.skillsync.skillsync_ai.authentication.store;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * OtpStore over the Redis protocol (StringRedisTemplate). Default backend.
 * Switch to the HTTP transport with app.otp.store=cacheify (OTP_STORE=cacheify).
 */
@Repository
@ConditionalOnProperty(name = "app.kv.store", havingValue = "redis", matchIfMissing = true)
public class RedisOtpStore implements OtpStore {

    private static final String OTP_PREFIX = "otp:verification:";
    private static final String COOLDOWN_PREFIX = "otp:cooldown:";

    private final StringRedisTemplate redisTemplate;

    public RedisOtpStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void storeOtpHash(String email, String otpHash) {
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otpHash, OtpStore.OTP_TTL);
    }

    @Override
    public Optional<String> getOtpHash(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(OTP_PREFIX + email));
    }

    @Override
    public void deleteOtpHash(String email) {
        redisTemplate.delete(OTP_PREFIX + email);
    }

    @Override
    public void startCooldown(String email) {
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", OtpStore.COOLDOWN_TTL);
    }

    @Override
    public boolean isOnCooldown(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_PREFIX + email));
    }

    @Override
    public long getCooldownSeconds(String email) {
        Long ttl = redisTemplate.getExpire(COOLDOWN_PREFIX + email);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }
}
