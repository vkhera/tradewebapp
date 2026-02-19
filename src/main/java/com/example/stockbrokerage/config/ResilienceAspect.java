package com.example.stockbrokerage.config;

import com.example.stockbrokerage.exception.RequestThrottledException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically applies rate limiting + circuit breaking
 * to every public method on every Spring @Service in this application.
 *
 * Design decisions:
 *  - ThreadLocal guard prevents double-application when one @Service calls another;
 *    only the outermost entry point is throttled, matching the "per-request" intent.
 *  - The service class's simple name is used as the key, matching the "services:"
 *    block in throttle-config.yaml (e.g. "TradeService", "PortfolioService").
 *  - If rate limiting is disabled for a service (enabled: false) the rate limiter
 *    entry is absent from the registry and the call passes through.
 *  - Circuit breaker fires AFTER the rate limit check.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ResilienceAspect {

    private final DynamicThrottleRegistry registry;

    /**
     * Guards against re-entrant application of resilience on nested service calls.
     * Set to true once we enter the outermost @Service boundary.
     */
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);

    @Around("within(@org.springframework.stereotype.Service *) " +
            "&& execution(public * com.example.stockbrokerage.service.*.*(..))") 
    public Object applyResilience(ProceedingJoinPoint pjp) throws Throwable {

        // Skip nested service-to-service calls — only throttle the outermost entry
        if (Boolean.TRUE.equals(INSIDE.get())) {
            return pjp.proceed();
        }

        INSIDE.set(true);
        try {
            return doWithResilience(pjp);
        } finally {
            INSIDE.remove();
        }
    }

    private Object doWithResilience(ProceedingJoinPoint pjp) throws Throwable {
        String serviceName = pjp.getTarget().getClass().getSimpleName();
        ThrottleConfig cfg = registry.getCurrentConfig();

        // ── 1. Determine effective settings for this service ───────────────
        ThrottleConfig.RateLimiterSettings    rlCfg = cfg.effectiveRateLimiter(serviceName);
        ThrottleConfig.CircuitBreakerSettings cbCfg = cfg.effectiveCircuitBreaker(serviceName);

        // ── 2. Rate-limiter check ──────────────────────────────────────────
        if (rlCfg.isEnabled()) {
            RateLimiter rl = registry.getRateLimiter(serviceName);
            boolean permitted = rl.acquirePermission();
            if (!permitted) {
                String msg = String.format(
                        "[%s] Rate limit exceeded (%d req / %d ms). Try again later.",
                        serviceName, rlCfg.getLimitForPeriod(), rlCfg.getLimitRefreshPeriodMs());
                log.warn(msg);
                throw new RequestThrottledException(msg);
            }
        }

        // ── 3. Circuit-breaker decoration ─────────────────────────────────
        if (cbCfg.isEnabled()) {
            CircuitBreaker cb = registry.getCircuitBreaker(serviceName);
            // Check if circuit is open before even attempting the call
            try {
                cb.acquirePermission();
            } catch (CallNotPermittedException ex) {
                String msg = String.format(
                        "[%s] Circuit is OPEN — failing fast. Will retry after %d ms.",
                        serviceName, cbCfg.getWaitDurationInOpenMs());
                log.warn(msg);
                throw new RequestThrottledException(msg);
            }

            long start = System.nanoTime();
            try {
                Object result = pjp.proceed();
                cb.onResult(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, result);
                return result;
            } catch (RequestThrottledException rte) {
                // Don't count throttle/circuit-open exceptions as failures
                cb.releasePermission();
                throw rte;
            } catch (Throwable t) {
                cb.onError(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, t);
                throw t;
            }
        }

        // ── 4. No circuit breaker — proceed normally ───────────────────────
        return pjp.proceed();
    }
}
