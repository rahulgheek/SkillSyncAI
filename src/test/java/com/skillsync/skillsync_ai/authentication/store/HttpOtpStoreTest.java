package com.skillsync.skillsync_ai.authentication.store;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Plain JUnit &mdash; no Spring context and no network: the RestTemplate is backed
 * by MockRestServiceServer so the Cacheify request/response contract is verified.
 */
class HttpOtpStoreTest {

    private static final String BASE = "https://cacheify.test";
    private static final String DB_ID = "rdb_test";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HttpOtpStore store;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        store = new HttpOtpStore(restTemplate, BASE, DB_ID, "test-key");
    }

    private static URI keyUri(String key) {
        return URI.create(BASE + "/api/data/" + DB_ID + "/keys/" + URLEncoder.encode(key, StandardCharsets.UTF_8));
    }

    private static URI stringsUri(String key) {
        return URI.create(BASE + "/api/data/" + DB_ID + "/strings/" + URLEncoder.encode(key, StandardCharsets.UTF_8));
    }

    @Test
    void getOtpHashReadsTheStoredValueFromTheExactKeyItIsGiven() {
        server.expect(requestTo(keyUri("skillsync:otp:verification:dev@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        "{\"data\":{\"key\":{\"value\":\"f00dface\",\"ttlMs\":250000}}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("f00dface", store.getOtpHash("dev@x.com").orElseThrow());
        server.verify();
    }

    @Test
    void getOtpHashMaps404ToEmpty() {
        server.expect(requestTo(keyUri("skillsync:otp:verification:gone@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(store.getOtpHash("gone@x.com").isEmpty());
        server.verify();
    }

    @Test
    void storeOtpHashPutsGeneratedValueWithFiveMinuteTtl() {
        server.expect(requestTo(stringsUri("skillsync:otp:verification:dev@x.com")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("expiresInMs\":300000")))
                .andRespond(withSuccess());

        store.storeOtpHash("dev@x.com", "f00dface");
        server.verify();
    }

    @Test
    void deleteOtpHashIgnoresNotFound() {
        server.expect(requestTo(keyUri("skillsync:otp:verification:dev@x.com")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertDoesNotThrow(() -> store.deleteOtpHash("dev@x.com"));
        server.verify();
    }

    @Test
    void cooldownSecondsComeFromRemainingTtl() {
        // isOnCooldown() re-uses getCooldownSeconds(), so two GETs are issued in this test
        server.expect(requestTo(keyUri("skillsync:otp:cooldown:dev@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"key\":{\"value\":\"1\",\"ttlMs\":30000}}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(keyUri("skillsync:otp:cooldown:dev@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"key\":{\"value\":\"1\",\"ttlMs\":30000}}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(30L, store.getCooldownSeconds("dev@x.com"));
        assertTrue(store.isOnCooldown("dev@x.com"));
        server.verify();
    }

    @Test
    void cooldownIsZeroWhenKeyIsMissing() {
        server.expect(requestTo(keyUri("skillsync:otp:cooldown:calm@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(keyUri("skillsync:otp:cooldown:calm@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(0L, store.getCooldownSeconds("calm@x.com"));
        assertFalse(store.isOnCooldown("calm@x.com"));
        server.verify();
    }

    @Test
    void non404ErrorsAreSurfacedNotSwallowed() {
        server.expect(requestTo(keyUri("skillsync:otp:verification:dev@x.com")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(IllegalStateException.class, () -> store.getOtpHash("dev@x.com"));
        server.verify();
    }

    @Test
    void blankApiKeyFailsFastAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new HttpOtpStore(restTemplate, BASE, DB_ID, "   "));
    }
}
