package com.skillsync.skillsync_ai.authentication.store;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Thin HTTP client for Cacheify, the Redis-compatible data platform backing
 * everything Redis-shaped when app.kv.store=cacheify:
 *
 * <ul>
 *   <li><b>strings with TTL</b> &mdash; get / set / delete (keys &amp; strings endpoints):
 *       used by OtpStore and ExchangeCodeStore implementations.</li>
 *   <li><b>atomic INCR</b> + PTTL/PEXPIRE (command endpoint): fixed-window rate limiting.</li>
 * </ul>
 *
 * Every response uses the {"success":true,"data":...} envelope; Redis nil surfaces as
 * null (mapped to empty Optional) while 404 (expired/consumed/never issued) maps to a
 * Redis-nil read. Any other status (401/403/409/413/422/429/503) fails loudly.
 * Cacheify enforces a mandatory expiry on every key, so writes always pass expiresInMs.
 */
@Component
@ConditionalOnProperty(name = "app.kv.store", havingValue = "cacheify")
public class CacheifyClient {

    /** A stored string key: value plus the server-side remaining TTL in milliseconds. */
    public record CacheifyEntry(String value, Long ttlMillis) { }

    /** Raised for any non-404 HTTP failure against the Cacheify API. */
    public static final class CacheifyApiException extends IllegalStateException {
        public final int status;

        public CacheifyApiException(int status, String detail) {
            super("Cacheify request failed (HTTP " + status + "): " + detail);
            this.status = status;
        }
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<Map<String, Object>>() { };

    /** Live testing against Cacheify showed intermittent TLS terminates; transport failures are retried. */
    private static final int MAX_TRANSPORT_RETRIES = 2;
    private static final long[] RETRY_BACKOFF_MS = {200, 500};

    private final RestTemplate http;
    private final String baseUrl;
    private final String databaseId;
    private final String apiKey;

    public CacheifyClient(RestTemplate cacheifyRestTemplate,
                          @Value("${cacheify.base-url}") String baseUrl,
                          @Value("${cacheify.database-id}") String databaseId,
                          @Value("${cacheify.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "KV_STORE=cacheify requires CACHEIFY_API_KEY (a read-write key scoped to that database).");
        }
        this.http = cacheifyRestTemplate;
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.databaseId = databaseId;
        this.apiKey = apiKey;
    }

    /** GET /keys/{key} &mdash; value + remaining TTL; 404 == Redis nil. */
    public Optional<CacheifyEntry> get(String key) {
        try {
            ResponseEntity<Map<String, Object>> response = executeWithRetry(() -> http.exchange(
                    dataUri("keys/" + encode(key)), HttpMethod.GET,
                    new HttpEntity<Map<String, Object>>(readHeaders()), JSON_OBJECT));
            Map<String, Object> body = response.getBody();
            Object data = (body == null) ? null : body.get("data");
            Object keySection = (data instanceof Map<?, ?> dataMap) ? dataMap.get("key") : null;
            if (!(keySection instanceof Map<?, ?> keyMap)) {
                return Optional.empty();
            }
            Object rawValue = keyMap.get("value");
            Object rawTtl = keyMap.get("ttlMs");
            return Optional.of(new CacheifyEntry(
                    rawValue == null ? null : String.valueOf(rawValue),
                    rawTtl instanceof Number ttl ? ttl.longValue() : null));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) { // never issued, expired, or consumed
                return Optional.empty();
            }
            throw apiException(e);
        }
    }

    /** PUT /strings/{key} &mdash; set with an explicit TTL, mirroring opsForValue().set(key, v, ttl). */
    public void set(String key, String value, Duration ttl) {
        Map<String, Object> payload = Map.of("value", value, "expiresInMs", ttl.toMillis());
        try {
            executeWithRetry(() -> http.exchange(
                    dataUri("strings/" + encode(key)), HttpMethod.PUT, bodyEntity(payload), String.class));
        } catch (HttpStatusCodeException e) {
            throw apiException(e);
        }
    }

    /** DELETE /keys/{key} &mdash; idempotent like Redis DEL; deleting an unknown key is a no-op. */
    public void delete(String key) {
        try {
            executeWithRetry(() -> http.exchange(
                    dataUri("keys/" + encode(key)), HttpMethod.DELETE, new HttpEntity<Void>(readHeaders()), String.class));
        } catch (HttpStatusCodeException e) {
            if (!isNotFound(e)) {
                throw apiException(e);
            }
        }
    }

    /** Command INCR &mdash; atomic counter increment (creates the key with Cacheify's default TTL). */
    public Long incr(String key) {
        return numericReply(executeCommand("INCR", key));
    }

    /** Command PTTL &mdash; remaining TTL in milliseconds; null/whenn absent, the key is gone. */
    public Long pttl(String key) {
        return numericReply(executeCommand("PTTL", key));
    }

    /** Command PEXPIRE &mdash; re-arm the counter key's TTL. */
    public void pexpire(String key, long ttlMs) {
        executeCommand("PEXPIRE", key, String.valueOf(ttlMs));
    }

    private Object executeCommand(String command, String... args) {
        Map<String, Object> payload = Map.of("command", command, "args", List.of(args));
        try {
            ResponseEntity<Map<String, Object>> response = executeWithRetry(() -> http.exchange(
                    commandUri(), HttpMethod.POST, bodyEntity(payload), JSON_OBJECT));
            Map<String, Object> body = response.getBody();
            Object data = body == null ? null : body.get("data");
            // Reality probe: the command endpoint wraps replies as {"success":true,"data":{"result":<reply>}}
            if (data instanceof Map<?, ?> resultMap) {
                return resultMap.get("result");
            }
            return data;
        } catch (HttpStatusCodeException e) {
            throw apiException(e);
        }
    }

    private static Long numericReply(Object reply) {
        return reply instanceof Number number ? number.longValue() : null;
    }

    /**
     * Retries transport-level failures only (TLS handshake termination, connection reset)
     * with a short backoff. HTTP failures (401/403/404/429/503&hellip;) are never retried.
     */
    private <T> ResponseEntity<T> executeWithRetry(java.util.function.Supplier<ResponseEntity<T>> call) {
        IllegalStateException lastTransportFailure = null;
        for (int attempt = 0; attempt <= MAX_TRANSPORT_RETRIES; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException transportFailure) {
                lastTransportFailure = new IllegalStateException(
                        "Cacheify transport failure: " + transportFailure.getMessage(), transportFailure);
                if (attempt < MAX_TRANSPORT_RETRIES) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS[attempt]);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw lastTransportFailure;
    }

    private URI dataUri(String path) {
        return URI.create(baseUrl + "/api/data/" + databaseId + "/" + path);
    }

    private URI commandUri() {
        return URI.create(baseUrl + "/api/data/" + databaseId + "/command");
    }

    private static String encode(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8); // covers ":" and "@" in e-mails
    }

    private HttpHeaders readHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpEntity<Object> bodyEntity(Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private static boolean isNotFound(HttpStatusCodeException e) {
        return e.getStatusCode().value() == 404;
    }

    private static CacheifyApiException apiException(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        return new CacheifyApiException(e.getStatusCode().value(),
                body == null || body.isBlank() ? "(no body)" : body);
    }
}
