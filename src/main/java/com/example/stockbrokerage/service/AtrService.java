package com.example.stockbrokerage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes Average True Range (ATR-14) for a stock symbol.
 *
 * <p>ATR is a volatility indicator that averages the True Range over N periods.
 * True Range = max(High−Low, |High−PrevClose|, |Low−PrevClose|).
 *
 * <p>Since the data layer only stores 5-minute closing prices, daily High/Low are
 * approximated as the max/min of the intraday 5-minute closes.  This is slightly
 * conservative (real intraday wicks can exceed any bar's close) but is a very
 * good approximation in practice and requires no additional API calls.
 *
 * <p>Smoothing uses Wilder's RMA:
 *   ATR₁ = SMA of first 14 TRs
 *   ATRₙ = (ATRₙ₋₁ × 13 + TRₙ) / 14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtrService {

    private final StockMarketDataService marketDataService;

    /** Number of 5-min bars per full NYSE trading day (9:30–16:00 = 78 bars). */
    private static final int BARS_PER_DAY = 78;

    /** ATR look-back period (standard: 14 trading days). */
    private static final int ATR_PERIOD = 14;

    /**
     * Number of 5-min bars to fetch.
     * We need ATR_PERIOD + 1 days of data (the first day only supplies a previous-close).
     * A buffer of +5 days handles weekends / data gaps.
     */
    private static final int HISTORY_BARS = (ATR_PERIOD + 6) * BARS_PER_DAY;

    // ── Result type ────────────────────────────────────────────────────────────

    /**
     * Holds the three ATR metrics returned by {@link #computeAtrResult}.
     *
     * @param atr14  Wilder-smoothed ATR over the 14-period window.
     * @param atr75  75th-percentile of the last 14 daily True Ranges (p75 spike level).
     * @param atr90  90th-percentile of the last 14 daily True Ranges (p90 tail risk).
     */
    public record AtrResult(BigDecimal atr14, BigDecimal atr75, BigDecimal atr90) {}

    /**
     * Computes the 14-period ATR for {@code symbol}.
     *
     * @return ATR rounded to 2 decimal places, or {@code null} when there is
     *         insufficient historical data.
     */
    public BigDecimal computeAtr14(String symbol) {
        AtrResult result = computeAtrResult(symbol);
        return result == null ? null : result.atr14();
    }

    /**
     * Computes ATR(14), ATR-75(14) and ATR-90(14) for {@code symbol}.
     *
     * <p>The three metrics share the same 14 daily True Range values:
     * <ul>
     *   <li>{@link AtrResult#atr14()} — Wilder RMA of those 14 TRs (standard ATR).</li>
     *   <li>{@link AtrResult#atr75()} — 75th-percentile of the 14 TRs (typical spike).</li>
     *   <li>{@link AtrResult#atr90()} — 90th-percentile of the 14 TRs (tail-risk day).</li>
     * </ul>
     *
     * @return populated {@link AtrResult}, or {@code null} when there is insufficient data.
     */
    public AtrResult computeAtrResult(String symbol) {
        List<BigDecimal> prices = marketDataService.getPrices(symbol, HISTORY_BARS);

        if (prices.size() < BARS_PER_DAY * (ATR_PERIOD + 1)) {
            log.debug("ATR: insufficient data for {} ({} bars)", symbol, prices.size());
            return null;
        }

        // ── Step 1: aggregate 5-min bars → daily [high, low, close] ────────────
        List<double[]> daily = buildDailyHLC(prices);

        if (daily.size() < ATR_PERIOD + 1) {
            log.debug("ATR: not enough daily buckets for {} ({} days)", symbol, daily.size());
            return null;
        }

        // ── Step 2: compute True Range for each day (need previous close) ──────
        List<Double> trValues = new ArrayList<>();
        for (int i = 1; i < daily.size(); i++) {
            double high      = daily.get(i)[0];
            double low       = daily.get(i)[1];
            double prevClose = daily.get(i - 1)[2];
            double tr = Math.max(high - low,
                        Math.max(Math.abs(high - prevClose),
                                 Math.abs(low  - prevClose)));
            trValues.add(tr);
        }

        if (trValues.size() < ATR_PERIOD) {
            return null;
        }

        // ── Step 3: Wilder's RMA ────────────────────────────────────────────────
        // Seed with simple average of first ATR_PERIOD values
        double atr = trValues.subList(0, ATR_PERIOD)
                             .stream().mapToDouble(Double::doubleValue)
                             .average().orElse(0.0);

        // Smooth remaining values
        for (int i = ATR_PERIOD; i < trValues.size(); i++) {
            atr = (atr * (ATR_PERIOD - 1) + trValues.get(i)) / ATR_PERIOD;
        }

        // ── Step 4: percentiles over the last ATR_PERIOD TRs ────────────────────
        List<Double> last14 = new ArrayList<>(
                trValues.subList(Math.max(0, trValues.size() - ATR_PERIOD), trValues.size()));
        Collections.sort(last14);

        BigDecimal result14  = BigDecimal.valueOf(atr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal result75  = BigDecimal.valueOf(percentile(last14, 75.0)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal result90  = BigDecimal.valueOf(percentile(last14, 90.0)).setScale(2, RoundingMode.HALF_UP);

        log.debug("ATR for {}: atr14={} p75={} p90={}", symbol, result14, result75, result90);
        return new AtrResult(result14, result75, result90);
    }

    /**
     * Splits a flat list of 5-minute closing prices into daily buckets and
     * returns [high, low, close] for each day.
     */
    private List<double[]> buildDailyHLC(List<BigDecimal> prices) {
        List<double[]> days = new ArrayList<>();
        int i = 0;
        while (i < prices.size()) {
            int end = Math.min(i + BARS_PER_DAY, prices.size());
            List<BigDecimal> dayBars = prices.subList(i, end);

            double high  = dayBars.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
            double low   = dayBars.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
            double close = dayBars.get(dayBars.size() - 1).doubleValue();

            days.add(new double[]{high, low, close});
            i = end;
        }
        return days;
    }

    /**
     * Interpolated percentile using the nearest-rank method on a pre-sorted list.
     * {@code pct} is in the range [0, 100].
     */
    private static double percentile(List<Double> sorted, double pct) {
        if (sorted.isEmpty()) return 0.0;
        int n = sorted.size();
        double rank = (pct / 100.0) * (n - 1);   // 0-based continuous rank
        int lo = (int) rank;
        int hi = Math.min(lo + 1, n - 1);
        double frac = rank - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }
}
