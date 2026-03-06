package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.SuggestedTradeHistoryResponse;
import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.dto.TradeSuccessRateResponse;
import com.example.stockbrokerage.entity.SuggestedTradeRecord;
import com.example.stockbrokerage.entity.SuggestedTradeRecord.TradeOutcomeStatus;
import com.example.stockbrokerage.repository.SuggestedTradeRecordRepository;
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
import java.util.List;

/**
 * Persists generated trade suggestions and performs a daily outcome check.
 *
 * <p>Daily scheduler (runs at 06:00) inspects all PENDING records within the last 10 days:
 * <ul>
 *   <li>SUCCESS – the actual market price is at or below {@code suggestedBuyBackPrice}
 *       (the target was hit).</li>
 *   <li>FAILED  – the record is older than 7 days and the target has not been hit.</li>
 * </ul>
 *
 * <p>Records already marked SUCCESS are excluded from re-evaluation (they are treated as
 * "done"). FAILED records are likewise excluded from re-evaluation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestedTradeTrackingService {

    /** How many days back to look when returning the "recent history" for the UI. */
    private static final int HISTORY_DAYS = 5;

    /** How many days back the daily scheduler scans for PENDING records. */
    private static final int SCHEDULER_LOOKBACK_DAYS = 10;

    /** Maximum age (days) before an un-hit suggestion is marked FAILED. */
    private static final int EXPIRY_DAYS = 7;

    private final SuggestedTradeRecordRepository repository;
    private final StockPriceService stockPriceService;

    // ──────────────────────────────────────────────────────────────────────────
    // Persistence
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Saves suggestions that were just generated for {@code clientId}.
     * De-duplicates on (clientId, symbol, today) so re-running intraday does not
     * create duplicate rows.
     */
    @Transactional
    public void saveSuggestions(Long clientId, List<SuggestedTradeResponse> suggestions) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd   = LocalDate.now().atTime(LocalTime.MAX);

        for (SuggestedTradeResponse s : suggestions) {
            boolean exists = repository.existsByClientIdAndSymbolAndSuggestedDateBetween(
                    clientId, s.getSymbol(), dayStart, dayEnd);
            if (exists) {
                log.debug("Suggestion for client={} symbol={} already stored today – skipping", clientId, s.getSymbol());
                continue;
            }

            SuggestedTradeRecord record = SuggestedTradeRecord.builder()
                    .clientId(clientId)
                    .symbol(s.getSymbol())
                    .quantity(s.getQuantity())
                    .suggestedDate(LocalDateTime.now())
                    .action(s.getAction())
                    .currentPriceAtSuggestion(s.getCurrentPrice())
                    .atr14(s.getAtr14())
                    .avgPredictedPrice(s.getAvgPredictedPrice())
                    .expectedChangePct(s.getExpectedChangePct())
                    .suggestedSellPrice(s.getSuggestedSellPrice())
                    .suggestedBuyBackPrice(s.getSuggestedBuyBackPrice())
                    .confidence(s.getConfidence())
                    .reasoning(s.getReasoning())
                    .status(TradeOutcomeStatus.PENDING)
                    .build();

            repository.save(record);
            log.info("Saved suggested trade record: client={} symbol={} action={}", clientId, s.getSymbol(), s.getAction());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Query helpers (used by the controller)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns all suggestion records for the given client from the last {@value #HISTORY_DAYS} days,
     * newest first.
     */
    public List<SuggestedTradeHistoryResponse> getRecentHistory(Long clientId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(HISTORY_DAYS);
        return repository
                .findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(clientId, cutoff)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /**
     * Computes the success-rate statistics for all RESOLVED suggestions of the given client.
     * Only counts records that have been resolved (SUCCESS or FAILED); PENDING records are
     * counted separately.
     */
    public TradeSuccessRateResponse getSuccessRate(Long clientId) {
        // Fetch all records for this client ever stored (all states)
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365); // look back up to 1 year
        List<SuggestedTradeRecord> all = repository
                .findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(clientId, cutoff);

        int success = (int) all.stream().filter(r -> r.getStatus() == TradeOutcomeStatus.SUCCESS).count();
        int failed  = (int) all.stream().filter(r -> r.getStatus() == TradeOutcomeStatus.FAILED).count();
        int pending = (int) all.stream().filter(r -> r.getStatus() == TradeOutcomeStatus.PENDING).count();
        int resolved = success + failed;

        double ratePct = resolved == 0 ? 0.0
                : BigDecimal.valueOf((double) success / resolved * 100)
                            .setScale(1, RoundingMode.HALF_UP)
                            .doubleValue();

        return TradeSuccessRateResponse.builder()
                .totalResolved(resolved)
                .successCount(success)
                .failedCount(failed)
                .pendingCount(pending)
                .successRatePct(ratePct)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Daily scheduler
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Runs every day at 06:00 (server local time).
     * Checks all PENDING suggestions from the last {@value #SCHEDULER_LOOKBACK_DAYS} days.
     * <ul>
     *   <li>If the current price ≤ {@code suggestedBuyBackPrice} → mark SUCCESS.</li>
     *   <li>If the suggestion is older than {@value #EXPIRY_DAYS} days → mark FAILED.</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void checkPendingSuggestions() {
        LocalDateTime lookback = LocalDateTime.now().minusDays(SCHEDULER_LOOKBACK_DAYS);
        List<SuggestedTradeRecord> pending =
                repository.findByStatusAndSuggestedDateAfter(TradeOutcomeStatus.PENDING, lookback);

        log.info("Daily trade-suggestion check: {} PENDING records to evaluate", pending.size());

        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(EXPIRY_DAYS);

        for (SuggestedTradeRecord record : pending) {
            try {
                evaluateRecord(record, expiryThreshold);
            } catch (Exception e) {
                log.warn("Could not evaluate suggestion id={} symbol={}: {}", record.getId(), record.getSymbol(), e.getMessage());
            }
        }
    }

    private void evaluateRecord(SuggestedTradeRecord record, LocalDateTime expiryThreshold) {
        BigDecimal currentPrice = stockPriceService.getCurrentPrice(record.getSymbol());

        // For WATCH suggestions, the buy-back target is optional.
        // We still track if the price dropped to the target level.
        BigDecimal target = record.getSuggestedBuyBackPrice();

        if (currentPrice != null && target != null
                && currentPrice.compareTo(target) <= 0) {
            // Target price was hit
            record.setStatus(TradeOutcomeStatus.SUCCESS);
            record.setResolvedDate(LocalDateTime.now());
            repository.save(record);
            log.info("Suggestion SUCCESS: id={} symbol={} target={} currentPrice={}",
                    record.getId(), record.getSymbol(), target, currentPrice);
            return;
        }

        if (record.getSuggestedDate().isBefore(expiryThreshold)) {
            // Older than 7 days and target not hit
            record.setStatus(TradeOutcomeStatus.FAILED);
            record.setResolvedDate(LocalDateTime.now());
            repository.save(record);
            log.info("Suggestion FAILED (expired): id={} symbol={} suggestedDate={}",
                    record.getId(), record.getSymbol(), record.getSuggestedDate());
        }
        // else: still within 7 days and target not hit → remain PENDING
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapper
    // ──────────────────────────────────────────────────────────────────────────

    private SuggestedTradeHistoryResponse toHistoryResponse(SuggestedTradeRecord r) {
        return SuggestedTradeHistoryResponse.builder()
                .id(r.getId())
                .clientId(r.getClientId())
                .symbol(r.getSymbol())
                .quantity(r.getQuantity())
                .suggestedDate(r.getSuggestedDate())
                .action(r.getAction())
                .currentPriceAtSuggestion(r.getCurrentPriceAtSuggestion())
                .atr14(r.getAtr14())
                .avgPredictedPrice(r.getAvgPredictedPrice())
                .expectedChangePct(r.getExpectedChangePct())
                .suggestedSellPrice(r.getSuggestedSellPrice())
                .suggestedBuyBackPrice(r.getSuggestedBuyBackPrice())
                .confidence(r.getConfidence())
                .reasoning(r.getReasoning())
                .status(r.getStatus())
                .resolvedDate(r.getResolvedDate())
                .build();
    }
}
