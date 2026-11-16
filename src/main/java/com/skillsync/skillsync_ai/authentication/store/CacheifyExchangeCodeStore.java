package com.skillsync.skillsync_ai.authentication.store;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * ExchangeCodeStore over Cacheify's HTTP/JSON API (see CacheifyClient).
 * Selected with app.kv.store=cacheify (KV_STORE=cacheify).
 */
@Repository
@ConditionalOnProperty(name = "app.kv.store", havingValue = "cacheify")
public class CacheifyExchangeCodeStore implements ExchangeCodeStore {

    private static final String PREFIX = "skillsync:oauth2:exchange:";

    private final CacheifyClient client;

    public CacheifyExchangeCodeStore(CacheifyClient client) {
        this.client = client;
    }

    @Override
    public void store(String code, String token) {
        client.set(PREFIX + code, token, ExchangeCodeStore.EXCHANGE_TTL);
    }

    @Override
    public Optional<String> consume(String code) {
        Optional<CacheifyClient.CacheifyEntry> entry = client.get(PREFIX + code);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        client.delete(PREFIX + code); // remove so the code cannot be reused
        return Optional.ofNullable(entry.get().value());
    }
}
