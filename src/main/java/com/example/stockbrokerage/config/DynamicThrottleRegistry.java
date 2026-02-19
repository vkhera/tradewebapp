package com.example.stockbrokerage.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads throttle-config.yaml on startup and every 60 seconds.
 *
 * Config resolution order:
 *  1. File path in throttle.config-path property (default: config/throttle-config.yaml
 *     relative to the working directory — i.e. the project root when using mvn spring-boot:run)
 *  2. Classpath resource  throttle-config.yaml   (bundled in the JAR as a safe fallback)
 *  3. Hard-coded defaults (1 TPS, circuit breaker enabled)
 *
 * On every reload the rate-limiter and circuit-breaker instances are rebuilt.
 * All @Service classes are automatically covered via ResilienceAspect.
 */
@Service
@Slf4j
public class DynamicThrottleRegistry {

    private final String configFilePath;
    private final ObjectMapper yamlMapper;

    // Volatile references allow atomic swap on reload
    private volatile ThrottleConfig currentConfig;
    private volatile Map<String, RateLimiter>    rateLimiters    = new ConcurrentHashMap<>();
    private volatile Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private volatile Instant lastLoadedAt = Instant.EPOCH;
    private volatile String  lastLoadedFrom = "not yet loaded";

    public DynamicThrottleRegistry(
            @Value("${throttle.config-path:config/throttle-config.yaml}") String configFilePath) {

        this.configFilePath = configFilePath;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(
                        com.fasterxml.jackson.databind.PropertyNamingStrategies.KEBAB_CASE);

        loadConfig(); // initial load at startup
    }

    // ── Scheduled reload ───────────────────────────────────────────────────

    /**
     * Hot-reload every 60 seconds.
     * fixedDelay ensures the next run doesn't start until the previous one finishes,
     * preventing overlapping reloads under a slow filesystem.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void reloadConfig() {
        log.debug("Throttle config hot-reload triggered (path={})", configFilePath);
        loadConfig();
    }

    // ── Config loading ────────────────────────────────────────────────────

    private synchronized void loadConfig() {
        try {
            ThrottleConfig config = null;
            String loadedFrom;

            // 1. Try external file
            File file = new File(configFilePath);
            if (file.exists() && file.canRead()) {
                config = yamlMapper.readValue(file, ThrottleConfig.class);
                loadedFrom = "file:" + file.getAbsolutePath();
            } else {
                // 2. Try classpath fallback
                InputStream is = getClass().getClassLoader()
                                           .getResourceAsStream("throttle-config.yaml");
                if (is != null) {
                    try (is) {
                        config = yamlMapper.readValue(is, ThrottleConfig.class);
                    }
                    loadedFrom = "classpath:throttle-config.yaml";
                } else {
                    // 3. Hard-coded defaults
                    config = new ThrottleConfig();
                    loadedFrom = "built-in defaults";
                }
            }

            this.currentConfig   = config;
            this.lastLoadedAt    = Instant.now();
            this.lastLoadedFrom  = loadedFrom;

            rebuildInstances(config);
            log.info("Throttle config loaded from [{}] — {} service override(s)",
                     loadedFrom, config.getServices().size());

        } catch (Exception e) {
            log.error("Failed to reload throttle config (keeping previous config): {}", e.getMessage(), e);
        }
    }

    /** Rebuilds all Resilience4j instances based on the new config. */
    private void rebuildInstances(ThrottleConfig config) {
        Map<String, RateLimiter>    newRL = new ConcurrentHashMap<>();
        Map<String, CircuitBreaker> newCB = new ConcurrentHashMap<>();

        // Pre-build instances for all explicitly configured services
        for (String svcName : config.getServices().keySet()) {
            ThrottleConfig.RateLimiterSettings    rl = config.effectiveRateLimiter(svcName);
            ThrottleConfig.CircuitBreakerSettings cb = config.effectiveCircuitBreaker(svcName);

            if (rl.isEnabled()) {
                newRL.put(svcName, buildRateLimiter(svcName, rl));
            }
            if (cb.isEnabled()) {
                newCB.put(svcName, buildCircuitBreaker(svcName, cb));
            }
        }

        // Atomic swap — in-flight aspect calls finish on old instances, new calls get new ones
        this.rateLimiters    = newRL;
        this.circuitBreakers = newCB;
    }

    // ── Public accessors used by ResilienceAspect ─────────────────────────

    /**
     * Returns the RateLimiter for the given service, creating one lazily
     * from the current config if it doesn't already exist.
     */
    public RateLimiter getRateLimiter(String serviceName) {
        // Use computeIfAbsent for thread-safe lazy creation
        Map<String, RateLimiter> snapshot = rateLimiters;
        RateLimiter existing = snapshot.get(serviceName);
        if (existing != null) return existing;

        ThrottleConfig.RateLimiterSettings cfg =
                currentConfig.effectiveRateLimiter(serviceName);
        RateLimiter rl = buildRateLimiter(serviceName, cfg);
        // Best-effort put; another thread may have beaten us, that's fine
        ((ConcurrentHashMap<String, RateLimiter>) snapshot).putIfAbsent(serviceName, rl);
        return ((ConcurrentHashMap<String, RateLimiter>) snapshot).get(serviceName);
    }

    /**
     * Returns the CircuitBreaker for the given service, creating one lazily.
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        Map<String, CircuitBreaker> snapshot = circuitBreakers;
        CircuitBreaker existing = snapshot.get(serviceName);
        if (existing != null) return existing;

        ThrottleConfig.CircuitBreakerSettings cfg =
                currentConfig.effectiveCircuitBreaker(serviceName);
        CircuitBreaker cb = buildCircuitBreaker(serviceName, cfg);
        ((ConcurrentHashMap<String, CircuitBreaker>) snapshot).putIfAbsent(serviceName, cb);
        return ((ConcurrentHashMap<String, CircuitBreaker>) snapshot).get(serviceName);
    }

    public ThrottleConfig getCurrentConfig()  { return currentConfig; }
    public Instant        getLastLoadedAt()   { return lastLoadedAt; }
    public String         getLastLoadedFrom() { return lastLoadedFrom; }

    /**
     * Returns a snapshot of current circuit-breaker states (for the status API).
     */
    public Map<String, String> getCircuitBreakerStates() {
        Map<String, String> states = new HashMap<>();
        circuitBreakers.forEach((name, cb) -> states.put(name, cb.getState().name()));
        return Collections.unmodifiableMap(states);
    }

    // ── Resilience4j builders ─────────────────────────────────────────────

    private RateLimiter buildRateLimiter(String name,
                                         ThrottleConfig.RateLimiterSettings cfg) {
        RateLimiterConfig rlCfg = RateLimiterConfig.custom()
                .limitForPeriod(cfg.getLimitForPeriod())
                .limitRefreshPeriod(Duration.ofMillis(cfg.getLimitRefreshPeriodMs()))
                .timeoutDuration(Duration.ofMillis(cfg.getTimeoutDurationMs()))
                .build();
        // Append timestamp so rebuilt instances have unique names in the Resilience4j registry
        return RateLimiter.of(name + "@" + Instant.now().getEpochSecond(), rlCfg);
    }

    private CircuitBreaker buildCircuitBreaker(String name,
                                               ThrottleConfig.CircuitBreakerSettings cfg) {
        CircuitBreakerConfig cbCfg = CircuitBreakerConfig.custom()
                .failureRateThreshold(cfg.getFailureRateThreshold())
                .slowCallRateThreshold(cfg.getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(cfg.getSlowCallDurationThresholdMs()))
                .waitDurationInOpenState(Duration.ofMillis(cfg.getWaitDurationInOpenMs()))
                .permittedNumberOfCallsInHalfOpenState(cfg.getPermittedCallsInHalfOpen())
                .slidingWindowSize(cfg.getSlidingWindowSize())
                .build();
        return CircuitBreaker.of(name + "@" + Instant.now().getEpochSecond(), cbCfg);
    }
}
