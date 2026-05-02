package com.example.stockbrokerage.service;

import com.example.stockbrokerage.repository.StockPriceCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Periodic housekeeping for high-churn data/log artifacts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataMaintenanceService {

    private final StockPriceCacheRepository stockPriceCacheRepository;

    @Value("${app.maintenance.stock-price-cache-retention.enabled:true}")
    private boolean cacheRetentionEnabled;

    @Value("${app.maintenance.stock-price-cache-retention.days:7}")
    private int cacheRetentionDays;

    @Value("${app.maintenance.tmp-log-cleanup.enabled:true}")
    private boolean tmpLogCleanupEnabled;

    @Value("${app.maintenance.tmp-log-cleanup.older-than-days:2}")
    private int tmpLogOlderThanDays;

    @Value("${app.maintenance.tmp-log-cleanup.log-dir:logs}")
    private String logDir;

    @PostConstruct
    public void startupCleanup() {
        cleanupTmpLogs("startup");
    }

    /**
     * Runs daily after the nightly price sync and removes old 5-minute bars.
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/New_York")
    @Transactional
    public void cleanupOldStockPriceCacheRows() {
        if (!cacheRetentionEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(cacheRetentionDays, 1));
        int deleted = stockPriceCacheRepository.deleteByBarTimeBefore(cutoff);
        if (deleted > 0) {
            log.info("Stock price cache retention removed {} rows older than {} ({} days)",
                    deleted, cutoff, cacheRetentionDays);
        } else {
            log.debug("Stock price cache retention found no rows older than {}", cutoff);
        }
    }

    /**
     * Runs daily to delete stale logback rollover temp files.
     */
    @Scheduled(cron = "0 45 3 * * *", zone = "America/New_York")
    public void cleanupTmpLogsScheduled() {
        cleanupTmpLogs("scheduled");
    }

    private void cleanupTmpLogs(String trigger) {
        if (!tmpLogCleanupEnabled) {
            return;
        }

        Path dir = Paths.get(logDir);
        if (!Files.isDirectory(dir)) {
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(Math.max(tmpLogOlderThanDays, 1) * 86_400L);
        int deleted = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tmp")) {
            for (Path path : stream) {
                try {
                    FileTime lastModified = Files.getLastModifiedTime(path);
                    if (lastModified.toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                        deleted++;
                    }
                } catch (IOException ex) {
                    log.warn("Could not delete tmp log file {}: {}", path, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.warn("Tmp log cleanup failed for {}: {}", dir, ex.getMessage());
            return;
        }

        if (deleted > 0) {
            log.info("Tmp log cleanup ({}) removed {} stale files from {}", trigger, deleted, dir);
        }
    }
}
