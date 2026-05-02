package com.example.stockbrokerage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures Spring's task scheduler with an adequate thread pool.
 *
 * <p>By default, Spring uses a single-threaded scheduler, which causes
 * concurrent scheduled jobs to block each other. With 8 threads, we can
 * comfortably run multiple jobs concurrently without starvation:
 * <ul>
 *   <li>Trend Analysis (10 min, ~1 min duration)</li>
 *   <li>Stock Predictions (60 min, ~1 min duration)</li>
 *   <li>Limit Order Processing (5 min)</li>
 *   <li>Reconciliation (1 min)</li>
 *   <li>Heartbeat (1 min)</li>
 *   <li>Daily jobs (cron-based)</li>
 * </ul>
 *
 * <p>Each scheduled method runs on its own deadline without being starved
 * by longer-running jobs.
 */
@Configuration
@Slf4j
public class TaskSchedulerConfig {

    /**
     * Creates a {@link ThreadPoolTaskScheduler} with 8 threads.
     * This ensures that scheduled jobs don't block each other.
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("batch-job-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.initialize();
        log.info("TaskScheduler initialized with pool size: 8");
        return scheduler;
    }
}
