package com.skillsync.skillsync_ai.authentication.services;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.skillsync.skillsync_ai.authentication.store.OtpStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit test without Spring context: OtpService against an in-memory OtpStore. */
class OtpServiceTest {

    /** Mimics the real stores: keys are whatever OtpService hands over, TTLs skipped. */
    private static final class InMemoryOtpStore implements OtpStore {
        private final Map<String, String> hashes = new HashMap<>();
        private final Map<String, Long> cooldowns = new HashMap<>();

        @Override public void storeOtpHash(String email, String otpHash) { hashes.put(email, otpHash); }
        @Override public Optional<String> getOtpHash(String email) { return Optional.ofNullable(hashes.get(email)); }
        @Override public void deleteOtpHash(String email) { hashes.remove(email); }
        @Override public void startCooldown(String email) { cooldowns.put(email, 45L); }
        @Override public boolean isOnCooldown(String email) { return cooldowns.getOrDefault(email, 0L) > 0; }
        @Override public long getCooldownSeconds(String email) { return cooldowns.getOrDefault(email, 0L); }
    }

    @Test
    void saveOtpStoresHashAndNeverThePlaintextCode() {
        InMemoryOtpStore store = new InMemoryOtpStore();
        OtpService service = new OtpService(store);

        service.saveOtp("USER@example.com", "123456");

        String stored = store.getOtpHash("user@example.com").orElseThrow();
        assertEquals(64, stored.length());
        assertNotEquals("123456", stored);
    }

    @Test
    void verifyAcceptsTheCodeRegardlessOfEmailCasingAndRejectsWrongCodes() {
        OtpService service = new OtpService(new InMemoryOtpStore());
        service.saveOtp("User@Example.COM", "123456");

        assertTrue(service.verifyOtp("user@example.com", "123456"));
        assertTrue(service.verifyOtp("USER@EXAMPLE.COM", "123456"));
        assertFalse(service.verifyOtp("user@example.com", "654321"));
        assertFalse(service.verifyOtp("someoneelse@example.com", "123456"));
    }

    @Test
    void deleteConsumesTheKey() {
        OtpService service = new OtpService(new InMemoryOtpStore());
        service.saveOtp("user@example.com", "123456");
        assertTrue(service.verifyOtp("user@example.com", "123456"));

        service.deleteOtp("user@example.com");

        assertFalse(service.verifyOtp("user@example.com", "123456"));
    }

    @Test
    void cooldownFlowDelegatesToTheStore() {
        OtpService service = new OtpService(new InMemoryOtpStore());

        assertFalse(service.isOnCooldown("user@example.com"));
        assertEquals(0L, service.getCooldownSeconds("user@example.com"));

        service.startCooldown("user@example.com");

        assertTrue(service.isOnCooldown("user@example.com"));
        assertTrue(service.getCooldownSeconds("user@example.com") > 0);
    }
}
