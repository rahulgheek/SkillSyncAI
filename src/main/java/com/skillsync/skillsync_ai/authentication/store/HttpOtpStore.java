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
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Same OtpStore contract, Cacheify over HTTP/JSON instead of the Redis protocol.
 *
 * <pre>
 * storeOtpHash / startCooldown -> PUT    /api/data/{dbId}/strings/{key}   (value + expiresInMs)
 * getOtpHash / getCooldownSeconds -> GET /api/data/{dbId}/keys/{key}      (value + ttlMs)
 * deleteOtpHash                -> DELETE /api/data/{dbId}/keys/{key}
 * </pre>
 *
 * Key names are URL-encoded (covers ":" and "@" in e-mail addresses) and emails are
 * stored lower-cased so one user has exactly one key regardless of register/verify casing.
 * A 404 (expired, consumed, or never issued) maps to empty/0 exactly like a Redis nil read;
 * every other status (401/403/409/413/422/429/503) is surfaced so OTP flows fail loudly.
 *
 * Selected with app.otp.store=cacheify (OTP_STORE=cacheify).
 */
@Repository
@ConditionalOnProperty(name = "app.kv.store", havingValue = "cacheify")
public class HttpOtpStore implements OtpStore {

    private static final String OTP_PREFIX = "skillsync:otp:verification:";
    private static final String COOLDOWN_PREFIX = "skillsync:otp:cooldown:";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<Map<String, Object>>() { };

    private final RestTemplate http;
    private final String baseUrl;
    private final String databaseId;
    private final String apiKey;

    public HttpOtpStore(RestTemplate cacheifyRestTemplate,
                        @Value("${cacheify.base-url}") String baseUrl,
                        @Value("${cacheify.database-id}") String databaseId,
                        @Value("${cacheify.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OTP_STORE=cacheify requires CACHEIFY_API_KEY (a read-write key scoped to that database).");
        }
        this.http = cacheifyRestTemplate;
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.databaseId = databaseId;
        this.apiKey = apiKey;
    }

    @Override
    public void storeOtpHash(String email, String otpHash) {
        writeKey(otpKey(email), otpHash, OtpStore.OTP_TTL);
    }

    @Override
    public Optional<String> getOtpHash(String email) {
        try {
            return Optional.ofNullable(extractValue(readKey(otpKey(email))));
        } catch (UnknownKeyException missing) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteOtpHash(String email) {
        try {
            http.exchange(keysUri(otpKey(email)), HttpMethod.DELETE, deleteEntity(), String.class);
        } catch (HttpStatusCodeException e) {
            if (!isNotFound(e)) { // deleting an unknown key is a no-op, like Redis DEL
                throw asIntegrationError(e);
            }
        }
    }

    @Override
    public void startCooldown(String email) {
        writeKey(cooldownKey(email), "1", OtpStore.COOLDOWN_TTL);
    }

    @Override
    public boolean isOnCooldown(String email) {
        return getCooldownSeconds(email) > 0;
    }

    @Override
    public long getCooldownSeconds(String email) {
        try {
            Number ttlMs = extractTtlMillis(readKey(cooldownKey(email)));
            // A response without ttlMs must NOT let resends bypass the cooldown window
            return ttlMs == null
                    ? OtpStore.COOLDOWN_TTL.toSeconds()
                    : Math.max(0, (long) Math.ceil(ttlMs.doubleValue() / 1000d));
        } catch (UnknownKeyException missing) {
            return 0;
        }
    }

    private void writeKey(String key, String value, Duration ttl) {
        Map<String, Object> payload = Map.of("value", value, "expiresInMs", ttl.toMillis());
        try {
            http.exchange(stringsUri(key), HttpMethod.PUT, bodyEntity(payload), String.class);
        } catch (HttpStatusCodeException e) {
            throw asIntegrationError(e);
        }
    }

    private Map<String, Object> readKey(String key) {
        try {
            ResponseEntity<Map<String, Object>> response = http.exchange(
                    keysUri(key), HttpMethod.GET, new HttpEntity<Map<String, Object>>(readHeaders()), JSON_OBJECT);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            if (isNotFound(e)) { // 404: never issued, expired, or consumed -> like a Redis nil read
                throw new UnknownKeyException();
            }
            throw asIntegrationError(e);
        }
    }

    private String otpKey(String email) {
        return OTP_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email;
    }

    private URI stringsUri(String key) {
        return URI.create(baseUrl + "/api/data/" + databaseId + "/strings/" + encodeKey(key));
    }

    private URI keysUri(String key) {
        return URI.create(baseUrl + "/api/data/" + databaseId + "/keys/" + encodeKey(key));
    }

    private static String encodeKey(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8);
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

    private HttpEntity<Void> deleteEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return new HttpEntity<>(headers);
    }

    private static boolean isNotFound(HttpStatusCodeException e) {
        return e.getStatusCode().value() == 404;
    }

    private IllegalStateException asIntegrationError(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        return new IllegalStateException("Cacheify request failed (HTTP " + e.getStatusCode().value()
                + (body == null || body.isBlank() ? "):" : "): " + body));
    }

    @SuppressWarnings("unchecked")
    private static String extractValue(Map<String, Object> envelope) {
        Object value = keySection(envelope).get("value");
        return value == null ? null : String.valueOf(value);
    }

    private static Number extractTtlMillis(Map<String, Object> envelope) {
        return (Number) keySection(envelope).get("ttlMs");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> keySection(Map<String, Object> envelope) {
        if (envelope == null) {
            return Map.of();
        }
        Object data = envelope.get("data");
        Object key = (data instanceof Map<?, ?> dataMap) ? dataMap.get("key") : null;
        return (key instanceof Map<?, ?> keyMap) ? (Map<String, Object>) keyMap : Map.of();
    }

    /** Internal marker: HTTP 404 translated into "missing key" (Redis nil semantics). */
    private static final class UnknownKeyException extends RuntimeException { }
}
