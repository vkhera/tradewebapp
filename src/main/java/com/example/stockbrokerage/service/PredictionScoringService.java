package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.PredictionDailyScore;
import com.example.stockbrokerage.entity.StockPriceCache;
import com.example.stockbrokerage.entity.StockPricePrediction;
import com.example.stockbrokerage.entity.SwingTradePrediction;
import com.example.stockbrokerage.entity.TrendPredictionResult;
import com.example.stockbrokerage.repository.PredictionDailyScoreRepository;
import com.example.stockbrokerage.repository.StockPriceCacheRepository;
import com.example.stockbrokerage.repository.StockPricePredictionRepository;
import com.example.stockbrokerage.repository.SwingTradePredictionRepository;
import com.example.stockbrokerage.repository.TrendPredictionResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * End-of-day job that aggregates prediction accuracy across three types —
 * hourly price, swing trade, and trend — plus a combined total.
 * Fires at 18:00 on weekdays after the market has settled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionScoringService {

    public static final String JOB_NAME = "PREDICTION_SCORING";
    private static final double HOURLY_SUCCESS_THRESHOLD_PCT = 3.0;
    private static final double TREND_NEUTRAL_THRESHOLD_PCT  = 0.5;

    private final StockPricePredictionRepository predictionRepository;
    private final SwingTradePredictionRepository  swingRepository;
    private final TrendPredictionResultRepository trendRepository;
    private final StockPriceCacheRepository       priceCacheRepository;
    private final PredictionDailyScoreRepository  scoreRepository;
    private final JobTrackerService               jobTracker;

    // ── Scheduled entry point ─────────────────────────────────────────────────

    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void runDailyScoringJob() {
        runTracked(LocalDate.now());
    }

    @Transactional
    public void runTracked(LocalDate date) {
        JobExecutionRecord job = jobTracker.startJob(JOB_NAME, LocalDateTime.now());
        try {
            scoreDay(date);
            jobTracker.completeJob(job);
        } catch (Exception e) {
            jobTracker.failJob(job, e.getMessage());
            throw e;
        }
    }

    // ── Core scoring ──────────────────────────────────────────────────────────

    @Transactional
    public void scoreDay(LocalDate scoreDate) {
        LocalDateTime from = scoreDate.atStartOfDay();
        LocalDateTime to   = scoreDate.plusDays(1).atStartOfDay();

        TypeScore hourlyScore = scoreHourlyPredictions(from, to);
        TypeScore swingScore  = scoreSwingPredictions(from, to);
        TypeScore trendScore  = scoreTrendPredictions(scoreDate);

        saveScore(scoreDate, PredictionDailyScore.PredictionType.HOURLY_PRICE, hourlyScore);
        saveScore(scoreDate, PredictionDailyScore.PredictionType.SWING_TRADE,  swingScore);
        saveScore(scoreDate, PredictionDailyScore.PredictionType.TREND,        trendScore);
        saveScore(scoreDate, PredictionDailyScore.PredictionType.COMBINED,
                  TypeScore.combine(hourlyScore, swingScore, trendScore));

        log.info("Prediction scoring {}: hourly={} swing={} trend={}",
                scoreDate, hourlyScore, swingScore, trendScore);
    }

    // ── Hourly price predictions ──────────────────────────────────────────────

    private TypeScore scoreHourlyPredictions(LocalDateTime from, LocalDateTime to) {
        List<StockPricePrediction> resolved =
                predictionRepository.findResolvedPredictionsBetween(from, to);
        if (resolved.isEmpty()) return TypeScore.empty();

        int total   = resolved.size();
        int success = 0;
        BigDecimal sumAbs = BigDecimal.ZERO;
        BigDecimal sumPct = BigDecimal.ZERO;

        for (StockPricePrediction p : resolved) {
            BigDecimal pct = p.getPercentageError();
            BigDecimal abs = p.getAbsoluteError();
            if (pct != null && pct.abs().compareTo(BigDecimal.valueOf(HOURLY_SUCCESS_THRESHOLD_PCT)) <= 0) success++;
            sumAbs = sumAbs.add(abs != null ? abs.abs() : BigDecimal.ZERO);
            sumPct = sumPct.add(pct != null ? pct.abs() : BigDecimal.ZERO);
        }

        BigDecimal bd = BigDecimal.valueOf(total);
        return new TypeScore(total, success,
                sumAbs.divide(bd, 4, RoundingMode.HALF_UP),
                sumPct.divide(bd, 4, RoundingMode.HALF_UP));
    }

    // ── Swing trade predictions ───────────────────────────────────────────────

    private TypeScore scoreSwingPredictions(LocalDateTime from, LocalDateTime to) {
        List<SwingTradePrediction> resolved = swingRepository.findResolvedBetween(
                List.of(SwingTradePrediction.SwingOutcomeStatus.SUCCESS,
                        SwingTradePrediction.SwingOutcomeStatus.FAILED),
                from, to);
        if (resolved.isEmpty()) return TypeScore.empty();

        int total   = resolved.size();
        int success = (int) resolved.stream()
                .filter(s -> s.getStatus() == SwingTradePrediction.SwingOutcomeStatus.SUCCESS)
                .count();

        // Use |actual - predicted return| as a percentage-error analogue
        BigDecimal sumErr = resolved.stream()
                .filter(s -> s.getActualReturnPct() != null && s.getPredictedReturnPct() != null)
                .map(s -> s.getActualReturnPct().subtract(s.getPredictedReturnPct()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long withReturn = resolved.stream()
                .filter(s -> s.getActualReturnPct() != null && s.getPredictedReturnPct() != null)
                .count();
        BigDecimal avgErr = withReturn > 0
                ? sumErr.divide(BigDecimal.valueOf(withReturn), 4, RoundingMode.HALF_UP)
                : null;

        return new TypeScore(total, success, null, avgErr);
    }

    // ── Trend predictions ─────────────────────────────────────────────────────

    private TypeScore scoreTrendPredictions(LocalDate scoreDate) {
        List<TrendPredictionResult> trends = trendRepository.findByPredictionDate(scoreDate);
        if (trends.isEmpty()) return TypeScore.empty();

        int total   = 0;
        int success = 0;

        LocalDateTime dayStart  = scoreDate.atStartOfDay();
        LocalDateTime dayEnd    = scoreDate.plusDays(1).atStartOfDay();
        LocalDateTime prevStart = scoreDate.minusDays(7).atStartOfDay();

        for (TrendPredictionResult t : trends) {
            Optional<StockPriceCache> todayBar =
                    priceCacheRepository.findLastBarOfDay(t.getSymbol(), dayStart, dayEnd);
            Optional<StockPriceCache> prevBar  =
                    priceCacheRepository.findLastBarBefore(t.getSymbol(), prevStart, dayStart);

            if (todayBar.isEmpty() || prevBar.isEmpty()) continue;

            BigDecimal today = todayBar.get().getClosePrice();
            BigDecimal prev  = prevBar.get().getClosePrice();
            if (prev.compareTo(BigDecimal.ZERO) == 0) continue;

            double changePct = today.subtract(prev)
                    .divide(prev, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            total++;
            boolean hit = switch (t.getOverallTrend()) {
                case "BULLISH", "UPTREND"           -> changePct > 0;
                case "BEARISH", "DOWNTREND"         -> changePct < 0;
                case "NEUTRAL", "SIDEWAYS"          -> Math.abs(changePct) < TREND_NEUTRAL_THRESHOLD_PCT;
                default                             -> false;
            };
            if (hit) success++;
        }

        return total == 0 ? TypeScore.empty() : new TypeScore(total, success, null, null);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void saveScore(LocalDate scoreDate, PredictionDailyScore.PredictionType type, TypeScore s) {
        if (s.total == 0) {
            log.debug("No data for {} type={}; skipping", scoreDate, type);
            return;
        }
        double rate = BigDecimal.valueOf((double) s.success / s.total * 100.0)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();

        PredictionDailyScore score = scoreRepository
                .findByScoreDateAndPredictionType(scoreDate, type)
                .orElseGet(() -> PredictionDailyScore.builder()
                        .scoreDate(scoreDate)
                        .predictionType(type)
                        .build());

        score.setTotalResolved(s.total);
        score.setSuccessCount(s.success);
        score.setFailureCount(s.total - s.success);
        score.setSuccessRatePct(rate);
        score.setAvgAbsoluteError(s.avgAbsErr);
        score.setAvgPercentageError(s.avgPctErr);
        scoreRepository.save(score);
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private record TypeScore(int total, int success, BigDecimal avgAbsErr, BigDecimal avgPctErr) {

        static TypeScore empty() { return new TypeScore(0, 0, null, null); }

        static TypeScore combine(TypeScore... parts) {
            int t = 0, s = 0;
            for (TypeScore p : parts) { t += p.total; s += p.success; }
            return new TypeScore(t, s, null, null);
        }

        @Override public String toString() {
            return "total=" + total + " success=" + success;
        }
    }
}

