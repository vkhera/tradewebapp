package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import com.example.stockbrokerage.dto.TrendPrediction;
import com.example.stockbrokerage.dto.TrendPrediction.TrendDirection;
import com.example.stockbrokerage.entity.TrendPredictionWeightHistory;
import com.example.stockbrokerage.repository.TrendPredictionResultRepository;
import com.example.stockbrokerage.repository.TrendPredictionWeightHistoryRepository;
import com.example.stockbrokerage.repository.TrendPredictionWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalysisService {
    
    private static final String PREDICTIONS_DIR = "trend_predictions";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final StockPriceService stockPriceService;
    private final TrendPredictionResultRepository trendResultRepository;
    private final TrendPredictionWeightRepository trendWeightRepository;
    private final TrendPredictionWeightHistoryRepository trendWeightHistoryRepository;
    private final MarketIndexService marketIndexService;
    private final OptionsDataService optionsDataService;
    private final NewsSentimentService newsSentimentService;
    private final EtfActivityService etfActivityService;

    // Technique names
    private static final String MA_CROSSOVER       = "MA_Crossover";
    private static final String RSI                = "RSI";
    private static final String MACD               = "MACD";
    private static final String PRICE_MOMENTUM     = "Price_Momentum";
    private static final String VOLUME_TREND       = "Volume_Trend";
    /** Composite trend signal derived from weighted market-index movements. */
    private static final String INDEX_MOMENTUM     = "Index_Momentum";
    /** Contrarian sentiment signal derived from options market put/call ratio and IV. */
    private static final String OPTIONS_SENTIMENT  = "Options_Sentiment";
    /** Directional signal derived from recent LLM-scored company news. */
    private static final String NEWS_SENTIMENT     = "News_Sentiment";
    /** ETF holdings-change signal from BUZZ (social buzz), HDGE (short), MMTM (momentum). */
    private static final String ETF_SIGNAL         = "ETF_Signal";
    
    @Transactional
    public TrendPrediction analyzeTrend(String symbol) {
        log.info("Analyzing trend for symbol: {}", symbol);
        
        // Ensure directories exist
        ensureDirectoriesExist();
        
        // Load current technique weights (per-stock)
        Map<String, Double> weights = loadWeights(symbol);
        
        // Calculate trends using different techniques
        Map<String, TrendDirection> techniqueResults = new HashMap<>();
        
        try {
            // Get historical prices (simulated for now)
            List<BigDecimal> prices = getHistoricalPrices(symbol, 200);
            List<Long> volumes = getHistoricalVolumes(symbol, 200);
            
            techniqueResults.put(MA_CROSSOVER,   calculateMACrossover(prices));
            techniqueResults.put(RSI,             calculateRSI(prices));
            techniqueResults.put(MACD,            calculateMACD(prices));
            techniqueResults.put(PRICE_MOMENTUM,     calculatePriceMomentum(prices));
            techniqueResults.put(VOLUME_TREND,        calculateVolumeTrend(volumes));
            techniqueResults.put(INDEX_MOMENTUM,      calculateIndexMomentum(symbol));
            techniqueResults.put(OPTIONS_SENTIMENT,   calculateOptionsSentiment(symbol));
            techniqueResults.put(NEWS_SENTIMENT,      calculateNewsSentiment(symbol));
            techniqueResults.put(ETF_SIGNAL,           calculateEtfSignal(symbol));

        } catch (Exception e) {
            log.error("Error calculating trends for {}", symbol, e);
            // Return default if error
            return createDefaultPrediction(symbol, weights);
        }
        
        // Calculate weighted overall trend
        TrendDirection overallTrend = calculateWeightedTrend(techniqueResults, weights);
        double confidence = calculateConfidence(techniqueResults, weights);
        
        TrendPrediction prediction = new TrendPrediction(
            symbol,
            LocalDate.now(),
            overallTrend,
            techniqueResults,
            weights,
            confidence
        );
        
        // Save prediction
        savePrediction(prediction);
        
        log.info("Trend analysis for {}: {} with {}% confidence", symbol, overallTrend, 
            String.format("%.2f", confidence * 100));
        
        return prediction;
    }
    
    private TrendDirection calculateMACrossover(List<BigDecimal> prices) {
        if (prices.size() < 200) {
            return TrendDirection.SIDEWAYS;
        }
        
        BigDecimal sma50 = calculateSMA(prices, 50);
        BigDecimal sma200 = calculateSMA(prices, 200);
        
        if (sma50.compareTo(sma200) > 0) {
            return TrendDirection.UPTREND;
        } else if (sma50.compareTo(sma200) < 0) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
    }
    
    private TrendDirection calculateRSI(List<BigDecimal> prices) {
        if (prices.size() < 14) {
            return TrendDirection.SIDEWAYS;
        }
        
        double rsi = calculateRSIValue(prices, 14);
        
        if (rsi > 70) {
            return TrendDirection.DOWNTREND; // Overbought
        } else if (rsi < 30) {
            return TrendDirection.UPTREND; // Oversold
        }
        return TrendDirection.SIDEWAYS;
    }
    
    private TrendDirection calculateMACD(List<BigDecimal> prices) {
        if (prices.size() < 26) {
            return TrendDirection.SIDEWAYS;
        }
        
        BigDecimal ema12 = calculateEMA(prices, 12);
        BigDecimal ema26 = calculateEMA(prices, 26);
        BigDecimal macd = ema12.subtract(ema26);
        
        if (macd.compareTo(BigDecimal.ZERO) > 0) {
            return TrendDirection.UPTREND;
        } else if (macd.compareTo(BigDecimal.ZERO) < 0) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
    }
    
    private TrendDirection calculatePriceMomentum(List<BigDecimal> prices) {
        if (prices.size() < 20) {
            return TrendDirection.SIDEWAYS;
        }
        
        BigDecimal currentPrice = prices.get(prices.size() - 1);
        BigDecimal priceWeekAgo = prices.get(prices.size() - 5);
        BigDecimal priceMonthAgo = prices.get(prices.size() - 20);
        
        BigDecimal weekChange = currentPrice.subtract(priceWeekAgo).divide(priceWeekAgo, 4, RoundingMode.HALF_UP);
        BigDecimal monthChange = currentPrice.subtract(priceMonthAgo).divide(priceMonthAgo, 4, RoundingMode.HALF_UP);
        
        BigDecimal avgChange = weekChange.add(monthChange).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        
        if (avgChange.compareTo(BigDecimal.valueOf(0.02)) > 0) {
            return TrendDirection.UPTREND;
        } else if (avgChange.compareTo(BigDecimal.valueOf(-0.02)) < 0) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
    }
    
    /**
     * Contrarian sentiment signal derived from the options market.
     *
     * <p>Uses the front-month put/call open-interest ratio as a sentiment gauge:
     * <ul>
     *   <li>PCR &gt; 1.5 – extreme bearish hedging (fear) → contrarian <b>UPTREND</b></li>
     *   <li>PCR &lt; 0.7 – heavy call buying (greed/complacency) → contrarian <b>DOWNTREND</b></li>
     *   <li>otherwise → SIDEWAYS</li>
     * </ul>
     * Returns SIDEWAYS when options data is unavailable so downstream weight
     * calculations are unaffected.
     */
    private TrendDirection calculateOptionsSentiment(String symbol) {
        OptionsSnapshot snap = optionsDataService.getOptionsSnapshot(symbol);
        if (!snap.dataAvailable()) {
            log.debug("OPTIONS_SENTIMENT unavailable for {} – defaulting to SIDEWAYS", symbol);
            return TrendDirection.SIDEWAYS;
        }
        double iv  = snap.atmImpliedVolatility();
        double pcr = snap.putCallRatioOI();
        if (pcr > 1.5) {
            log.debug("OPTIONS_SENTIMENT for {}: UPTREND (PCR={:.2f}, IV={:.1f}%)", symbol, pcr, iv * 100);
            return TrendDirection.UPTREND;
        }
        if (pcr < 0.7) {
            log.debug("OPTIONS_SENTIMENT for {}: DOWNTREND (PCR={:.2f}, IV={:.1f}%)", symbol, pcr, iv * 100);
            return TrendDirection.DOWNTREND;
        }
        log.debug("OPTIONS_SENTIMENT for {}: SIDEWAYS (PCR={:.2f}, IV={:.1f}%)", symbol, pcr, iv * 100);
        return TrendDirection.SIDEWAYS;
    }

    /**
     * Derives a trend direction from the composite market-index adjustment factor.
     * A positive dampened factor means the weighted basket of indices is signalling
     * upward pressure on this stock; negative means downward.
     *
     * <p>Thresholds (post-dampening):
     * <ul>
     *   <li>&gt; 0.002 (+0.2%) → UPTREND</li>
     *   <li>&lt; −0.002 (−0.2%) → DOWNTREND</li>
     *   <li>otherwise → SIDEWAYS</li>
     * </ul>
     */
    private TrendDirection calculateIndexMomentum(String symbol) {
        try {
            double factor = marketIndexService.computeIndexAdjustmentFactor(symbol);
            log.debug("Index momentum factor for {}: {:.4f}", symbol, factor);
            if (factor >  0.002) return TrendDirection.UPTREND;
            if (factor < -0.002) return TrendDirection.DOWNTREND;
        } catch (Exception e) {
            log.debug("Index momentum unavailable for {}: {}", symbol, e.getMessage());
        }
        return TrendDirection.SIDEWAYS;
    }

    private TrendDirection calculateNewsSentiment(String symbol) {
        try {
            TrendDirection direction = newsSentimentService.calculateNewsTrend(symbol, 5);
            log.debug("News sentiment for {}: {}", symbol, direction);
            return direction;
        } catch (Exception e) {
            log.debug("News sentiment unavailable for {}: {}", symbol, e.getMessage());
            return TrendDirection.SIDEWAYS;
        }
    }

    private TrendDirection calculateEtfSignal(String symbol) {
        try {
            TrendDirection direction = etfActivityService.getEtfSignal(symbol);
            log.debug("ETF signal for {}: {}", symbol, direction);
            return direction;
        } catch (Exception e) {
            log.debug("ETF signal unavailable for {}: {}", symbol, e.getMessage());
            return TrendDirection.SIDEWAYS;
        }
    }

    private TrendDirection calculateVolumeTrend(List<Long> volumes) {
        if (volumes.size() < 20) {
            return TrendDirection.SIDEWAYS;
        }
        
        long avgRecentVolume = volumes.subList(volumes.size() - 10, volumes.size()).stream()
            .mapToLong(Long::longValue).sum() / 10;
        long avgOlderVolume = volumes.subList(volumes.size() - 20, volumes.size() - 10).stream()
            .mapToLong(Long::longValue).sum() / 10;
        
        double volumeChange = (double) (avgRecentVolume - avgOlderVolume) / avgOlderVolume;
        
        if (volumeChange > 0.2) {
            return TrendDirection.UPTREND; // Increasing volume suggests uptrend
        } else if (volumeChange < -0.2) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
    }
    
    private BigDecimal calculateSMA(List<BigDecimal> prices, int period) {
        List<BigDecimal> recentPrices = prices.subList(prices.size() - period, prices.size());
        BigDecimal sum = recentPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = prices.get(prices.size() - period);
        
        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = prices.get(i).subtract(ema).multiply(multiplier).add(ema);
        }
        
        return ema;
    }
    
    private double calculateRSIValue(List<BigDecimal> prices, int period) {
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        
        for (int i = prices.size() - period; i < prices.size() - 1; i++) {
            BigDecimal change = prices.get(i + 1).subtract(prices.get(i));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains.add(change);
                losses.add(BigDecimal.ZERO);
            } else {
                gains.add(BigDecimal.ZERO);
                losses.add(change.abs());
            }
        }
        
        BigDecimal avgGain = gains.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        return 100.0 - (100.0 / (1.0 + rs.doubleValue()));
    }
    
    private TrendDirection calculateWeightedTrend(Map<String, TrendDirection> techniqueResults, 
                                                   Map<String, Double> weights) {
        Map<TrendDirection, Double> scores = new HashMap<>();
        scores.put(TrendDirection.UPTREND, 0.0);
        scores.put(TrendDirection.DOWNTREND, 0.0);
        scores.put(TrendDirection.SIDEWAYS, 0.0);
        
        for (Map.Entry<String, TrendDirection> entry : techniqueResults.entrySet()) {
            String technique = entry.getKey();
            TrendDirection trend = entry.getValue();
            double weight = weights.getOrDefault(technique, 0.2);
            
            scores.put(trend, scores.get(trend) + weight);
        }
        
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(TrendDirection.SIDEWAYS);
    }
    
    private double calculateConfidence(Map<String, TrendDirection> techniqueResults, 
                                       Map<String, Double> weights) {
        Map<TrendDirection, Double> scores = new HashMap<>();
        scores.put(TrendDirection.UPTREND, 0.0);
        scores.put(TrendDirection.DOWNTREND, 0.0);
        scores.put(TrendDirection.SIDEWAYS, 0.0);
        
        double totalWeight = 0.0;
        for (Map.Entry<String, TrendDirection> entry : techniqueResults.entrySet()) {
            String technique = entry.getKey();
            TrendDirection trend = entry.getValue();
            double weight = weights.getOrDefault(technique, 0.2);
            
            scores.put(trend, scores.get(trend) + weight);
            totalWeight += weight;
        }
        
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return totalWeight > 0 ? maxScore / totalWeight : 0.5;
    }
    
    private List<BigDecimal> getHistoricalPrices(String symbol, int days) {
        // Simulate historical prices based on current price
        // In production, this would fetch real historical data
        try {
            BigDecimal currentPrice = stockPriceService.getCurrentPrice(symbol);
            List<BigDecimal> prices = new ArrayList<>();
            
            Random random = new Random();
            BigDecimal price = currentPrice.multiply(BigDecimal.valueOf(0.9)); // Start 10% lower
            
            for (int i = 0; i < days; i++) {
                double change = (random.nextDouble() - 0.5) * 0.03; // +/- 1.5% daily
                price = price.multiply(BigDecimal.valueOf(1 + change));
                prices.add(price);
            }
            
            return prices;
        } catch (Exception e) {
            log.warn("Could not get price for {}, using defaults", symbol);
            return generateDefaultPrices(days);
        }
    }
    
    private List<Long> getHistoricalVolumes(String symbol, int days) {
        // Simulate volume data
        List<Long> volumes = new ArrayList<>();
        Random random = new Random();
        long baseVolume = 1000000;
        
        for (int i = 0; i < days; i++) {
            long volume = baseVolume + (long) ((random.nextDouble() - 0.5) * baseVolume * 0.5);
            volumes.add(volume);
        }
        
        return volumes;
    }
    
    private List<BigDecimal> generateDefaultPrices(int days) {
        List<BigDecimal> prices = new ArrayList<>();
        Random random = new Random();
        BigDecimal price = BigDecimal.valueOf(100);
        
        for (int i = 0; i < days; i++) {
            double change = (random.nextDouble() - 0.5) * 0.03;
            price = price.multiply(BigDecimal.valueOf(1 + change));
            prices.add(price);
        }
        
        return prices;
    }
    
    private void ensureDirectoriesExist() {
        try {
            Path dir = Paths.get(PREDICTIONS_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created predictions directory: {}", PREDICTIONS_DIR);
            }
        } catch (IOException e) {
            log.error("Error creating predictions directory", e);
        }
    }
    
    private Map<String, Double> loadWeights(String symbol) {
        Map<String, Double> weights = new HashMap<>();
        String weightsFile = "%s/%s_weights.csv".formatted(PREDICTIONS_DIR, symbol);
        Path weightsPath = Paths.get(weightsFile);
        
        if (!Files.exists(weightsPath)) {
            // Initialize with balanced weights across all nine techniques (total = 1.00)
            weights.put(MA_CROSSOVER,      0.15);
            weights.put(RSI,               0.14);
            weights.put(MACD,              0.11);
            weights.put(PRICE_MOMENTUM,    0.14);
            weights.put(VOLUME_TREND,      0.07);
            weights.put(INDEX_MOMENTUM,    0.11);
            weights.put(OPTIONS_SENTIMENT, 0.10);
            weights.put(NEWS_SENTIMENT,    0.10);
            weights.put(ETF_SIGNAL,        0.08);
            saveWeights(symbol, weights);
            log.info("Initialized default weights (incl. ETF_Signal) for {}", symbol);
            return weights;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(weightsPath.toFile()))) {
            String line;
            boolean isHeader = true;
            
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    weights.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                }
            }
        } catch (IOException e) {
            log.error("Error loading weights for {}", symbol, e);
        }

        // Ensure new techniques added after initial deployment have a default weight
        // so existing per-symbol CSV files don't need to be migrated manually.
        weights.putIfAbsent(OPTIONS_SENTIMENT, 0.13);
        weights.putIfAbsent(INDEX_MOMENTUM,    0.12);
        weights.putIfAbsent(NEWS_SENTIMENT,    0.11);
        weights.putIfAbsent(ETF_SIGNAL,        0.08);

        normalizeWeights(weights);

        return weights;
    }

    private void normalizeWeights(Map<String, Double> weights) {
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0.0) {
            return;
        }
        for (Map.Entry<String, Double> entry : new HashMap<>(weights).entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / totalWeight);
        }
    }
    
    private void saveWeights(String symbol, Map<String, Double> weights) {
        String weightsFile = "%s/%s_weights.csv".formatted(PREDICTIONS_DIR, symbol);
        Path weightsPath = Paths.get(weightsFile);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        try (PrintWriter writer = new PrintWriter(new FileWriter(weightsPath.toFile()))) {
            writer.println("Technique,Weight,LastUpdated");

            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                writer.println("%s,%.4f,%s".formatted(
                    entry.getKey(),
                    entry.getValue(),
                    today.format(DATE_FORMATTER)
                ));
                // Record history then mirror to PostgreSQL
                try {
                    trendWeightRepository.findBySymbolAndTechnique(symbol, entry.getKey()).ifPresent(existing -> {
                        TrendPredictionWeightHistory hist = new TrendPredictionWeightHistory(
                            null, symbol, entry.getKey(), existing.getWeight(), entry.getValue(), now);
                        trendWeightHistoryRepository.save(hist);
                    });
                    trendWeightRepository.upsertWeight(symbol, entry.getKey(), entry.getValue(), today);
                } catch (Exception e) {
                    log.warn("DB upsert failed for trend weight {}/{}: {}", symbol, entry.getKey(), e.getMessage());
                }
            }

            log.debug("Saved weights for {} to {}", symbol, weightsPath);
        } catch (IOException e) {
            log.error("Error saving weights for {}", symbol, e);
        }
    }

    @Transactional
    private void savePrediction(TrendPrediction prediction) {
        String fileName = "%s/%s_predictions.csv".formatted(
            PREDICTIONS_DIR,
            prediction.getSymbol()
        );
        Path filePath = Paths.get(fileName);
        boolean isNewFile = !Files.exists(filePath);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile(), true))) {
            if (isNewFile) {
                writer.println("Date,OverallTrend,Confidence,%s,%s,%s,%s,%s,%s,%s,%s,%s".formatted(
                    MA_CROSSOVER, RSI, MACD, PRICE_MOMENTUM, VOLUME_TREND, INDEX_MOMENTUM, OPTIONS_SENTIMENT, NEWS_SENTIMENT, ETF_SIGNAL
                ));
            }
            
            writer.println("%s,%s,%.4f,%s,%s,%s,%s,%s,%s,%s,%s,%s".formatted(
                prediction.getPredictionDate().format(DATE_FORMATTER),
                prediction.getOverallTrend(),
                prediction.getConfidence(),
                prediction.getTechniqueResults().get(MA_CROSSOVER),
                prediction.getTechniqueResults().get(RSI),
                prediction.getTechniqueResults().get(MACD),
                prediction.getTechniqueResults().get(PRICE_MOMENTUM),
                prediction.getTechniqueResults().get(VOLUME_TREND),
                prediction.getTechniqueResults().get(INDEX_MOMENTUM),
                prediction.getTechniqueResults().get(OPTIONS_SENTIMENT),
                prediction.getTechniqueResults().get(NEWS_SENTIMENT),
                prediction.getTechniqueResults().get(ETF_SIGNAL)
            ));
            
            log.debug("Saved prediction for {} to {}", prediction.getSymbol(), fileName);
        } catch (IOException e) {
            log.error("Error saving prediction for {}", prediction.getSymbol(), e);
        }

        // Mirror to PostgreSQL
        try {
            Map<String, TrendDirection> results = prediction.getTechniqueResults();
            trendResultRepository.upsertResult(
                prediction.getSymbol(),
                prediction.getPredictionDate(),
                prediction.getOverallTrend().name(),
                prediction.getConfidence(),
                String.valueOf(results.get(MA_CROSSOVER)),
                String.valueOf(results.get(RSI)),
                String.valueOf(results.get(MACD)),
                String.valueOf(results.get(PRICE_MOMENTUM)),
                String.valueOf(results.get(VOLUME_TREND)),
                String.valueOf(results.get(INDEX_MOMENTUM)),
                String.valueOf(results.get(OPTIONS_SENTIMENT)),
                String.valueOf(results.get(NEWS_SENTIMENT)),
                String.valueOf(results.get(ETF_SIGNAL)),
                LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("DB upsert failed for trend prediction {}: {}", prediction.getSymbol(), e.getMessage());
        }
    }
    
    private TrendPrediction createDefaultPrediction(String symbol, Map<String, Double> weights) {
        Map<String, TrendDirection> defaultResults = new HashMap<>();
        defaultResults.put(MA_CROSSOVER, TrendDirection.SIDEWAYS);
        defaultResults.put(RSI, TrendDirection.SIDEWAYS);
        defaultResults.put(MACD, TrendDirection.SIDEWAYS);
        defaultResults.put(PRICE_MOMENTUM, TrendDirection.SIDEWAYS);
        defaultResults.put(VOLUME_TREND, TrendDirection.SIDEWAYS);
        defaultResults.put(INDEX_MOMENTUM, TrendDirection.SIDEWAYS);
        defaultResults.put(OPTIONS_SENTIMENT, TrendDirection.SIDEWAYS);
        defaultResults.put(NEWS_SENTIMENT, TrendDirection.SIDEWAYS);
        defaultResults.put(ETF_SIGNAL, TrendDirection.SIDEWAYS);
        
        return new TrendPrediction(
            symbol,
            LocalDate.now(),
            TrendDirection.SIDEWAYS,
            defaultResults,
            weights,
            0.5
        );
    }
    
    public void updateWeights(String symbol, TrendDirection actualTrend) {
        log.info("Updating weights based on actual trend for {}: {}", symbol, actualTrend);
        
        // Load the latest prediction for this symbol
        TrendPrediction lastPrediction = getLastPrediction(symbol);
        if (lastPrediction == null) {
            log.warn("No previous prediction found for {}", symbol);
            return;
        }
        
        Map<String, Double> weights = loadWeights(symbol);
        
        // Adjust weights based on which techniques got it right for this stock
        for (Map.Entry<String, TrendDirection> entry : lastPrediction.getTechniqueResults().entrySet()) {
            String technique = entry.getKey();
            TrendDirection predicted = entry.getValue();
            double currentWeight = weights.get(technique);
            
            if (predicted == actualTrend) {
                // Increase weight for correct predictions
                weights.put(technique, Math.min(0.5, currentWeight * 1.1));
            } else {
                // Decrease weight for incorrect predictions
                weights.put(technique, Math.max(0.05, currentWeight * 0.9));
            }
        }
        
        // Normalize weights to sum to 1.0
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / totalWeight);
        }
        
        saveWeights(symbol, weights);
        log.info("Updated weights for {} - Techniques now weighted based on performance", symbol);
    }
    
    /**
     * Get the last cached prediction for a symbol without recalculating.
     * This is used for fast portfolio loading.
     */
    public TrendPrediction getLastPrediction(String symbol) {
        String fileName = "%s/%s_predictions.csv".formatted(PREDICTIONS_DIR, symbol);
        Path filePath = Paths.get(fileName);
        
        if (!Files.exists(filePath)) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            String lastLine = null;
            boolean isHeader = true;
            
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                lastLine = line;
            }
            
            if (lastLine != null) {
                String[] parts = lastLine.split(",");
                if (parts.length >= 8) {
                    Map<String, TrendDirection> results = new HashMap<>();
                    results.put(MA_CROSSOVER, parseTrend(parts, 3));
                    results.put(RSI, parseTrend(parts, 4));
                    results.put(MACD, parseTrend(parts, 5));
                    results.put(PRICE_MOMENTUM, parseTrend(parts, 6));
                    results.put(VOLUME_TREND, parseTrend(parts, 7));
                    results.put(INDEX_MOMENTUM, parseTrend(parts, 8));
                    results.put(OPTIONS_SENTIMENT, parseTrend(parts, 9));
                    results.put(NEWS_SENTIMENT, parseTrend(parts, 10));
                    
                    return new TrendPrediction(
                        symbol,
                        LocalDate.parse(parts[0], DATE_FORMATTER),
                        TrendDirection.valueOf(parts[1]),
                        results,
                        loadWeights(symbol),
                        Double.parseDouble(parts[2])
                    );
                }
            }
        } catch (IOException e) {
            log.error("Error reading last prediction for {}", symbol, e);
        }
        
        return null;
    }

    private TrendDirection parseTrend(String[] parts, int index) {
        if (index >= parts.length) {
            return TrendDirection.SIDEWAYS;
        }
        try {
            return TrendDirection.valueOf(parts[index]);
        } catch (Exception e) {
            return TrendDirection.SIDEWAYS;
        }
    }
}
