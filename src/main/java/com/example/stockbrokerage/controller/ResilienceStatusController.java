package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.config.DynamicThrottleRegistry;
import com.example.stockbrokerage.config.ThrottleConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoint for inspecting and force-reloading the throttle/circuit-breaker config.
 * Secured to ADMIN role in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/resilience")
@RequiredArgsConstructor
@Tag(name = "Resilience Admin", description = "Throttle and circuit-breaker configuration status")
public class ResilienceStatusController {

    private final DynamicThrottleRegistry registry;

    @GetMapping("/status")
    @Operation(summary = "Current throttle config + circuit-breaker states")
    public ResponseEntity<Map<String, Object>> status() {
        ThrottleConfig cfg = registry.getCurrentConfig();
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("lastLoadedAt",   registry.getLastLoadedAt());
        body.put("lastLoadedFrom", registry.getLastLoadedFrom());
        body.put("nextReloadIn",   "≤60 seconds (fixed-delay scheduler)");

        // Global defaults
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("rateLimiter",    cfg.getDefaults().getRateLimiter());
        defaults.put("circuitBreaker", cfg.getDefaults().getCircuitBreaker());
        body.put("globalDefaults", defaults);

        // Per-service overrides
        body.put("serviceOverrides", cfg.getServices());

        // Live circuit-breaker states
        body.put("circuitBreakerStates", registry.getCircuitBreakerStates());

        return ResponseEntity.ok(body);
    }

    @PostMapping("/reload")
    @Operation(summary = "Force-reload throttle-config.yaml immediately (without waiting 60 s)")
    public ResponseEntity<Map<String, Object>> forceReload() {
        Instant before = registry.getLastLoadedAt();
        registry.reloadConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("previousLoadAt", before);
        body.put("newLoadAt",      registry.getLastLoadedAt());
        body.put("loadedFrom",     registry.getLastLoadedFrom());
        body.put("serviceOverrides", registry.getCurrentConfig().getServices().size());
        return ResponseEntity.ok(body);
    }
}
