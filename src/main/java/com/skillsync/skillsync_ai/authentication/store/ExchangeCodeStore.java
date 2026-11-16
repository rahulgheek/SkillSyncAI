package com.skillsync.skillsync_ai.authentication.store;

import java.time.Duration;
import java.util.Optional;

/**
 * Persistence contract for the OAuth2 one-time exchange code (single-use
 * JWT bridge between the backend and the frontend callback).
 *
 * Redis semantics are mirrored exactly:
 * - write carries its own TTL (30 seconds), enforced server-side
 * - a missing key reads as "invalid or expired" (empty Optional)
 * - consume() = read + delete; the consumer must treat it as one-time
 */
public interface ExchangeCodeStore {

    /** Exchange codes live for exactly 30 seconds (matches the previous Redis TTL). */
    Duration EXCHANGE_TTL = Duration.ofSeconds(30);

    /** Stores token -> code mapping with the 30 second TTL. */
    void store(String code, String token);

    /** Returns the token once and removes the key; empty when consumed/expired/never issued. */
    Optional<String> consume(String code);
}
