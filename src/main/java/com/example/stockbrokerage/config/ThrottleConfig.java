package com.example.stockbrokerage.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO that maps to throttle-config.yaml.
 *
 * Global defaults apply to every @Service.
 * Per-service overrides (under "services:") replace the entire
 * rate-limiter or circuit-breaker block for that service;
 * omitted blocks fall back to the global defaults.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThrottleConfig {

    private GlobalDefaults defaults = new GlobalDefaults();
    private Map<String, ServiceOverride> services = new HashMap<>();

    // ── Nested types ────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GlobalDefaults {
        private RateLimiterSettings rateLimiter = new RateLimiterSettings();
        private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();
    }

    /** Per-service block — null sub-object means "use global defaults". */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceOverride {
        private RateLimiterSettings rateLimiter;      // null → inherit defaults
        private CircuitBreakerSettings circuitBreaker; // null → inherit defaults
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RateLimiterSettings {
        /** Set to false to disable rate limiting for a service entirely. */
        private boolean enabled = true;
        /** Maximum number of calls allowed within limitRefreshPeriodMs. Default = 1 TPS. */
        private int limitForPeriod = 1;
        /** The period (ms) in which limitForPeriod calls are allowed. Default 1 000 ms. */
        private long limitRefreshPeriodMs = 1_000;
        /**
         * How long to wait for a permit before throwing.
         * 0 = fail immediately (no queuing).
         */
        private long timeoutDurationMs = 0;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CircuitBreakerSettings {
        /** Set to false to disable the circuit breaker for a service. */
        private boolean enabled = true;
        /** % of calls that must fail to open the circuit (0–100). */
        private float failureRateThreshold = 50.0f;
        /** % of calls that must be slow to open the circuit (0–100). */
        private float slowCallRateThreshold = 80.0f;
        /** Calls taking longer than this (ms) are counted as slow. */
        private long slowCallDurationThresholdMs = 2_000;
        /** Number of recent calls tracked for failure rate. */
        private int slidingWindowSize = 10;
        /** Calls allowed through while circuit is HALF_OPEN. */
        private int permittedCallsInHalfOpen = 3;
        /** Time (ms) the circuit stays OPEN before moving to HALF_OPEN. */
        private long waitDurationInOpenMs = 30_000;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns the effective rate-limiter settings for a given service:
     * uses the service-specific block if it exists, otherwise the global defaults.
     */
    public RateLimiterSettings effectiveRateLimiter(String serviceName) {
        ServiceOverride svc = services.get(serviceName);
        if (svc != null && svc.getRateLimiter() != null) {
            return svc.getRateLimiter();
        }
        return defaults.getRateLimiter();
    }

    /**
     * Returns the effective circuit-breaker settings for a given service.
     */
    public CircuitBreakerSettings effectiveCircuitBreaker(String serviceName) {
        ServiceOverride svc = services.get(serviceName);
        if (svc != null && svc.getCircuitBreaker() != null) {
            return svc.getCircuitBreaker();
        }
        return defaults.getCircuitBreaker();
    }
}
