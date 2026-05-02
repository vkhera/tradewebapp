package com.example.stockbrokerage.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aspect that intercepts all @Scheduled methods to provide consistent
 * logging, error handling, and execution time tracking.
 *
 * <p>This ensures that every batch job—whether it has explicit try-catch
 * or not—is properly logged with:
 * <ul>
 *   <li>Start timestamp and thread name</li>
 *   <li>Duration and completion status</li>
 *   <li>Exceptions with full stack trace (not swallowed)</li>
 * </ul>
 *
 * <p>This makes it much easier to debug why jobs are missing or failing
 * via the application logs and the Admin job history endpoint.
 */
@Aspect
@Component
@Slf4j
public class ScheduledTaskAspect {

    @Around("@annotation(scheduled)")
    public Object aroundScheduledTask(ProceedingJoinPoint joinPoint, Scheduled scheduled) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        // Log job start
        log.info("BATCH_JOB_START [{}] {}.{}() on thread {}", 
                methodName, className, methodName, threadName);

        try {
            // Execute the scheduled method
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("BATCH_JOB_SUCCESS [{}] {}.{}() completed in {}ms", 
                    methodName, className, methodName, duration);
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BATCH_JOB_FAILURE [{}] {}.{}() failed after {}ms: {}", 
                    methodName, className, methodName, duration, e.getMessage(), e);
            
            // Re-throw so that any existing error handling can also log it
            // This prevents swallowing exceptions while still providing our enhanced logging
            throw e;
        }
    }
}
