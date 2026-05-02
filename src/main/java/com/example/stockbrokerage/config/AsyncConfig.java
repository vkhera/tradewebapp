package com.example.stockbrokerage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures Spring's async executor for @Async methods and
 * CompletableFuture background tasks.
 *
 * <p>Without explicit configuration, Spring uses the default ForkJoinPool,
 * which may be too small for our background job triggers. This executor
 * ensures that manual admin job triggers (which run in CompletableFuture)
 * have a dedicated thread pool and won't starve other threads.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * Creates a dedicated thread pool for async tasks.
     * Provides 4 core threads and up to 10 max threads for background jobs.
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-job-");
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("AsyncExecutor initialized: corePoolSize=4, maxPoolSize=10");
        return executor;
    }
}
