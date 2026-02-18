package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.TrendPrediction;
import com.example.stockbrokerage.dto.TrendPrediction.TrendDirection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalysisService {
    
    private static final String PREDICTIONS_DIR = "trend_predictions";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final StockPriceService stockPriceService;
    
    // Technique names
    private static final String MA_CROSSOVER = "MA_Crossover";
    private static final String RSI = "RSI";
    private static final String MACD = "MACD";
    private static final String PRICE_MOMENTUM = "Price_Momentum";
    private static final String VOLUME_TREND = "Volume_Trend";
    
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
            
            techniqueResults.put(MA_CROSSOVER, calculateMACrossover(prices));
            techniqueResults.put(RSI, calculateRSI(prices));
            techniqueResults.put(MACD, calculateMACD(prices));
            techniqueResults.put(PRICE_MOMENTUM, calculatePriceMomentum(prices));
            techniqueResults.put(VOLUME_TREND, calculateVolumeTrend(volumes));
            
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
        
        double maxScore = scores.values().stream().max(Double::compare).orElse(0.0);
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
            // Initialize with equal weights for this stock
            weights.put(MA_CROSSOVER, 0.2);
            weights.put(RSI, 0.2);
            weights.put(MACD, 0.2);
            weights.put(PRICE_MOMENTUM, 0.2);
            weights.put(VOLUME_TREND, 0.2);
            saveWeights(symbol, weights);
            log.info("Initialized default weights for {}", symbol);
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
        
        return weights;
    }
    
    private void saveWeights(String symbol, Map<String, Double> weights) {
        String weightsFile = "%s/%s_weights.csv".formatted(PREDICTIONS_DIR, symbol);
        Path weightsPath = Paths.get(weightsFile);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(weightsPath.toFile()))) {
            writer.println("Technique,Weight,LastUpdated");
            
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                writer.println("%s,%.4f,%s".formatted(
                    entry.getKey(),
                    entry.getValue(),
                    LocalDate.now().format(DATE_FORMATTER)
                ));
            }
            
            log.debug("Saved weights for {} to {}", symbol, weightsPath);
        } catch (IOException e) {
            log.error("Error saving weights for {}", symbol, e);
        }
    }
    
    private void savePrediction(TrendPrediction prediction) {
        String fileName = "%s/%s_predictions.csv".formatted(
            PREDICTIONS_DIR,
            prediction.getSymbol()
        );
        Path filePath = Paths.get(fileName);
        boolean isNewFile = !Files.exists(filePath);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile(), true))) {
            if (isNewFile) {
                writer.println("Date,OverallTrend,Confidence,%s,%s,%s,%s,%s".formatted(
                    MA_CROSSOVER, RSI, MACD, PRICE_MOMENTUM, VOLUME_TREND
                ));
            }
            
            writer.println("%s,%s,%.4f,%s,%s,%s,%s,%s".formatted(
                prediction.getPredictionDate().format(DATE_FORMATTER),
                prediction.getOverallTrend(),
                prediction.getConfidence(),
                prediction.getTechniqueResults().get(MA_CROSSOVER),
                prediction.getTechniqueResults().get(RSI),
                prediction.getTechniqueResults().get(MACD),
                prediction.getTechniqueResults().get(PRICE_MOMENTUM),
                prediction.getTechniqueResults().get(VOLUME_TREND)
            ));
            
            log.debug("Saved prediction for {} to {}", prediction.getSymbol(), fileName);
        } catch (IOException e) {
            log.error("Error saving prediction for {}", prediction.getSymbol(), e);
        }
    }
    
    private TrendPrediction createDefaultPrediction(String symbol, Map<String, Double> weights) {
        Map<String, TrendDirection> defaultResults = new HashMap<>();
        defaultResults.put(MA_CROSSOVER, TrendDirection.SIDEWAYS);
        defaultResults.put(RSI, TrendDirection.SIDEWAYS);
        defaultResults.put(MACD, TrendDirection.SIDEWAYS);
        defaultResults.put(PRICE_MOMENTUM, TrendDirection.SIDEWAYS);
        defaultResults.put(VOLUME_TREND, TrendDirection.SIDEWAYS);
        
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
                    results.put(MA_CROSSOVER, TrendDirection.valueOf(parts[3]));
                    results.put(RSI, TrendDirection.valueOf(parts[4]));
                    results.put(MACD, TrendDirection.valueOf(parts[5]));
                    results.put(PRICE_MOMENTUM, TrendDirection.valueOf(parts[6]));
                    results.put(VOLUME_TREND, TrendDirection.valueOf(parts[7]));
                    
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
}
