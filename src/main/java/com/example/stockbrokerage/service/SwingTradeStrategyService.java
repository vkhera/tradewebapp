package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import com.example.stockbrokerage.dto.DailyBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes technical swing-trade signals from daily OHLCV bar data using five strategies:
 * <ol>
 *   <li>RSI(14)                – oversold / overbought via 14-day RSI</li>
 *   <li>MACD(12,26,9)          – momentum crossover via EMA(12)/EMA(26) + signal EMA(9)</li>
 *   <li>Bollinger Bands(20,2)  – mean-reversion via 20-day SMA ± 2σ</li>
 *   <li>EMA Crossover (9/21)   – golden/death cross and trend divergence</li>
 *   <li>Volume Momentum        – breakout above 10-day high with volume confirmation</li>
 * </ol>
 *
 * <p>Each strategy returns a {@link SwingSignal} with:
 * <ul>
 *   <li>{@code strength}       – float in [-1.0, +1.0]; positive = bullish, negative = bearish.</li>
 *   <li>{@code targetReturnPct}– expected percentage gain if the signal proves correct.</li>
 *   <li>{@code holdDays}       – estimated number of trading days to hold.</li>
 *   <li>{@code description}    – human-readable explanation of the signal.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwingTradeStrategyService {

    /** Minimum bars needed for the most data-hungry strategy (EMA26 + signal warmup). */
    private static final int MIN_BARS = 50;

    /** Number of daily bars to request (6-month trading range → ~126 bars). */
    public static final int REQUEST_DAYS = 150;

    public static final String RSI       = "RSI";
    public static final String MACD      = "MACD";
    public static final String BOLLINGER = "BOLLINGER";
    public static final String EMA_CROSS = "EMA_CROSS";
    public static final String MOMENTUM  = "MOMENTUM";

    private final YahooFinanceClient yahooFinanceClient;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns all strategy signals for the given symbol.
     * Fetches daily bars internally; returns an empty list if insufficient data.
     */
    public List<SwingSignal> computeSignals(String symbol) {
        List<DailyBar> bars = yahooFinanceClient.getDailyBars(symbol, REQUEST_DAYS);
        if (bars.size() < MIN_BARS) {
            log.warn("Insufficient daily bars for {} ({} bars, need {})", symbol, bars.size(), MIN_BARS);
            return List.of();
        }

        List<double[]> ohlcv = toDoubleArray(bars); // [open, high, low, close, volume] per bar
        double[] closes  = ohlcv.stream().mapToDouble(r -> r[3]).toArray();
        double[] volumes = ohlcv.stream().mapToDouble(r -> r[4]).toArray();

        List<SwingSignal> signals = new ArrayList<>();
        safeCompute(signals, () -> rsiSignal(closes),          RSI);
        safeCompute(signals, () -> macdSignal(closes),         MACD);
        safeCompute(signals, () -> bollingerSignal(closes),    BOLLINGER);
        safeCompute(signals, () -> emaCrossSignal(closes),     EMA_CROSS);
        safeCompute(signals, () -> momentumSignal(closes, volumes), MOMENTUM);

        return signals;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 1: RSI(14)
    // ──────────────────────────────────────────────────────────────────────────

    SwingSignal rsiSignal(double[] closes) {
        int n = closes.length;
        if (n < 15) return neutral(RSI);

        // Compute RSI over last 14 days using Wilder's smoothed method
        double[] changes = new double[14];
        for (int i = 0; i < 14; i++) {
            changes[i] = closes[n - 14 + i] - closes[n - 15 + i];
        }
        double avgGain = 0, avgLoss = 0;
        for (double d : changes) {
            if (d > 0) avgGain += d;
            else avgLoss += -d;
        }
        avgGain /= 14;
        avgLoss /= 14;

        double rsi = avgLoss == 0 ? 100 : 100 - 100.0 / (1.0 + avgGain / avgLoss);

        if (rsi < 30) {
            double strength = Math.min(1.0, (30.0 - rsi) / 30.0);
            double targetReturn = 5.0 + strength * 8.0;   // 5–13% target
            return new SwingSignal(RSI, strength, targetReturn, 7,
                    "RSI is %.1f (oversold < 30); mean-reversion upside expected.".formatted(rsi));
        } else if (rsi > 70) {
            double strength = -Math.min(1.0, (rsi - 70.0) / 30.0);
            double targetReturn = 4.0 + Math.abs(strength) * 6.0;  // 4–10% avoided decline
            return new SwingSignal(RSI, strength, targetReturn, 7,
                    "RSI is %.1f (overbought > 70); mean-reversion downside expected.".formatted(rsi));
        }

        return neutral(RSI);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 2: MACD(12, 26, 9)
    // ──────────────────────────────────────────────────────────────────────────

    SwingSignal macdSignal(double[] closes) {
        int n = closes.length;
        if (n < 35) return neutral(MACD);

        double[] ema12 = ema(closes, 12);
        double[] ema26 = ema(closes, 26);

        // MACD line
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) {
            macdLine[i] = ema12[i] - ema26[i];
        }

        // Signal line (EMA9 of MACD)
        double[] signalLine = ema(macdLine, 9);

        // Histogram
        double histToday    = macdLine[n - 1] - signalLine[n - 1];
        double histYesterday = macdLine[n - 2] - signalLine[n - 2];

        double currentPrice = closes[n - 1];
        double macdToPrice  = Math.abs(macdLine[n - 1]) / currentPrice * 100;

        if (histYesterday <= 0 && histToday > 0) {
            // Bullish crossover
            double strength = Math.min(0.9, 0.65 + macdToPrice * 2);
            return new SwingSignal(MACD, strength, 5.0 + macdToPrice * 3, 10,
                    "MACD bullish crossover: histogram turned positive (%.4f → %.4f).".formatted(histYesterday, histToday));
        } else if (histYesterday >= 0 && histToday < 0) {
            // Bearish crossover
            double strength = -Math.min(0.9, 0.65 + macdToPrice * 2);
            return new SwingSignal(MACD, strength, 4.0 + macdToPrice * 3, 10,
                    "MACD bearish crossover: histogram turned negative (%.4f → %.4f).".formatted(histYesterday, histToday));
        } else if (histToday > 0) {
            // Mild bullish trend
            return new SwingSignal(MACD, 0.25, 3.0, 7,
                    "MACD histogram positive (%.4f); mild bullish momentum.".formatted(histToday));
        } else if (histToday < 0) {
            // Mild bearish trend
            return new SwingSignal(MACD, -0.25, 3.0, 7,
                    "MACD histogram negative (%.4f); mild bearish momentum.".formatted(histToday));
        }

        return neutral(MACD);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 3: Bollinger Bands (20-day SMA ± 2σ)
    // ──────────────────────────────────────────────────────────────────────────

    SwingSignal bollingerSignal(double[] closes) {
        int n = closes.length;
        if (n < 20) return neutral(BOLLINGER);

        double[] window = java.util.Arrays.copyOfRange(closes, n - 20, n);
        double sma = mean(window);
        double std = std(window, sma);

        double upper = sma + 2 * std;
        double lower = sma - 2 * std;
        double price = closes[n - 1];

        if (std == 0) return neutral(BOLLINGER);

        if (price <= lower) {
            double penetration = (lower - price) / std;
            double strength    = Math.min(1.0, 0.5 + penetration * 0.5);
            double targetReturn = (sma - price) / price * 100;
            return new SwingSignal(BOLLINGER, strength, Math.max(2, targetReturn), 5,
                    "Price ($%.2f) at/below lower Bollinger band ($%.2f); mean-reversion to $%.2f expected.".formatted(price, lower, sma));
        } else if (price >= upper) {
            double penetration = (price - upper) / std;
            double strength    = -Math.min(1.0, 0.5 + penetration * 0.5);
            double targetReturn = (price - sma) / price * 100;
            return new SwingSignal(BOLLINGER, strength, Math.max(2, targetReturn), 5,
                    "Price ($%.2f) at/above upper Bollinger band ($%.2f); mean-reversion to $%.2f expected.".formatted(price, upper, sma));
        } else if (price < sma - 1.0 * std) {
            // Near lower band – mild bullish
            double targetReturn = (sma - price) / price * 100;
            return new SwingSignal(BOLLINGER, 0.3, Math.max(1.5, targetReturn * 0.6), 6,
                    "Price near lower Bollinger band; mild bullish bias.".formatted());
        }

        return neutral(BOLLINGER);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 4: EMA Crossover (9-day / 21-day)
    // ──────────────────────────────────────────────────────────────────────────

    SwingSignal emaCrossSignal(double[] closes) {
        int n = closes.length;
        if (n < 25) return neutral(EMA_CROSS);

        double[] ema9  = ema(closes, 9);
        double[] ema21 = ema(closes, 21);

        double fast     = ema9[n - 1];
        double slow     = ema21[n - 1];
        double fastPrev = ema9[n - 2];
        double slowPrev = ema21[n - 2];

        double gapPct = Math.abs(fast - slow) / slow * 100;

        if (fastPrev <= slowPrev && fast > slow) {
            // Golden cross
            double strength = Math.min(0.9, 0.75 + gapPct);
            return new SwingSignal(EMA_CROSS, strength, 5.0 + gapPct * 2, 10,
                    "EMA(9) just crossed above EMA(21) – golden cross; bullish momentum starting.");
        } else if (fastPrev >= slowPrev && fast < slow) {
            // Death cross
            double strength = -Math.min(0.9, 0.75 + gapPct);
            return new SwingSignal(EMA_CROSS, strength, 4.0 + gapPct * 2, 10,
                    "EMA(9) just crossed below EMA(21) – death cross; bearish momentum starting.");
        } else if (fast > slow && fast > fastPrev && slow > slowPrev) {
            // Bullish trend, both EMAs rising
            double strength = Math.min(0.5, 0.25 + gapPct * 0.1);
            return new SwingSignal(EMA_CROSS, strength, 3.0 + gapPct, 7,
                    "EMA(9) > EMA(21) and both rising; uptrend in progress.");
        } else if (fast < slow && fast < fastPrev && slow < slowPrev) {
            // Bearish trend, both EMAs falling
            double strength = -Math.min(0.5, 0.25 + gapPct * 0.1);
            return new SwingSignal(EMA_CROSS, strength, 3.0 + gapPct, 7,
                    "EMA(9) < EMA(21) and both falling; downtrend in progress.");
        }

        return neutral(EMA_CROSS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 5: Volume Momentum (price breakout + volume confirmation)
    // ──────────────────────────────────────────────────────────────────────────

    SwingSignal momentumSignal(double[] closes, double[] volumes) {
        int n = closes.length;
        if (n < 51) return neutral(MOMENTUM);

        double price = closes[n - 1];

        // 10-day price high (excluding current bar)
        double high10 = 0;
        for (int i = n - 11; i < n - 1; i++) high10 = Math.max(high10, closes[i]);

        // 10-day price low (excluding current bar)
        double low10 = Double.MAX_VALUE;
        for (int i = n - 11; i < n - 1; i++) low10 = Math.min(low10, closes[i]);

        // 50-day SMA
        double sma50 = mean(java.util.Arrays.copyOfRange(closes, n - 50, n));

        // 20-day average volume
        double[] recentVols = java.util.Arrays.copyOfRange(volumes, n - 21, n - 1);
        double avgVolume = mean(recentVols);
        double todayVolume = volumes[n - 1];
        double volumeRatio = avgVolume > 0 ? todayVolume / avgVolume : 1.0;

        boolean aboveSma50   = price > sma50;
        boolean priceBreakout = price > high10;
        boolean priceBreakdown = price < low10;
        boolean volumeSpike  = volumeRatio >= 1.5;

        if (priceBreakout && aboveSma50 && volumeSpike) {
            double returnPct = (price - high10) / high10 * 100 + 5.0;
            return new SwingSignal(MOMENTUM, 0.85, Math.min(15, returnPct), 7,
                    "Price ($%.2f) broke above 10-day high ($%.2f) with %.1fx average volume and is above SMA(50) ($%.2f).".formatted(price, high10, volumeRatio, sma50));
        } else if (priceBreakout && aboveSma50) {
            return new SwingSignal(MOMENTUM, 0.50, 5.0, 5,
                    "Price ($%.2f) broke above 10-day high ($%.2f); above SMA(50) – moderate bullish breakout.".formatted(price, high10));
        } else if (priceBreakdown && !aboveSma50 && volumeSpike) {
            double returnPct = (low10 - price) / low10 * 100 + 4.0;
            return new SwingSignal(MOMENTUM, -0.75, Math.min(12, returnPct), 7,
                    "Price ($%.2f) broke below 10-day low ($%.2f) with %.1fx average volume and is below SMA(50) ($%.2f).".formatted(price, low10, volumeRatio, sma50));
        } else if (priceBreakdown && !aboveSma50) {
            return new SwingSignal(MOMENTUM, -0.40, 4.0, 5,
                    "Price ($%.2f) broke below 10-day low ($%.2f); below SMA(50) – bearish breakdown.".formatted(price, low10));
        }

        return neutral(MOMENTUM);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Technical indicator helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Computes the exponential moving average using the standard multiplier k = 2/(n+1). */
    double[] ema(double[] data, int period) {
        double[] result = new double[data.length];
        double k = 2.0 / (period + 1);
        // Seed with SMA of first `period` values
        double sum = 0;
        for (int i = 0; i < Math.min(period, data.length); i++) sum += data[i];
        result[Math.min(period - 1, data.length - 1)] = sum / Math.min(period, data.length);
        for (int i = period; i < data.length; i++) {
            result[i] = data[i] * k + result[i - 1] * (1 - k);
        }
        return result;
    }

    double mean(double[] data) {
        double sum = 0;
        for (double v : data) sum += v;
        return sum / data.length;
    }

    double std(double[] data, double mean) {
        double variance = 0;
        for (double v : data) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / data.length);
    }

    private SwingSignal neutral(String name) {
        return new SwingSignal(name, 0.0, 0.0, 0, "No significant signal detected.");
    }

    private void safeCompute(List<SwingSignal> signals, java.util.function.Supplier<SwingSignal> fn, String name) {
        try {
            signals.add(fn.get());
        } catch (Exception e) {
            log.warn("Strategy {} threw exception: {}", name, e.getMessage());
            signals.add(neutral(name));
        }
    }

    private List<double[]> toDoubleArray(List<DailyBar> bars) {
        return bars.stream().map(b -> new double[]{
                b.open()   != null ? b.open().doubleValue()   : 0,
                b.high()   != null ? b.high().doubleValue()   : 0,
                b.low()    != null ? b.low().doubleValue()    : 0,
                b.close()  != null ? b.close().doubleValue()  : 0,
                b.volume()
        }).collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Inner record – strategy signal
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Signal produced by a single swing-trade strategy.
     *
     * @param strategyName   Strategy identifier.
     * @param strength       Direction and conviction: -1.0 (strong bearish) to +1.0 (strong bullish).
     * @param targetReturnPct Expected percentage return if the signal proves correct (always positive).
     * @param holdDays        Estimated trading days to hold.
     * @param description     Human-readable rationale.
     */
    public record SwingSignal(
            String strategyName,
            double strength,
            double targetReturnPct,
            int holdDays,
            String description) {

        public boolean isBullish()  { return strength > 0.1; }
        public boolean isBearish()  { return strength < -0.1; }
        public boolean hasSignal()  { return Math.abs(strength) > 0.1; }
    }
}
