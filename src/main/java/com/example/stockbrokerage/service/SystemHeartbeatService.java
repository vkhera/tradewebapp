package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.SystemHeartbeat;
import com.example.stockbrokerage.repository.SystemHeartbeatRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes a heartbeat to the {@code system_heartbeat} table every minute.
 *
 * <p>The heartbeat row (always {@code id = 1}) is used to distinguish between:
 * <ul>
 *   <li><b>System downtime</b> – the heartbeat timestamp is stale, meaning the JVM
 *       was not running during the gap.</li>
 *   <li><b>Job failure</b> – the heartbeat is current but a job's execution record
 *       shows FAILED or is absent, meaning the JVM was alive but the job crashed.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHeartbeatService {

    private final SystemHeartbeatRepository repository;

    private LocalDateTime startupTime;

    @PostConstruct
    public void recordStartup() {
        startupTime = LocalDateTime.now();
        // Immediately write the startup heartbeat so the row exists in DB.
        pulse();
        log.info("System heartbeat initialised. Startup time: {}", startupTime);
    }

    /** Upserts the single heartbeat row every minute. */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void pulse() {
        repository.save(new SystemHeartbeat(1L, LocalDateTime.now(), startupTime));
        log.debug("Heartbeat written at {}", LocalDateTime.now());
    }
}
