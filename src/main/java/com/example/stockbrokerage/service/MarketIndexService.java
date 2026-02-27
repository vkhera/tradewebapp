package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.MarketIndexInfluence;
import com.example.stockbrokerage.entity.MarketIndexWeight;
import com.example.stockbrokerage.repository.MarketIndexWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the influence of macro-market indices on individual stock price
 * predictions and trend direction.
 *
 * <h3>Indices tracked</h3>
 * <ul>
 *   <li>IWM  – iShares Russell 2000 (small-cap breadth)</li>
 *   <li>QQQ  – Invesco NASDAQ-100 (tech/growth leadership)</li>
 *   <li>VOO  – Vanguard S&amp;P 500 (broad market)</li>
 *   <li>DIA  – SPDR Dow Jones Industrial (blue-chip / defensive)</li>
 *   <li>VXVY – CBOE Volatility-of-Volatility proxy (risk sentiment)</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Fetch 240 five-minute bars (~20 hours) of price history for the
 *       stock and each index via {@link StockMarketDataService}.</li>
 *   <li>Compute Pearson correlation between the stock's and each index's
 *       log-returns over those bars.</li>
 *   <li>Weights are initialised as |correlation| / Σ|correlations| and
 *       are stored per (stock, index) in the {@code market_index_weight}
 *       PostgreSQL table.</li>
 *   <li>After each hourly batch the weights are updated: if an index's
 *       directional signal (correlation × index-return) matched the
 *       actual stock move, that index's weight is increased; otherwise
 *       it is decreased.  Weights are then renormalised to sum to 1.</li>
 *   <li>The <em>index adjustment factor</em> fed into the price
 *       prediction is a dampened, weighted sum:
 *       {@code factor = DAMPENING × Σ(weight_i × correlation_i × indexReturn_i)}.</li>
 *   <li>The factor is also converted to a trend signal for
 *       {@link TrendAnalysisService}: positive → UPTREND,
 *       negative → DOWNTREND, near-zero → SIDEWAYS.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketIndexService {

    // ── Constants ────────────────────────────────────────────────────────────

    /** Indices whose influence is tracked against every portfolio stock. */
    public static final List<String> INDEX_SYMBOLS =
        List.of("IWM", "QQQ", "VOO", "DIA", "VXVY");

    /** Number of 5-min bars used for the rolling correlation (~20 hours of trading). */
    private static final int CORRELATION_BARS = 240;

    /**
     * Dampening coefficient applied to the raw index adjustment signal.
     * At 0.30 the maximum total index influence on a predicted price is ≈ 10%
     * (signal ≈ ±33% before dampening, dampened ≈ ±10%).
     */
    private static final double DAMPENING = 0.30;

    /** Weight adjustment multipliers (mirrors StockPricePredictionService conventions). */
    private static final double INCREASE_FACTOR = 1.12;
    private static final double DECREASE_FACTOR = 0.88;
    private static final double MIN_WEIGHT       = 0.05;
    private static final double MAX_WEIGHT       = 0.60;

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final StockMarketDataService       marketDataService;
    private final MarketIndexWeightRepository  weightRepository;

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns the net index-based adjustment factor that should be applied to
     * the prediction ensemble price for {@code symbol}.
     *
     * <p>The factor is a signed decimal (typically in the range −0.10 to +0.10).
     * Callers should apply: {@code adjustedPrice = rawPrice × (1 + factor)}.
     *
     * @param symbol portfolio stock ticker
     * @return signed adjustment factor (0.0 if index data is unavailable)
     */
    public double computeIndexAdjustmentFactor(String symbol) {
        try {
            List<BigDecimal> stockHistory = marketDataService.getPrices(symbol, CORRELATION_BARS);
            if (stockHistory.size() < 2) return 0.0;

            Map<String, Double> weights = loadOrInitWeights(symbol, stockHistory);

            double factor = 0.0;
            for (String idx : INDEX_SYMBOLS) {
                try {
                    List<BigDecimal> indexHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    if (indexHistory.size() < 2) continue;

                    double corr    = pearsonCorrelation(logReturns(stockHistory), logReturns(indexHistory));
                    double idxRet  = todayReturn(indexHistory);
                    double w       = weights.getOrDefault(idx, 1.0 / INDEX_SYMBOLS.size());

                    factor += w * corr * idxRet;
                } catch (Exception e) {
                    log.debug("Skipping index {} for {}: {}", idx, symbol, e.getMessage());
                }
            }
            return factor * DAMPENING;
        } catch (Exception e) {
            log.warn("Index adjustment failed for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Builds the full {@link MarketIndexInfluence} list for a stock, used to
     * populate the prediction popup in the frontend.
     *
     * @param symbol portfolio stock ticker
     * @return one entry per index; empty list if data is unavailable
     */
    public List<MarketIndexInfluence> getIndexInfluences(String symbol) {
        List<MarketIndexInfluence> influences = new ArrayList<>();
        try {
            List<BigDecimal> stockHistory = marketDataService.getPrices(symbol, CORRELATION_BARS);
            if (stockHistory.size() < 2) return List.of();

            Map<String, Double> weights = loadOrInitWeights(symbol, stockHistory);

            for (String idx : INDEX_SYMBOLS) {
                try {
                    List<BigDecimal> idxHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    if (idxHistory.size() < 2) {
                        influences.add(buildUnavailableInfluence(idx, weights.getOrDefault(idx, 0.2)));
                        continue;
                    }

                    double corr      = pearsonCorrelation(logReturns(stockHistory), logReturns(idxHistory));
                    double idxRet    = todayReturn(idxHistory);
                    double w         = weights.getOrDefault(idx, 1.0 / INDEX_SYMBOLS.size());
                    double influence = w * corr * idxRet * DAMPENING * 100; // as %

                    influences.add(new MarketIndexInfluence(
                        idx,
                        idxHistory.getLast(),
                        round2(idxRet * 100),
                        round4(corr),
                        round4(w),
                        round4(influence)
                    ));
                } catch (Exception e) {
                    log.debug("Skipping index {} for {}: {}", idx, symbol, e.getMessage());
                    influences.add(buildUnavailableInfluence(idx, weights.getOrDefault(idx, 0.2)));
                }
            }
        } catch (Exception e) {
            log.warn("getIndexInfluences failed for {}: {}", symbol, e.getMessage());
        }
        return influences;
    }

    /**
     * Refreshes the correlation figures and index weights for {@code symbol}.
     * Should be called once per batch cycle (hourly) <em>before</em> predictions
     * are calculated so that the freshest correlations drive the adjustment.
     *
     * @param symbol portfolio stock ticker
     */
    @Transactional
    public void refreshCorrelations(String symbol) {
        try {
            List<BigDecimal> stockHistory = marketDataService.getPrices(symbol, CORRELATION_BARS);
            if (stockHistory.size() < 10) return;

            List<Double> stockReturns = logReturns(stockHistory);
            Map<String, Double> currentWeights = loadOrInitWeights(symbol, stockHistory);
            Map<String, Double> newCorrelations = new LinkedHashMap<>();

            for (String idx : INDEX_SYMBOLS) {
                try {
                    List<BigDecimal> idxHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    if (idxHistory.size() < 10) continue;
                    double corr = pearsonCorrelation(stockReturns, logReturns(idxHistory));
                    newCorrelations.put(idx, corr);
                } catch (Exception e) {
                    log.debug("Correlation refresh skipped for index {} / {}: {}", idx, symbol, e.getMessage());
                }
            }

            // Persist updated correlations (weights unchanged — learning happens in updateIndexWeights)
            LocalDate today = LocalDate.now();
            for (Map.Entry<String, Double> e : newCorrelations.entrySet()) {
                double w = currentWeights.getOrDefault(e.getKey(), 1.0 / INDEX_SYMBOLS.size());
                weightRepository.upsertWeight(symbol, e.getKey(), w, e.getValue(), today);
            }

            log.debug("Refreshed index correlations for {}: {}", symbol, newCorrelations);
        } catch (Exception e) {
            log.warn("refreshCorrelations failed for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Adjusts index weights based on how well each index's directional signal
     * matched the actual observed stock movement.  Call this after an actual
     * price has been resolved for {@code symbol}.
     *
     * <p>For each index {@code i}:
     * <ul>
     *   <li>Compute the index signal: {@code signal_i = correlation_i × indexReturn_i}</li>
     *   <li>If {@code sign(signal_i) == sign(actualStockReturn)} → the index was
     *       directionally correct → increase weight by {@code INCREASE_FACTOR}.</li>
     *   <li>Otherwise → decrease weight by {@code DECREASE_FACTOR}.</li>
     * </ul>
     * Weights are clamped to [MIN_WEIGHT, MAX_WEIGHT] and renormalised.
     *
     * @param symbol            portfolio stock ticker
     * @param actualStockReturn actual observed stock return (signed decimal, e.g. 0.012 = +1.2%)
     */
    @Transactional
    public void updateIndexWeights(String symbol, double actualStockReturn) {
        if (Math.abs(actualStockReturn) < 0.0001) return; // negligible – skip

        try {
            List<MarketIndexWeight> rows = weightRepository.findBySymbol(symbol);
            if (rows.isEmpty()) return;

            Map<String, Double> weights = rows.stream()
                .collect(Collectors.toMap(MarketIndexWeight::getIndexSymbol, MarketIndexWeight::getWeight));
            Map<String, Double> correlations = rows.stream()
                .collect(Collectors.toMap(MarketIndexWeight::getIndexSymbol, MarketIndexWeight::getCorrelation));

            LocalDate today = LocalDate.now();
            for (String idx : INDEX_SYMBOLS) {
                try {
                    List<BigDecimal> idxHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    if (idxHistory.size() < 2) continue;

                    double idxReturn = todayReturn(idxHistory);
                    double corr      = correlations.getOrDefault(idx, 0.0);
                    double signal    = corr * idxReturn;      // predicted direction contribution

                    double currentWeight = weights.getOrDefault(idx, 1.0 / INDEX_SYMBOLS.size());
                    boolean correct = (signal >= 0) == (actualStockReturn >= 0);
                    double newWeight = correct
                        ? Math.min(MAX_WEIGHT, currentWeight * INCREASE_FACTOR)
                        : Math.max(MIN_WEIGHT, currentWeight * DECREASE_FACTOR);

                    weights.put(idx, newWeight);
                    log.debug("{}/{}: signal direction {} → weight {:.4f}→{:.4f}",
                        symbol, idx, correct ? "✓" : "✗", currentWeight, newWeight);

                    weightRepository.upsertWeight(symbol, idx, newWeight, corr, today);
                } catch (Exception e) {
                    log.debug("Weight update skipped for {}/{}: {}", symbol, idx, e.getMessage());
                }
            }

            // Renormalise
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total > 0) {
                LocalDate finalToday = LocalDate.now();
                weights.forEach((idx, w) -> {
                    double normed = w / total;
                    weights.put(idx, normed);
                    try {
                        double corr = correlations.getOrDefault(idx, 0.0);
                        weightRepository.upsertWeight(symbol, idx, normed, corr, finalToday);
                    } catch (Exception ex) {
                        log.debug("Normalisation upsert failed for {}/{}: {}", symbol, idx, ex.getMessage());
                    }
                });
            }

            log.info("Updated index weights for {} based on actual return {:.4f}", symbol, actualStockReturn);
        } catch (Exception e) {
            log.warn("updateIndexWeights failed for {}: {}", symbol, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Loads existing weights from the DB for (symbol, each index).
     * For any index not yet stored, initialises from the absolute correlation,
     * then persists the new row.
     */
    private Map<String, Double> loadOrInitWeights(String symbol,
                                                   List<BigDecimal> stockHistory) {
        Map<String, Double> weights = new LinkedHashMap<>();

        List<MarketIndexWeight> existing = weightRepository.findBySymbol(symbol);
        Map<String, MarketIndexWeight> byIdx = existing.stream()
            .collect(Collectors.toMap(MarketIndexWeight::getIndexSymbol, w -> w));

        // If we already have all five indices, just load and return
        if (byIdx.size() == INDEX_SYMBOLS.size()) {
            INDEX_SYMBOLS.forEach(idx -> weights.put(idx, byIdx.get(idx).getWeight()));
            return weights;
        }

        // Otherwise build from correlations and fill in missing entries
        List<Double> stockReturns = logReturns(stockHistory);
        Map<String, Double> absCorrMap = new LinkedHashMap<>();

        for (String idx : INDEX_SYMBOLS) {
            if (byIdx.containsKey(idx)) {
                weights.put(idx, byIdx.get(idx).getWeight());
            } else {
                try {
                    List<BigDecimal> idxHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    if (idxHistory.size() >= 2) {
                        double corr = pearsonCorrelation(stockReturns, logReturns(idxHistory));
                        absCorrMap.put(idx, Math.abs(corr));
                    } else {
                        absCorrMap.put(idx, 0.0);
                    }
                } catch (Exception e) {
                    absCorrMap.put(idx, 0.0);
                }
            }
        }

        // Determine normalised initial weights for missing indices
        if (!absCorrMap.isEmpty()) {
            double sum = absCorrMap.values().stream().mapToDouble(Double::doubleValue).sum();
            // If all correlations are zero, use equal weights
            if (sum < 1e-6) {
                absCorrMap.replaceAll((k, v) -> 1.0 / absCorrMap.size());
                sum = 1.0;
            }
            for (Map.Entry<String, Double> e : absCorrMap.entrySet()) {
                String idx = e.getKey();
                double initWeight = e.getValue() / sum;
                // Clamp so no single index dominates from the start
                initWeight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, initWeight));
                weights.put(idx, initWeight);

                // Persist the newly initialised row
                try {
                    List<BigDecimal> idxHistory = marketDataService.getPrices(idx, CORRELATION_BARS);
                    double corr = idxHistory.size() >= 2
                        ? pearsonCorrelation(stockReturns, logReturns(idxHistory)) : 0.0;
                    weightRepository.upsertWeight(symbol, idx, initWeight, corr, LocalDate.now());
                } catch (Exception ex) {
                    log.debug("Init upsert skipped for {}/{}: {}", symbol, idx, ex.getMessage());
                }
            }

            // Re-normalise after clamping
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total > 0) weights.replaceAll((k, v) -> v / total);
        }

        return weights;
    }

    // ── Return series ─────────────────────────────────────────────────────────

    /**
     * Converts a price series to log-returns:
     * {@code r_i = ln(P_i / P_{i-1})}.
     */
    private List<Double> logReturns(List<BigDecimal> prices) {
        List<Double> ret = new ArrayList<>(prices.size() - 1);
        for (int i = 1; i < prices.size(); i++) {
            double prev = prices.get(i - 1).doubleValue();
            double curr = prices.get(i).doubleValue();
            if (prev > 0 && curr > 0) {
                ret.add(Math.log(curr / prev));
            } else {
                ret.add(0.0);
            }
        }
        return ret;
    }

    /**
     * Computes today's intraday return as a decimal (e.g. 0.012 for +1.2%).
     * Uses the first bar of the provided history as a proxy for the opening
     * price since the 5-min cache covers ~20 trading hours.
     */
    private double todayReturn(List<BigDecimal> history) {
        if (history.size() < 2) return 0.0;
        double open  = history.getFirst().doubleValue();
        double close = history.getLast().doubleValue();
        return open > 0 ? (close - open) / open : 0.0;
    }

    // ── Pearson correlation ───────────────────────────────────────────────────

    /**
     * Pearson product-moment correlation coefficient between two return series.
     * The series are aligned by taking the last {@code min(|a|, |b|)} entries.
     */
    private double pearsonCorrelation(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n < 5) return 0.0;

        // Use last n values from each
        List<Double> xa = n < a.size() ? a.subList(a.size() - n, a.size()) : a;
        List<Double> xb = n < b.size() ? b.subList(b.size() - n, b.size()) : b;

        double sumA = 0, sumB = 0;
        for (int i = 0; i < n; i++) { sumA += xa.get(i); sumB += xb.get(i); }
        double meanA = sumA / n, meanB = sumB / n;

        double cov = 0, varA = 0, varB = 0;
        for (int i = 0; i < n; i++) {
            double da = xa.get(i) - meanA, db = xb.get(i) - meanB;
            cov  += da * db;
            varA += da * da;
            varB += db * db;
        }

        double denom = Math.sqrt(varA * varB);
        if (denom < 1e-12) return 0.0;
        double r = cov / denom;
        // Clamp to [-1, 1] to guard against floating-point drift
        return Math.max(-1.0, Math.min(1.0, r));
    }

    // ── Unavailable placeholder ───────────────────────────────────────────────

    private MarketIndexInfluence buildUnavailableInfluence(String idx, double weight) {
        return new MarketIndexInfluence(idx, BigDecimal.ZERO, 0.0, 0.0, weight, 0.0);
    }

    // ── Rounding helpers ──────────────────────────────────────────────────────

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
