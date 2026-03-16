package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.SwingTradeSuggestionResponse;
import com.example.stockbrokerage.dto.SwingTradeSuccessRateResponse;
import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.SwingStrategyWeight;
import com.example.stockbrokerage.entity.SwingTradePrediction;
import com.example.stockbrokerage.entity.SwingTradePrediction.SwingOutcomeStatus;
import com.example.stockbrokerage.repository.SwingStrategyWeightRepository;
import com.example.stockbrokerage.repository.SwingTradePredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists generated swing-trade suggestions and performs daily outcome evaluation.
 *
 * <h3>Daily scheduler (06:30 ET)</h3>
 * <ol>
 *   <li>Finds all PENDING swing-trade records within the last 14 days.</li>
 *   <li>Checks current market price against targetPrice / stopLoss.</li>
 *   <li>Marks SUCCESS if price reached the target; FAILED if hold period expired or stop-loss hit.</li>
 *   <li>Updates strategy weights based on newly resolved records.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwingTradeTrackingService {

    private static final int    HISTORY_DAYS          = 5;
    private static final int    SCHEDULER_LOOKBACK    = 14;
    private static final ZoneId EST = ZoneId.of("America/New_York");

    // Weight update multipliers (mirroring the hourly prediction weight system)
    private static final double WIN_MULTIPLIER   = 1.15;
    private static final double LOSS_MULTIPLIER  = 0.85;
    private static final double WEIGHT_FLOOR     = 0.05;
    private static final double WEIGHT_CAP       = 0.60;
    private static final int    STRATEGY_COUNT   = 5;

    private final SwingTradePredictionRepository swingRepository;
    private final SwingStrategyWeightRepository  weightRepository;
    private final StockPriceService              stockPriceService;
    private final JobTrackerService              jobTracker;

    // ──────────────────────────────────────────────────────────────────────────
    // Persistence
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Persists newly generated swing suggestions for {@code clientId}.
     * De-duplicates on (clientId, symbol, today) to avoid duplicate rows on intraday re-runs.
     */
    @Transactional
    public void saveSuggestions(Long clientId, List<SwingTradeSuggestionResponse> suggestions) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd   = LocalDate.now().atTime(LocalTime.MAX);

        for (SwingTradeSuggestionResponse s : suggestions) {
            boolean exists = swingRepository.existsByClientIdAndSymbolAndSuggestedDateBetween(
                    clientId, s.getSymbol(), dayStart, dayEnd);
            if (exists) {
                log.debug("Swing suggestion for client={} symbol={} already stored today – skipping", clientId, s.getSymbol());
                continue;
            }

            SwingTradePrediction record = SwingTradePrediction.builder()
                    .clientId(clientId)
                    .symbol(s.getSymbol())
                    .quantity(s.getQuantity())
                    .action(s.getAction())
                    .entryPrice(s.getCurrentPrice())
                    .targetPrice(s.getTargetPrice())
                    .stopLoss(s.getStopLoss())
                    .predictedReturnPct(s.getPredictedReturnPct())
                    .holdDaysEstimated(s.getHoldDaysEstimated())
                    .confidence(s.getConfidence())
                    .topStrategies(s.getTopStrategies())
                    .reasoning(s.getReasoning())
                    .strategySignals(s.getTopStrategies()) // stored as-is for weight lookup
                    .suggestedDate(s.getSuggestedDate() != null
                            ? s.getSuggestedDate()
                            : ZonedDateTime.now(EST).toLocalDateTime())
                    .status(SwingOutcomeStatus.PENDING)
                    .build();

            swingRepository.save(record);
            log.info("Saved swing trade: client={} symbol={} action={} return={}%",
                    clientId, s.getSymbol(), s.getAction(), s.getPredictedReturnPct());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Query helpers (used by the controller)
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the last {@value #HISTORY_DAYS} days of swing suggestions for the client, newest first. */
    public List<SwingTradeSuggestionResponse> getRecentHistory(Long clientId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(HISTORY_DAYS);
        return swingRepository
                .findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(clientId, cutoff)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Computes success-rate statistics for all resolved swing predictions of the given client. */
    public SwingTradeSuccessRateResponse getSuccessRate(Long clientId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
        List<SwingTradePrediction> all = swingRepository
                .findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(clientId, cutoff);

        int success = (int) all.stream().filter(r -> r.getStatus() == SwingOutcomeStatus.SUCCESS).count();
        int failed  = (int) all.stream().filter(r -> r.getStatus() == SwingOutcomeStatus.FAILED).count();
        int pending = (int) all.stream().filter(r -> r.getStatus() == SwingOutcomeStatus.PENDING).count();
        int resolved = success + failed;

        double ratePct = resolved == 0 ? 0.0
                : BigDecimal.valueOf((double) success / resolved * 100)
                            .setScale(1, RoundingMode.HALF_UP)
                            .doubleValue();

        return SwingTradeSuccessRateResponse.builder()
                .totalResolved(resolved)
                .successCount(success)
                .failedCount(failed)
                .pendingCount(pending)
                .successRatePct(ratePct)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Daily scheduler (06:30 ET)
    // ──────────────────────────────────────────────────────────────────────────

    public static final String JOB_NAME = "SWING_TRADE_CHECK";

    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void evaluatePendingSwingTrades() {
        JobExecutionRecord job = jobTracker.startJob(JOB_NAME, LocalDateTime.now());
        try {
            runPendingEvaluation();
            jobTracker.completeJob(job);
        } catch (Exception e) {
            jobTracker.failJob(job, e.getMessage());
            throw e;
        }
    }

    /** Core evaluation logic, extracted so it can be called by catch-up on startup. */
    public void runPendingEvaluation() {
        LocalDateTime lookback = LocalDateTime.now().minusDays(SCHEDULER_LOOKBACK);
        List<SwingTradePrediction> pending =
                swingRepository.findByStatusAndSuggestedDateAfter(SwingOutcomeStatus.PENDING, lookback);

        log.info("Daily swing-trade check: {} PENDING records", pending.size());

        List<SwingTradePrediction> newlyResolved = new ArrayList<>();

        for (SwingTradePrediction record : pending) {
            try {
                if (evaluateRecord(record)) {
                    newlyResolved.add(record);
                }
            } catch (Exception e) {
                log.warn("Could not evaluate swing trade id={} symbol={}: {}", record.getId(), record.getSymbol(), e.getMessage());
            }
        }

        if (!newlyResolved.isEmpty()) {
            updateStrategyWeights(newlyResolved);
        }
    }

    /**
     * Evaluates a single PENDING record against the current market price.
     *
     * @return true if the record was resolved (SUCCESS or FAILED).
     */
    private boolean evaluateRecord(SwingTradePrediction record) {
        BigDecimal currentPrice = stockPriceService.getCurrentPrice(record.getSymbol());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) return false;

        LocalDateTime now = ZonedDateTime.now(EST).toLocalDateTime();
        LocalDateTime expiryDate = record.getSuggestedDate().plusDays(record.getHoldDaysEstimated());
        boolean expired = now.isAfter(expiryDate);

        boolean targetHit;
        if ("HOLD".equals(record.getAction())) {
            // SUCCESS: price rose to or above target
            targetHit = record.getTargetPrice() != null
                    && currentPrice.compareTo(record.getTargetPrice()) >= 0;
        } else {
            // SELL: SUCCESS: price fell to or below re-entry target
            targetHit = record.getTargetPrice() != null
                    && currentPrice.compareTo(record.getTargetPrice()) <= 0;
        }

        if (targetHit) {
            double actualReturn = record.getEntryPrice().compareTo(BigDecimal.ZERO) > 0
                    ? currentPrice.subtract(record.getEntryPrice())
                                  .divide(record.getEntryPrice(), 6, RoundingMode.HALF_UP)
                                  .multiply(BigDecimal.valueOf(100))
                                  .doubleValue()
                    : 0.0;
            record.setStatus(SwingOutcomeStatus.SUCCESS);
            record.setResolvedDate(now);
            record.setActualExitPrice(currentPrice);
            record.setActualReturnPct(BigDecimal.valueOf(actualReturn).setScale(2, RoundingMode.HALF_UP));
            swingRepository.save(record);
            log.info("Swing SUCCESS: id={} {} {} target={} current={}", record.getId(), record.getSymbol(), record.getAction(), record.getTargetPrice(), currentPrice);
            return true;
        }

        if (expired) {
            record.setStatus(SwingOutcomeStatus.FAILED);
            record.setResolvedDate(now);
            record.setActualExitPrice(currentPrice);
            swingRepository.save(record);
            log.info("Swing FAILED (expired): id={} {} holdDays={}", record.getId(), record.getSymbol(), record.getHoldDaysEstimated());
            return true;
        }

        return false;
    }

    /**
     * Updates strategy weights based on newly resolved swing trade records.
     * WIN: strategy weight × 1.15 (cap 0.60); LOSS: × 0.85 (floor 0.05).
     * Weights are normalised to sum to 1.0 after updates.
     */
    private void updateStrategyWeights(List<SwingTradePrediction> resolved) {
        List<SwingStrategyWeight> allWeights = weightRepository.findAll();
        if (allWeights.isEmpty()) return;

        LocalDateTime now = ZonedDateTime.now(EST).toLocalDateTime();

        for (SwingTradePrediction record : resolved) {
            if (record.getTopStrategies() == null || record.getTopStrategies().isBlank()) continue;

            String[] strategiesInRecord = record.getTopStrategies().split("[,\\s]+");
            boolean success = record.getStatus() == SwingOutcomeStatus.SUCCESS;

            for (String stratName : strategiesInRecord) {
                String trimmed = stratName.trim();
                allWeights.stream()
                        .filter(w -> w.getStrategyName().equalsIgnoreCase(trimmed))
                        .findFirst()
                        .ifPresent(w -> {
                            if (success) {
                                w.setWeight(Math.min(WEIGHT_CAP, w.getWeight() * WIN_MULTIPLIER));
                                w.setWinCount(w.getWinCount() + 1);
                            } else {
                                w.setWeight(Math.max(WEIGHT_FLOOR, w.getWeight() * LOSS_MULTIPLIER));
                                w.setLossCount(w.getLossCount() + 1);
                            }
                            w.setLastUpdated(now);
                        });
            }
        }

        // Normalise so all weights sum to 1.0
        double total = allWeights.stream().mapToDouble(SwingStrategyWeight::getWeight).sum();
        if (total > 0) {
            for (SwingStrategyWeight w : allWeights) {
                double normalised = w.getWeight() / total;
                // Re-apply floor/cap after normalisation
                normalised = Math.max(WEIGHT_FLOOR / STRATEGY_COUNT, Math.min(WEIGHT_CAP, normalised));
                w.setWeight(normalised);
            }
        }

        weightRepository.saveAll(allWeights);
        log.info("Updated swing strategy weights after {} resolved records", resolved.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapper
    // ──────────────────────────────────────────────────────────────────────────

    private SwingTradeSuggestionResponse toResponse(SwingTradePrediction r) {
        return SwingTradeSuggestionResponse.builder()
                .id(r.getId())
                .symbol(r.getSymbol())
                .quantity(r.getQuantity())
                .currentPrice(r.getEntryPrice())
                .action(r.getAction())
                .targetPrice(r.getTargetPrice())
                .stopLoss(r.getStopLoss())
                .predictedReturnPct(r.getPredictedReturnPct())
                .holdDaysEstimated(r.getHoldDaysEstimated())
                .confidence(r.getConfidence())
                .topStrategies(r.getTopStrategies())
                .reasoning(r.getReasoning())
                .suggestedDate(r.getSuggestedDate())
                .status(r.getStatus().name())
                .resolvedDate(r.getResolvedDate())
                .actualExitPrice(r.getActualExitPrice())
                .actualReturnPct(r.getActualReturnPct())
                .build();
    }
}
