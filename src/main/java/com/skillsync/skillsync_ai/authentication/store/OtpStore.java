package com.skillsync.skillsync_ai.authentication.store;

import java.time.Duration;
import java.util.Optional;

/**
 * Persistence contract for OTP verification data, deliberately kept Redis-shaped:
 * a missing/expired key reads as "empty", and every write carries its own TTL that
 * is enforced server-side (TTL in Redis, expiresInMs over Cacheify).
 *
 * Implementations:
 * - RedisOtpStore &mdash; Redis protocol over TCP (default backend)
 * - HttpOtpStore  &mdash; Cacheify HTTP/JSON API (app.otp.store=cacheify)
 */
public interface OtpStore {

    /** Verification codes live for exactly 5 minutes, enforced by the store server. */
    Duration OTP_TTL = Duration.ofMinutes(5);

    /** OTP resend cooldown is exactly 45 seconds. */
    Duration COOLDOWN_TTL = Duration.ofSeconds(45);

    /** Stores the SHA-256 hash of the verification code (email already normalized). */
    void storeOtpHash(String email, String otpHash);

    /** Returns the stored hash, or empty when never issued / expired / consumed (Redis nil). */
    Optional<String> getOtpHash(String email);

    /** Drops the verification key; deleting an unknown key is a no-op (Redis DEL semantics). */
    void deleteOtpHash(String email);

    /** Starts the resend cooldown for the given email. */
    void startCooldown(String email);

    /** True while the resend cooldown is still active. */
    boolean isOnCooldown(String email);

    /** Seconds left in the resend cooldown, 0 when none. */
    long getCooldownSeconds(String email);
}
