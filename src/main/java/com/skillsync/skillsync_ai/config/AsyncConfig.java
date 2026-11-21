package com.skillsync.skillsync_ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-");
        // Spring calls afterPropertiesSet() -> initialize() when the bean is
        // created; calling initialize() here would create a second pool and
        // leak the first.
        return executor;
    }

    /**
     * Dedicated, deliberately small executor for AI matching jobs.
     *
     * <p>Groq calls can take tens of seconds. Running them on the shared
     * {@code taskExecutor} would starve email, roadmap and other background
     * work. The queue is bounded and uses {@link ThreadPoolExecutor.AbortPolicy}
     * so overload is visible (the job is marked FAILED) instead of silently
     * growing without bound or blocking an HTTP request thread.</p>
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("AiMatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
