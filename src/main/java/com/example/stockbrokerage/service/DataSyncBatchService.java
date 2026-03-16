package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.repository.StockPriceCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Nightly batch job (2 AM EST) that syncs the on-disk CSV files from
 * {@code stock_predictions/} into the {@code stock_price_cache} PostgreSQL table.
 *
 * Trend predictions and prediction weights are written directly to the DB by
 * {@link TrendAnalysisService} and {@link StockPricePredictionService} as each
 * batch run produces new data, so no secondary sync is needed for those tables.
 *
 * Price history CSVs can contain thousands of 5-minute bars per symbol, so syncing
 * is done nightly in bulk using native {@code INSERT … ON CONFLICT DO NOTHING} to
 * avoid writing duplicate rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncBatchService {

    private static final String STOCK_PREDICTIONS_DIR = "stock_predictions";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StockPriceCacheRepository stockPriceCacheRepository;
    private final JobTrackerService jobTracker;

    public static final String JOB_NAME = "DATA_SYNC";

    /**
     * Runs every night at 2:00 AM Eastern time.
     * Reads every {@code *_prices.csv} file and upserts the bars to PostgreSQL.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "America/New_York")
    public void syncPriceDataToDatabase() {
        JobExecutionRecord job = jobTracker.startJob(JOB_NAME, LocalDateTime.now());
        try {
            runSync();
            jobTracker.completeJob(job);
        } catch (Exception e) {
            jobTracker.failJob(job, e.getMessage());
            throw e;
        }
    }

    /** Core sync logic, extracted so it can be called by catch-up on startup. */
    public void runSync() {
        log.info("=== Starting nightly price-data sync to PostgreSQL ===");

        Path dir = Paths.get(STOCK_PREDICTIONS_DIR);
        if (!Files.exists(dir)) {
            log.warn("stock_predictions directory not found, skipping sync");
            return;
        }

        int totalFiles = 0;
        int totalBars  = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*_prices.csv")) {
            for (Path csvFile : stream) {
                String fileName = csvFile.getFileName().toString();
                String symbol   = fileName.replace("_prices.csv", "");
                int bars = syncPriceFile(symbol, csvFile);
                log.info("Synced {} bars for {} from {}", bars, symbol, fileName);
                totalBars += bars;
                totalFiles++;
            }
        } catch (IOException e) {
            log.error("Error reading stock_predictions directory", e);
        }

        log.info("=== Nightly price-data sync complete: {} files, {} bars upserted ===",
                 totalFiles, totalBars);
    }

    /**
     * Reads a single {@code {symbol}_prices.csv} and batch-upserts its rows.
     * Returns the number of rows processed.
     */
    @Transactional
    public int syncPriceFile(String symbol, Path csvFile) {
        List<BarRow> rows = parsePriceCsv(csvFile);
        if (rows.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        for (BarRow row : rows) {
            try {
                stockPriceCacheRepository.upsertBar(symbol, row.barTime, row.closePrice, now);
            } catch (Exception e) {
                log.warn("Skipping bar {}/{} due to error: {}", symbol, row.barTime, e.getMessage());
            }
        }
        return rows.size();
    }

    // -------------------------------------------------------------------------

    private List<BarRow> parsePriceCsv(Path file) {
        List<BarRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                try {
                    LocalDateTime barTime  = LocalDateTime.parse(parts[0].trim(), TS_FMT);
                    BigDecimal closePrice  = new BigDecimal(parts[1].trim());
                    rows.add(new BarRow(barTime, closePrice));
                } catch (Exception e) {
                    log.debug("Skipping malformed price row in {}: {}", file.getFileName(), line);
                }
            }
        } catch (IOException e) {
            log.error("Error reading price CSV {}: {}", file, e.getMessage());
        }
        return rows;
    }

    private record BarRow(LocalDateTime barTime, BigDecimal closePrice) {}
}
