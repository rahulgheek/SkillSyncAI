package com.skillsync.skillsync_ai.authentication.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client settings for the Cacheify transport (see CacheifyClient and the
 * CacheifyOtpStore / exchange-code / rate-limit implementations).
 * Only created when the Cacheify backend is selected, so the Redis default
 * deployment never pays for an unused bean.
 */
@Configuration
public class CacheifyClientConfig {

    @Bean
    @ConditionalOnProperty(name = "app.kv.store", havingValue = "cacheify")
    RestTemplate cacheifyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
