package com.skillsync.skillsync_ai.authentication.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private static final String OTP_PREFIX = "otp:verification:";
    private static final int OTP_EXPIRATION_MINUTES = 5;

    public void saveOtp(String email, String otp) {
        String key = OTP_PREFIX + email;
        redisTemplate.opsForValue().set(key, otp, Duration.ofMinutes(OTP_EXPIRATION_MINUTES));
    }

    public String getOtp(String email) {
        String key = OTP_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteOtp(String email) {
        String key = OTP_PREFIX + email;
        redisTemplate.delete(key);
    }
}