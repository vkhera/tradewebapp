package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import com.example.stockbrokerage.dto.DailyBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SwingTradeStrategyService}.
 */
class SwingTradeStrategyServiceTest {

    private SwingTradeStrategyService service;
    private YahooFinanceClient yahooFinanceClient;

    @BeforeEach
    void setUp() {
        yahooFinanceClient = mock(YahooFinanceClient.class);
        service = new SwingTradeStrategyService(yahooFinanceClient);
    }

    // ── computeSignals ────────────────────────────────────────────────────────

    @Test
    void computeSignals_insufficientBars_returnsEmptyList() {
        when(yahooFinanceClient.getDailyBars(eq("AAPL"), anyInt()))
                .thenReturn(flatBars(10, 100.0));

        List<SwingTradeStrategyService.SwingSignal> signals = service.computeSignals("AAPL");

        assertThat(signals).isEmpty();
    }

    @Test
    void computeSignals_sufficientBars_returnsFiveSignals() {
        // 100 bars of slowly rising prices — all strategies return a signal
        when(yahooFinanceClient.getDailyBars(eq("AAPL"), anyInt()))
                .thenReturn(risingBars(100, 100.0, 0.5));

        List<SwingTradeStrategyService.SwingSignal> signals = service.computeSignals("AAPL");

        assertThat(signals).hasSize(5);
        assertThat(signals).extracting(SwingTradeStrategyService.SwingSignal::strategyName)
                .containsExactlyInAnyOrder(
                        SwingTradeStrategyService.RSI,
                        SwingTradeStrategyService.MACD,
                        SwingTradeStrategyService.BOLLINGER,
                        SwingTradeStrategyService.EMA_CROSS,
                        SwingTradeStrategyService.MOMENTUM);
    }

    @Test
    void computeSignals_delegatesToYahooClient() {
        when(yahooFinanceClient.getDailyBars("TSLA", SwingTradeStrategyService.REQUEST_DAYS))
                .thenReturn(flatBars(10, 50.0));

        service.computeSignals("TSLA");

        verify(yahooFinanceClient).getDailyBars("TSLA", SwingTradeStrategyService.REQUEST_DAYS);
    }

    // ── RSI signal ────────────────────────────────────────────────────────────

    @Test
    void rsiSignal_oversoldStock_returnsBullishSignal() {
        // Construct closes where last 14 changes are all losses → RSI < 30
        double[] closes = oversoldCloses(60);

        SwingTradeStrategyService.SwingSignal signal = service.rsiSignal(closes);

        assertThat(signal.strength()).isGreaterThan(0.0);
        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.RSI);
        assertThat(signal.isBullish()).isTrue();
        assertThat(signal.description()).contains("oversold");
    }

    @Test
    void rsiSignal_overboughtStock_returnsBearishSignal() {
        // Construct closes where last 14 changes are all gains → RSI > 70
        double[] closes = overboughtCloses(60);

        SwingTradeStrategyService.SwingSignal signal = service.rsiSignal(closes);

        assertThat(signal.strength()).isLessThan(0.0);
        assertThat(signal.isBearish()).isTrue();
        assertThat(signal.description()).contains("overbought");
    }

    @Test
    void rsiSignal_neutralStock_returnsNeutralSignal() {
        // Mix of equal gains and losses → RSI ≈ 50
        double[] closes = neutralCloses(60);

        SwingTradeStrategyService.SwingSignal signal = service.rsiSignal(closes);

        assertThat(signal.hasSignal()).isFalse();
        assertThat(signal.strength()).isZero();
    }

    // ── MACD signal ───────────────────────────────────────────────────────────

    @Test
    void macdSignal_tooFewBars_returnsNeutral() {
        double[] closes = flatDoubles(20, 100.0);

        SwingTradeStrategyService.SwingSignal signal = service.macdSignal(closes);

        assertThat(signal.hasSignal()).isFalse();
    }

    @Test
    void macdSignal_sufficientBars_returnsSignal() {
        // 80 bars of rising prices creates upward MACD
        double[] closes = risingDoubles(80, 50.0, 0.5);

        SwingTradeStrategyService.SwingSignal signal = service.macdSignal(closes);

        // Rising prices → EMA12 > EMA26 → bullish MACD
        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.MACD);
    }

    // ── Bollinger Bands signal ────────────────────────────────────────────────

    @Test
    void bollingerSignal_priceBelowLowerBand_returnsBullishSignal() {
        // 50 stable bars then a sharp drop: last close well below lower band
        double[] closes = priceDive(80);

        SwingTradeStrategyService.SwingSignal signal = service.bollingerSignal(closes);

        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.BOLLINGER);
        assertThat(signal.isBullish()).isTrue();
    }

    @Test
    void bollingerSignal_priceAboveUpperBand_returnsBearishSignal() {
        // 50 stable bars then a sharp spike: last close well above upper band
        double[] closes = priceSpike(80);

        SwingTradeStrategyService.SwingSignal signal = service.bollingerSignal(closes);

        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.BOLLINGER);
        assertThat(signal.isBearish()).isTrue();
    }

    // ── EMA Crossover signal ──────────────────────────────────────────────────

    @Test
    void emaCrossSignal_tooFewBars_returnsNeutral() {
        double[] closes = flatDoubles(15, 100.0);

        SwingTradeStrategyService.SwingSignal signal = service.emaCrossSignal(closes);

        assertThat(signal.hasSignal()).isFalse();
    }

    @Test
    void emaCrossSignal_bullishTrend_returnsPositiveStrength() {
        // Sustained rising prices → EMA9 > EMA21
        double[] closes = risingDoubles(80, 50.0, 1.0);

        SwingTradeStrategyService.SwingSignal signal = service.emaCrossSignal(closes);

        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.EMA_CROSS);
        assertThat(signal.strength()).isGreaterThan(0);
    }

    // ── Momentum (Volume) signal ──────────────────────────────────────────────

    @Test
    void momentumSignal_priceBreaksout_withHighVolume_returnsBullish() {
        // Simulate: 60 stable bars (base), then last close above 10-day high with spike volume
        int n = 70;
        double[] closes = new double[n];
        double[] volumes = new double[n];

        // Flat base
        for (int i = 0; i < n - 1; i++) {
            closes[i] = 100.0;
            volumes[i] = 1_000_000;
        }
        // Breakout bar: price 5% above range and 2× avg volume
        closes[n - 1] = 110.0;
        volumes[n - 1] = 2_500_000;

        SwingTradeStrategyService.SwingSignal signal = service.momentumSignal(closes, volumes);

        assertThat(signal.strategyName()).isEqualTo(SwingTradeStrategyService.MOMENTUM);
        assertThat(signal.strength()).isGreaterThan(0);
    }

    // ── SwingSignal helpers ──────────────────────────────────────────────────

    @Test
    void swingSignal_isBullish_whenStrengthPositive() {
        SwingTradeStrategyService.SwingSignal s = new SwingTradeStrategyService.SwingSignal("TEST", 0.5, 5.0, 7, "bullish");
        assertThat(s.isBullish()).isTrue();
        assertThat(s.isBearish()).isFalse();
        assertThat(s.hasSignal()).isTrue();
    }

    @Test
    void swingSignal_isBearish_whenStrengthNegative() {
        SwingTradeStrategyService.SwingSignal s = new SwingTradeStrategyService.SwingSignal("TEST", -0.5, 5.0, 7, "bearish");
        assertThat(s.isBearish()).isTrue();
        assertThat(s.isBullish()).isFalse();
        assertThat(s.hasSignal()).isTrue();
    }

    @Test
    void swingSignal_hasSignal_falseWhenZero() {
        SwingTradeStrategyService.SwingSignal s = new SwingTradeStrategyService.SwingSignal("TEST", 0.0, 0.0, 0, "neutral");
        assertThat(s.hasSignal()).isFalse();
    }

    // ── Test data helpers ─────────────────────────────────────────────────────

    /** n flat bars all at the given close price. */
    private List<DailyBar> flatBars(int n, double closePrice) {
        List<DailyBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        BigDecimal c = BigDecimal.valueOf(closePrice);
        for (int i = 0; i < n; i++) {
            bars.add(new DailyBar(date.plusDays(i), c, c, c, c, 1_000_000));
        }
        return bars;
    }

    /** n bars rising by stepPerBar each day, starting at startPrice. */
    private List<DailyBar> risingBars(int n, double startPrice, double stepPerBar) {
        List<DailyBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < n; i++) {
            BigDecimal c = BigDecimal.valueOf(startPrice + i * stepPerBar);
            bars.add(new DailyBar(date.plusDays(i), c, c.add(BigDecimal.ONE), c, c, 1_000_000 + i * 10_000));
        }
        return bars;
    }

    /** n flat doubles for strategy unit testing. */
    private double[] flatDoubles(int n, double value) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = value;
        return arr;
    }

    /** n rising doubles: startPrice + i * step. */
    private double[] risingDoubles(int n, double startPrice, double step) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = startPrice + i * step;
        return arr;
    }

    /**
     * 60 bars with dramatic recent losses to produce RSI < 30.
     * First 46 bars flat, then 14 big drops.
     */
    private double[] oversoldCloses(int n) {
        double[] closes = new double[n];
        // Flat warm-up
        for (int i = 0; i < n - 14; i++) closes[i] = 100.0;
        // 14 days of steep falls
        for (int i = 0; i < 14; i++) {
            closes[n - 14 + i] = 100.0 - (i + 1) * 5.0; // -5, -10, ... -70
        }
        return closes;
    }

    /**
     * 60 bars with dramatic recent gains to produce RSI > 70.
     * First 46 bars flat, then 14 big rises.
     */
    private double[] overboughtCloses(int n) {
        double[] closes = new double[n];
        for (int i = 0; i < n - 14; i++) closes[i] = 100.0;
        for (int i = 0; i < 14; i++) {
            closes[n - 14 + i] = 100.0 + (i + 1) * 5.0; // +5, +10, ... +70
        }
        return closes;
    }

    /**
     * 60 bars alternating +1 and -1 changes to produce an RSI near 50.
     */
    private double[] neutralCloses(int n) {
        double[] closes = new double[n];
        closes[0] = 100.0;
        for (int i = 1; i < n; i++) {
            closes[i] = closes[i - 1] + (i % 2 == 0 ? 1.0 : -1.0);
        }
        return closes;
    }

    /**
     * 80 bars: first 60 stable at 100, then a slow drift down and a final hard drop
     * to force the last close well below the Bollinger lower band.
     */
    private double[] priceDive(int n) {
        double[] closes = new double[n];
        for (int i = 0; i < n - 5; i++) closes[i] = 100.0;
        closes[n - 5] = 95.0;
        closes[n - 4] = 90.0;
        closes[n - 3] = 85.0;
        closes[n - 2] = 80.0;
        closes[n - 1] = 60.0; // way below lower band (SMA20 ~99, std ~7 → lower ~85)
        return closes;
    }

    /**
     * 80 bars: first 75 stable at 100, then a sharp spike to force the close
     * well above the Bollinger upper band.
     */
    private double[] priceSpike(int n) {
        double[] closes = new double[n];
        for (int i = 0; i < n - 5; i++) closes[i] = 100.0;
        closes[n - 5] = 105.0;
        closes[n - 4] = 110.0;
        closes[n - 3] = 115.0;
        closes[n - 2] = 120.0;
        closes[n - 1] = 145.0; // way above upper band (SMA20 ~103, std ~10 → upper ~123)
        return closes;
    }
}

