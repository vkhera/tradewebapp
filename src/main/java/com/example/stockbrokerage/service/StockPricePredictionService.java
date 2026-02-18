package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.HourlyPricePrediction;
import com.example.stockbrokerage.dto.StockPricePredictionResponse;
import com.example.stockbrokerage.entity.StockPricePrediction;
import com.example.stockbrokerage.repository.StockPricePredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Predicts hourly stock prices for the next 8 hours using 5 techniques
 * with adaptive per‑stock technique weights that improve over time.
 *
 * Techniques:
 *  1. Linear Regression   – OLS trend line over last 40h → extrapolate
 *  2. EMA Extrapolation   – Double EMA captures level + trend component
 *  3. Momentum            – Rate-of-change projected linearly
 *  4. Mean Reversion      – Bollinger-band pull toward 20h mean
 *  5. Holt-Winters        – Double exponential smoothing (level + trend)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPricePredictionService {

    // ------------------------------------------------------------------ constants
    private static final String DATA_DIR        = "stock_predictions";
    private static final int    HISTORY_HOURS   = 40;
    private static final int    PREDICT_HOURS   = 8;
    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final String TECH_LINEAR_REGRESSION = "Linear_Regression";
    static final String TECH_EMA_EXTRAPOLATION = "EMA_Extrapolation";
    static final String TECH_MOMENTUM          = "Momentum";
    static final String TECH_MEAN_REVERSION    = "Mean_Reversion";
    static final String TECH_HOLT_WINTERS      = "Holt_Winters";

    private static final List<String> ALL_TECHNIQUES = List.of(
        TECH_LINEAR_REGRESSION,
        TECH_EMA_EXTRAPOLATION,
        TECH_MOMENTUM,
        TECH_MEAN_REVERSION,
        TECH_HOLT_WINTERS
    );

    // weighting: ×1.15 for low error, ×0.85 for high error; min 0.05, max 0.60
    private static final double INCREASE_FACTOR = 1.15;
    private static final double DECREASE_FACTOR = 0.85;
    private static final double MIN_WEIGHT       = 0.05;
    private static final double MAX_WEIGHT       = 0.60;

    private final StockMarketDataService    marketDataService;
    private final StockPricePredictionRepository repository;

    // ================================================================= public API

    /**
     * Generates predictions for the next {@code PREDICT_HOURS} hours.
     * Returns last cached result if calculated within the last 50 minutes.
     */
    public StockPricePredictionResponse getPredictions(String symbol) {
        ensureDataDir();

        // Return cached response if fresh enough
        StockPricePredictionResponse cached = loadLatestFromDb(symbol);
        if (cached != null) {
            cached.setCached(true);
            return cached;
        }

        return calculateAndStore(symbol);
    }

    /**
     * Forces recalculation regardless of cache. Called by the batch scheduler.
     */
    public StockPricePredictionResponse calculateAndStore(String symbol) {
        log.info("Calculating price predictions for {}", symbol);
        ensureDataDir();

        List<BigDecimal> history = marketDataService.getHourlyPrices(symbol, HISTORY_HOURS);
        if (history.size() < 5) {
            log.warn("Insufficient history for {} ({} points)", symbol, history.size());
            return buildEmptyResponse(symbol);
        }

        BigDecimal currentPrice = history.getLast();
        Map<String, Double> weights = loadWeights(symbol);

        LocalDateTime baseHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        List<HourlyPricePrediction> hourlyPredictions = new ArrayList<>();

        for (int h = 1; h <= PREDICT_HOURS; h++) {
            LocalDateTime targetHour = baseHour.plusHours(h);

            // Run each technique
            Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
            breakdown.put(TECH_LINEAR_REGRESSION, linearRegression(history, h));
            breakdown.put(TECH_EMA_EXTRAPOLATION, emaExtrapolation(history, h));
            breakdown.put(TECH_MOMENTUM,          momentum(history, h));
            breakdown.put(TECH_MEAN_REVERSION,    meanReversion(history, h));
            breakdown.put(TECH_HOLT_WINTERS,      holtWinters(history, h));

            // Weighted ensemble
            BigDecimal weightedPrice = weightedMean(breakdown, weights);
            double confidence = calculateConfidence(breakdown, weights);

            HourlyPricePrediction prediction = new HourlyPricePrediction(
                targetHour, weightedPrice, confidence,
                new LinkedHashMap<>(breakdown), new LinkedHashMap<>(weights)
            );
            hourlyPredictions.add(prediction);

            // Persist each technique's prediction to DB
            persistTechniquesPredictions(symbol, baseHour, targetHour, breakdown, weights);
        }

        // Save per-symbol prediction CSV
        savePredictionCsv(symbol, currentPrice, baseHour, hourlyPredictions);

        StockPricePredictionResponse response = new StockPricePredictionResponse(
            symbol, currentPrice, baseHour,
            hourlyPredictions, new LinkedHashMap<>(weights),
            averageConfidence(hourlyPredictions), false,
            LocalDateTime.now()
        );

        log.info("Completed predictions for {} – current price: {}, 1h prediction: {}",
            symbol, currentPrice, hourlyPredictions.getFirst().getPredictedPrice());

        return response;
    }

    /**
     * Called by the batch scheduler to resolve past predictions and update weights.
     */
    public void resolveAndUpdateWeights(String symbol) {
        log.info("Resolving past predictions and updating weights for {}", symbol);

        BigDecimal currentPrice = marketDataService.getCurrentPrice(symbol);
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // Find DB predictions whose target hour has passed but actual price not filled
        List<StockPricePrediction> unresolved = repository.findUnresolvedPredictions(symbol, now);
        if (unresolved.isEmpty()) return;

        // Group by technique and compute average error
        Map<String, List<BigDecimal>> techniqueErrors = new HashMap<>();

        for (StockPricePrediction pred : unresolved) {
            pred.setActualPrice(currentPrice);
            BigDecimal absErr = currentPrice.subtract(pred.getPredictedPrice()).abs();
            BigDecimal pctErr = currentPrice.compareTo(BigDecimal.ZERO) > 0
                ? absErr.divide(currentPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            pred.setAbsoluteError(absErr);
            pred.setPercentageError(pctErr);
            repository.save(pred);

            techniqueErrors.computeIfAbsent(pred.getTechnique(), k -> new ArrayList<>()).add(pctErr);
        }

        // Update weights: lower error → increase weight
        Map<String, Double> weights = loadWeights(symbol);
        for (Map.Entry<String, List<BigDecimal>> entry : techniqueErrors.entrySet()) {
            String technique = entry.getKey();
            double avgErr = entry.getValue().stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElse(5.0);

            double current = weights.getOrDefault(technique, 0.2);
            double adjusted = avgErr < 2.0
                ? Math.min(MAX_WEIGHT, current * INCREASE_FACTOR)   // good prediction (< 2% error)
                : Math.max(MIN_WEIGHT, current * DECREASE_FACTOR);  // poor prediction
            weights.put(technique, adjusted);

            log.debug("{} – technique {} avg error: {:.2f}% → weight {:.4f} → {:.4f}",
                symbol, technique, avgErr, current, adjusted);
        }

        // Normalise
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        weights.replaceAll((k, v) -> v / total);

        saveWeights(symbol, weights);
        log.info("Updated per-stock weights for {} based on {} resolved predictions", symbol, unresolved.size());
    }

    // ============================================================= prediction techniques

    /** OLS linear regression on prices, extrapolated h steps. */
    private BigDecimal linearRegression(List<BigDecimal> prices, int h) {
        int n = prices.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = prices.get(i).doubleValue();
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) return prices.getLast();
        double slope     = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        double predicted = intercept + slope * (n - 1 + h);
        return toPrice(predicted);
    }

    /** Double EMA: captures both level and trend, projects h steps. */
    private BigDecimal emaExtrapolation(List<BigDecimal> prices, int h) {
        double alpha = 2.0 / (Math.min(prices.size(), 12) + 1);
        double ema1 = prices.getFirst().doubleValue();
        double ema2 = ema1;
        for (BigDecimal p : prices) {
            ema1 = alpha * p.doubleValue() + (1 - alpha) * ema1;
            ema2 = alpha * ema1 + (1 - alpha) * ema2;
        }
        double a = 2 * ema1 - ema2;
        double b = (alpha / (1 - alpha)) * (ema1 - ema2);
        return toPrice(a + b * h);
    }

    /** Momentum: rate of change over last 5h, projected linearly. */
    private BigDecimal momentum(List<BigDecimal> prices, int h) {
        int lookback = Math.min(5, prices.size() - 1);
        if (lookback <= 0) return prices.getLast();
        double old = prices.get(prices.size() - 1 - lookback).doubleValue();
        double now = prices.getLast().doubleValue();
        double roc = (now - old) / lookback;     // $ per hour
        return toPrice(now + roc * h);
    }

    /** Mean reversion: pulls predicted price toward 20h Bollinger-band center. */
    private BigDecimal meanReversion(List<BigDecimal> prices, int h) {
        int window = Math.min(20, prices.size());
        List<BigDecimal> slice = prices.subList(prices.size() - window, prices.size());
        double mean   = slice.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double now    = prices.getLast().doubleValue();
        double dev    = now - mean;
        // Exponential decay toward mean; half-life ≈ 6 hours
        double decay  = Math.exp(-0.115 * h);
        return toPrice(mean + dev * decay);
    }

    /** Holt-Winters double exponential smoothing with level (α) and trend (β). */
    private BigDecimal holtWinters(List<BigDecimal> prices, int h) {
        double alpha = 0.3, beta = 0.1;
        double level = prices.getFirst().doubleValue();
        double trend = (prices.getLast().doubleValue() - prices.getFirst().doubleValue())
            / Math.max(1, prices.size() - 1);

        for (int i = 1; i < prices.size(); i++) {
            double val       = prices.get(i).doubleValue();
            double prevLevel = level;
            level = alpha * val + (1 - alpha) * (level + trend);
            trend = beta  * (level - prevLevel) + (1 - beta) * trend;
        }
        return toPrice(level + trend * h);
    }

    // ============================================================= helpers

    private BigDecimal weightedMean(Map<String, BigDecimal> breakdown, Map<String, Double> weights) {
        double num = 0, den = 0;
        for (Map.Entry<String, BigDecimal> e : breakdown.entrySet()) {
            double w = weights.getOrDefault(e.getKey(), 0.2);
            num += w * e.getValue().doubleValue();
            den += w;
        }
        return den == 0 ? BigDecimal.ZERO : toPrice(num / den);
    }

    /**
     * Confidence: 1 – (normalised std-dev of technique predictions).
     * When all techniques agree perfectly → confidence = 1.
     */
    private double calculateConfidence(Map<String, BigDecimal> breakdown, Map<String, Double> weights) {
        BigDecimal weightedPrice = weightedMean(breakdown, weights);
        double ref  = weightedPrice.doubleValue();
        if (ref == 0) return 0.5;
        double sumSq = 0;
        for (BigDecimal v : breakdown.values()) {
            double diff = (v.doubleValue() - ref) / ref;
            sumSq += diff * diff;
        }
        double stdDev = Math.sqrt(sumSq / breakdown.size());
        return Math.max(0, Math.min(1, 1 - stdDev * 10));
    }

    private double averageConfidence(List<HourlyPricePrediction> list) {
        return list.stream().mapToDouble(HourlyPricePrediction::getConfidence).average().orElse(0.5);
    }

    private BigDecimal toPrice(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) return BigDecimal.valueOf(0.01);
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    // ============================================================= weight persistence

    private String weightsFilePath(String symbol) {
        return DATA_DIR + "/" + symbol + "_pred_weights.csv";
    }

    Map<String, Double> loadWeights(String symbol) {
        Path path = Paths.get(weightsFilePath(symbol));
        Map<String, Double> weights = new LinkedHashMap<>();
        // defaults
        ALL_TECHNIQUES.forEach(t -> weights.put(t, 0.2));

        if (!Files.exists(path)) {
            saveWeights(symbol, weights);
            return weights;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",");
                if (p.length >= 2) weights.put(p[0].trim(), Double.parseDouble(p[1].trim()));
            }
        } catch (IOException e) {
            log.error("Error loading prediction weights for {}: {}", symbol, e.getMessage());
        }
        return weights;
    }

    void saveWeights(String symbol, Map<String, Double> weights) {
        Path path = Paths.get(weightsFilePath(symbol));
        try (PrintWriter w = new PrintWriter(new FileWriter(path.toFile()))) {
            w.println("Technique,Weight,LastUpdated");
            weights.forEach((t, wt) ->
                w.printf("%s,%.6f,%s%n", t, wt, LocalDateTime.now().format(FMT)));
        } catch (IOException e) {
            log.error("Error saving prediction weights for {}: {}", symbol, e.getMessage());
        }
    }

    // ============================================================= CSV prediction log

    private void savePredictionCsv(String symbol, BigDecimal currentPrice,
                                    LocalDateTime madeAt, List<HourlyPricePrediction> preds) {
        String fileName = DATA_DIR + "/" + symbol + "_hourly_predictions.csv";
        Path filePath = Paths.get(fileName);
        boolean isNew = !Files.exists(filePath);

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile(), true))) {
            if (isNew) {
                w.println("PredictionMadeAt,CurrentPrice,TargetHour,PredictedPrice,Confidence,"
                    + String.join(",", ALL_TECHNIQUES));
            }
            for (HourlyPricePrediction hp : preds) {
                StringBuilder sb = new StringBuilder();
                sb.append(madeAt.format(FMT)).append(",")
                  .append(currentPrice.toPlainString()).append(",")
                  .append(hp.getTargetHour().format(FMT)).append(",")
                  .append(hp.getPredictedPrice().toPlainString()).append(",")
                  .append("%.4f".formatted(hp.getConfidence()));
                for (String t : ALL_TECHNIQUES) {
                    BigDecimal v = hp.getTechniqueBreakdown().getOrDefault(t, BigDecimal.ZERO);
                    sb.append(",").append(v.toPlainString());
                }
                w.println(sb);
            }
        } catch (IOException e) {
            log.error("Error saving prediction CSV for {}: {}", symbol, e.getMessage());
        }
    }

    // ============================================================= DB persistence

    private void persistTechniquesPredictions(String symbol, LocalDateTime madeAt,
                                               LocalDateTime targetHour,
                                               Map<String, BigDecimal> breakdown,
                                               Map<String, Double> weights) {
        for (Map.Entry<String, BigDecimal> e : breakdown.entrySet()) {
            String technique = e.getKey();
            // Avoid duplicate rows for same symbol+technique+targetHour
            Optional<StockPricePrediction> existing =
                repository.findBySymbolAndTechniqueAndTargetHour(symbol, technique, targetHour);
            if (existing.isPresent()) continue;

            StockPricePrediction rec = new StockPricePrediction();
            rec.setSymbol(symbol);
            rec.setTechnique(technique);
            rec.setPredictionMadeAt(madeAt);
            rec.setTargetHour(targetHour);
            rec.setPredictedPrice(e.getValue());
            repository.save(rec);
        }
    }

    // ============================================================= DB cached load

    private StockPricePredictionResponse loadLatestFromDb(String symbol) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(50);
        LocalDateTime now    = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        List<StockPricePrediction> recs =
            repository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(symbol, now);

        if (recs.isEmpty()) return null;

        // check freshness
        boolean fresh = recs.stream().allMatch(r -> r.getPredictionMadeAt().isAfter(cutoff));
        if (!fresh) return null;

        // Group by targetHour
        Map<LocalDateTime, List<StockPricePrediction>> byHour = recs.stream()
            .collect(Collectors.groupingBy(StockPricePrediction::getTargetHour));

        if (byHour.isEmpty()) return null;

        Map<String, Double> weights = loadWeights(symbol);
        BigDecimal currentPrice = marketDataService.getCurrentPrice(symbol);

        List<HourlyPricePrediction> predictions = byHour.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(PREDICT_HOURS)
            .map(entry -> {
                Map<String, BigDecimal> breakdown = entry.getValue().stream()
                    .collect(Collectors.toMap(
                        StockPricePrediction::getTechnique,
                        StockPricePrediction::getPredictedPrice,
                        (a, b) -> a, LinkedHashMap::new));

                BigDecimal weighted = weightedMean(breakdown, weights);
                double conf = calculateConfidence(breakdown, weights);
                return new HourlyPricePrediction(entry.getKey(), weighted, conf,
                    breakdown, new LinkedHashMap<>(weights));
            })
            .collect(Collectors.toList());

        LocalDateTime madeAt = recs.getFirst().getPredictionMadeAt();
        return new StockPricePredictionResponse(
            symbol, currentPrice, madeAt, predictions, new LinkedHashMap<>(weights),
            averageConfidence(predictions), true, madeAt);
    }

    private StockPricePredictionResponse buildEmptyResponse(String symbol) {
        BigDecimal cur = marketDataService.getCurrentPrice(symbol);
        return new StockPricePredictionResponse(
            symbol, cur, LocalDateTime.now(), List.of(), Map.of(), 0.0, false, LocalDateTime.now());
    }

    private void ensureDataDir() {
        Path dir = Paths.get(DATA_DIR);
        if (!Files.exists(dir)) {
            try { Files.createDirectories(dir); } catch (IOException e) { /* ignored */ }
        }
    }
}
