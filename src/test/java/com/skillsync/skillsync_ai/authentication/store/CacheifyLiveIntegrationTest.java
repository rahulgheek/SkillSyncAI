package com.skillsync.skillsync_ai.authentication.store;

import java.io.FileInputStream;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LIVE smoke test against the Cacheify service (https://cacheify.bot-keep.xyz).
 *
 * It exercises the exact production transport (CacheifyClient) end-to-end:
 * - string set / get / delete with TTL          (OTP + OAuth2 exchange code primitive)
 * - atomic INCR + PTTL/PEXPIRE counter          (rate-limit primitive)
 *
 * Credentials come from the OS environment or from ./.env.properties; values are
 * never printed. Without a CACHEIFY_API_KEY the test is SKIPPED, so plain
 * {@code mvnw test} stays green on machines without Cacheify credentials.
 *
 * Run explicitly with:  mvnw test -Dtest=CacheifyLiveIntegrationTest
 */
class CacheifyLiveIntegrationTest {

    private static final CacheifyClient NO_CLIENT = null;

    private static final Properties ENV_FILE = new Properties();
    static {
        try (var in = new FileInputStream("./.env.properties")) {
            ENV_FILE.load(in);
        } catch (Exception ignored) {
            // no .env.properties -> fall back to OS environment only
        }
    }

    private CacheifyClient client;
    private String stringKey;
    private String counterKey;

    private static String config(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return ENV_FILE.getProperty(key);
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    @BeforeEach
    void setUp() {
        String baseUrl = firstNonBlank(config("CACHEIFY_BASE_URL"), "https://cacheify.bot-keep.xyz");
        String dbId = firstNonBlank(config("CACHEIFY_DB_ID"), "rdb_15d6788f-6d67-4b4f-af71-427a475358ca");
        String apiKey = config("CACHEIFY_API_KEY");

        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "CACHEIFY_API_KEY not configured (OS env or .env.properties) - skipping live Cacheify check");

        client = new CacheifyClient(new RestTemplate(), baseUrl, dbId, apiKey);

        String suffix = Long.toHexString(System.currentTimeMillis());
        stringKey = "skillsync:it:" + suffix + ":string";
        counterKey = "skillsync:it:" + suffix + ":counter";
    }

    @AfterEach
    void cleanUp() {
        if (client != null) {
            try { client.delete(stringKey); } catch (Exception ignored) { }
            try { client.delete(counterKey); } catch (Exception ignored) { }
        }
    }

    @Test
    void setGetDeleteRoundTripWorksOverHttps() {
        client.set(stringKey, "it-value-123", Duration.ofSeconds(30));

        CacheifyClient.CacheifyEntry entry = client.get(stringKey)
                .orElseThrow(() -> new AssertionError("SET wrote the key but GET could not read it back"));
        assertEquals("it-value-123", entry.value());
        assertNotNull(entry.ttlMillis());
        assertTrue(entry.ttlMillis() > 0, "server must keep a TTL on every key");

        client.delete(stringKey);
        assertTrue(client.get(stringKey).isEmpty(), "DELETE must surface as a Redis-nil read (404)");
    }

    @Test
    void atomicCounterAndTtlArmingWorksOverHttps() {
        assertEquals(1L, client.incr(counterKey));

        Long startTtl = client.pttl(counterKey);
        if (startTtl == null || startTtl <= 0) {
            fail("PTTL after INCR must be positive (Cacheify mandatory-expiry), was: " + startTtl);
        }

        // Window start: TTL defaults to 24h for brand-new keys -> PEXPIRE must cap it at our window
        client.pexpire(counterKey, 60_000);
        Long armed = client.pttl(counterKey);
        assertNotNull(armed);
        assertTrue(armed > 0 && armed <= 60_000,
                "PEXPIRE should cap the remaining TTL at 60s, but PTTL was " + armed + "ms");

        assertEquals(2L, client.incr(counterKey), "INCR must keep counting on the same key");
    }
}
