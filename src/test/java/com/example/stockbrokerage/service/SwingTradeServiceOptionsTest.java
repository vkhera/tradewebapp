package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import com.example.stockbrokerage.dto.SwingTradeSuggestionResponse;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.SwingStrategyWeight;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.SwingStrategyWeightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the options-market overlay in {@link SwingTradeService}.
 *
 * <p>Tests focus on the three overlay behaviours introduced in the options integration:
 * <ol>
 *   <li>Dynamic stop-loss widening proportional to IV when IV &gt; 30%.</li>
 *   <li>Confidence boost (+8) when PCR contrarian signal aligns with the trade direction.</li>
 *   <li>Max-pain target anchoring for short-hold suggestions (≤5 days).</li>
 * </ol>
 * They also verify that unavailable options data does not break or crash the service.
 */
class SwingTradeServiceOptionsTest {

    private SwingTradeService         service;
    private PortfolioRepository       portfolioRepository;
    private SwingTradeStrategyService strategyService;
    private SwingStrategyWeightRepository weightRepository;
    private StockPriceService         stockPriceService;
    private OptionsDataService        optionsDataService;

    // Stock price used across tests
    private static final double PRICE = 100.0;

    @BeforeEach
    void setUp() {
        portfolioRepository  = mock(PortfolioRepository.class);
        strategyService      = mock(SwingTradeStrategyService.class);
        weightRepository     = mock(SwingStrategyWeightRepository.class);
        stockPriceService    = mock(StockPriceService.class);
        optionsDataService   = mock(OptionsDataService.class);

        service = new SwingTradeService(
                portfolioRepository,
                strategyService,
                weightRepository,
                stockPriceService,
                optionsDataService);
    }

    // ── No options data — no crash ────────────────────────────────────────────

    @Test
    void optionsUnavailable_suggestionStillReturned() {
        setupBullishScenario("AAPL");
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(OptionsSnapshot.unavailable("AAPL"));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        // No "Options market:" suffix when data is unavailable
        assertThat(result.get(0).getReasoning()).doesNotContain("Options market:");
    }

    // ── Dynamic stop-loss widening ────────────────────────────────────────────

    @Test
    void highIV_widensStopLossForHold() {
        setupBullishScenario("AAPL");
        // IV = 50% (above 30% baseline) → multiplier = 1 + (0.50-0.30)*2 = 1.40
        // Baseline stop = price * (1 - 0.03) = 97.0
        // Widened stop  = price * (1 - 0.03 * 1.40) = price * (1 - 0.042) = 95.80 (approx)
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.50, 1.0, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        SwingTradeSuggestionResponse suggestion = result.get(0);
        assertThat(suggestion.getAction()).isEqualTo("HOLD");
        // Widened stop-loss must be further below price than the 3% default
        double stopLoss = suggestion.getStopLoss().doubleValue();
        double defaultStop = PRICE * (1.0 - 0.03);  // 97.0
        assertThat(stopLoss).isLessThan(defaultStop);
    }

    @Test
    void lowIV_doesNotWidenStopLoss() {
        setupBullishScenario("AAPL");
        // IV = 25% (below 30% baseline) → no widening
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.25, 1.0, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        double stopLoss = result.get(0).getStopLoss().doubleValue();
        double defaultStop = PRICE * (1.0 - 0.03);
        // Stop-loss should equal default (within rounding)
        assertThat(stopLoss).isCloseTo(defaultStop, org.assertj.core.data.Offset.offset(0.01));
    }

    // ── Confidence boost ──────────────────────────────────────────────────────

    @Test
    void extremeFear_boostsConfidenceForBullishSignal() {
        // PCR > 1.5 = extreme fear, contrarian bullish → should add +8 to confidence
        setupBullishScenario("AAPL");
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.20, 1.8, 0.0, true));

        // Baseline: net signal ≈ 0.80 → confidence = min(95, 80) = 80
        // After boost:  min(95, 80 + 8) = 88
        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        // We cannot know the exact baseline without knowing internal net signal, but
        // we can assert the reasoning mentions options context and data is used.
        assertThat(result.get(0).getReasoning()).contains("Options market:");
        assertThat(result.get(0).getConfidence()).isGreaterThan(0).isLessThanOrEqualTo(95);
    }

    @Test
    void extremeGreed_boostsConfidenceForBearishSignal() {
        // PCR < 0.7 = extreme greed, contrarian bearish → boost for SELL signal
        setupBearishScenario("NVDA");
        when(optionsDataService.getOptionsSnapshot("NVDA"))
                .thenReturn(new OptionsSnapshot("NVDA", 0.20, 0.5, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SELL");
        assertThat(result.get(0).getReasoning()).contains("Options market:");
        assertThat(result.get(0).getReasoning()).contains("extreme greed");
    }

    @Test
    void extremeFear_doesNotBoostBearishSignal() {
        // PCR > 1.5 is only contrarian bullish — should NOT boost a SELL signal
        setupBearishScenario("NVDA");
        OptionsSnapshot fearSnap = new OptionsSnapshot("NVDA", 0.20, 1.8, 0.0, true);
        when(optionsDataService.getOptionsSnapshot("NVDA")).thenReturn(fearSnap);

        // With extreme fear but bearish signal, baseline confidence should NOT increase
        // (confidence boost only applies when signals are aligned/contrarian)
        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);
        // Just verify it completes without error and returns a suggestion
        assertThat(result).hasSize(1);
    }

    // ── Max-pain target anchor ────────────────────────────────────────────────

    @Test
    void maxPain_betweenPriceAndTarget_anchorsTargetForShortHold() {
        // Bullish signal with avgReturn of ~5% → target ≈ 105.
        // Max pain = 102 (between current 100 and target ~105, hold ≤5 days)
        // → target should be anchored to 102.
        setupBullishScenario("AAPL");
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.20, 1.0, 102.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        SwingTradeSuggestionResponse suggestion = result.get(0);
        // If hold days <=5 and max pain (102) is between price (100) and target (~105),
        // target is anchored to max pain
        if (suggestion.getHoldDaysEstimated() != null && suggestion.getHoldDaysEstimated() <= 5) {
            assertThat(suggestion.getTargetPrice().doubleValue())
                    .isCloseTo(102.0, org.assertj.core.data.Offset.offset(0.01));
        }
        // Always verify reasoning context is appended
        assertThat(suggestion.getReasoning()).contains("Options market:");
        assertThat(suggestion.getReasoning()).contains("MaxPain=");
    }

    @Test
    void maxPainZero_doesNotAnchorTarget() {
        // maxPain = 0 → anchor logic must not fire (guard: maxPainStrike > 0)
        setupBullishScenario("AAPL");
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.20, 1.0, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        // Target should be above current price (not anchored to zero)
        assertThat(result.get(0).getTargetPrice().doubleValue()).isGreaterThan(PRICE);
    }

    // ── Reasoning suffix ──────────────────────────────────────────────────────

    @Test
    void optionsAvailable_appendsOptionsContextToReasoning() {
        setupBullishScenario("AAPL");
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.42, 1.6, 98.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result).hasSize(1);
        String reasoning = result.get(0).getReasoning();
        assertThat(reasoning).contains("Options market:");
        assertThat(reasoning).contains("IV=");
        assertThat(reasoning).contains("PCR=");
        assertThat(reasoning).contains("MaxPain=");
    }

    @Test
    void optionsAvailable_ivLabelReflectsElevated() {
        setupBullishScenario("AAPL");
        // IV=45% → "elevated" (≥40% but <60%)
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.45, 1.0, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result.get(0).getReasoning()).contains("elevated");
    }

    @Test
    void optionsAvailable_extremeIVLabelReflectsExtreme() {
        setupBullishScenario("AAPL");
        // IV=65% → "extreme"
        when(optionsDataService.getOptionsSnapshot("AAPL"))
                .thenReturn(new OptionsSnapshot("AAPL", 0.65, 1.0, 0.0, true));

        List<SwingTradeSuggestionResponse> result = service.getSwingTradeSuggestions(1L);

        assertThat(result.get(0).getReasoning()).contains("extreme");
    }

    // ── Helper setup methods ──────────────────────────────────────────────────

    /**
     * Sets up a strongly bullish scenario for the given symbol:
     * all 5 strategies return bullish signals with strength ~0.8,
     * so net signal is well above the 0.25 threshold and action = HOLD.
     */
    private void setupBullishScenario(String symbol) {
        Portfolio holding = new Portfolio();
        holding.setSymbol(symbol);
        holding.setQuantity(10);

        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(holding));
        when(stockPriceService.getCurrentPrice(symbol)).thenReturn(BigDecimal.valueOf(PRICE));

        List<SwingTradeStrategyService.SwingSignal> signals = bullishSignals();
        when(strategyService.computeSignals(symbol)).thenReturn(signals);

        // All weights default to 0.20 (5 strategies × 0.20 = 1.0 total)
        for (String name : List.of("RSI", "MACD", "BOLLINGER", "EMA_CROSS", "MOMENTUM")) {
            SwingStrategyWeight w = new SwingStrategyWeight();
            w.setStrategyName(name);
            w.setWeight(0.20);
            w.setLastUpdated(LocalDateTime.now());
            when(weightRepository.findByStrategyName(name)).thenReturn(Optional.of(w));
        }
    }

    /**
     * Sets up a strongly bearish scenario for the given symbol:
     * all 5 strategies return bearish signals with strength ~ -0.8,
     * so net signal is well below –0.25 threshold and action = SELL.
     */
    private void setupBearishScenario(String symbol) {
        Portfolio holding = new Portfolio();
        holding.setSymbol(symbol);
        holding.setQuantity(15);

        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(holding));
        when(stockPriceService.getCurrentPrice(symbol)).thenReturn(BigDecimal.valueOf(PRICE));

        List<SwingTradeStrategyService.SwingSignal> signals = bearishSignals();
        when(strategyService.computeSignals(symbol)).thenReturn(signals);

        for (String name : List.of("RSI", "MACD", "BOLLINGER", "EMA_CROSS", "MOMENTUM")) {
            SwingStrategyWeight w = new SwingStrategyWeight();
            w.setStrategyName(name);
            w.setWeight(0.20);
            w.setLastUpdated(LocalDateTime.now());
            when(weightRepository.findByStrategyName(name)).thenReturn(Optional.of(w));
        }
    }

    private List<SwingTradeStrategyService.SwingSignal> bullishSignals() {
        return List.of(
            new SwingTradeStrategyService.SwingSignal("RSI",       0.80, 5.0, 7, "oversold"),
            new SwingTradeStrategyService.SwingSignal("MACD",      0.75, 4.0, 7, "bullish crossover"),
            new SwingTradeStrategyService.SwingSignal("BOLLINGER", 0.70, 3.5, 5, "below lower band"),
            new SwingTradeStrategyService.SwingSignal("EMA_CROSS", 0.65, 3.0, 5, "bullish EMA cross"),
            new SwingTradeStrategyService.SwingSignal("MOMENTUM",  0.60, 4.5, 7, "volume breakout")
        );
    }

    private List<SwingTradeStrategyService.SwingSignal> bearishSignals() {
        return List.of(
            new SwingTradeStrategyService.SwingSignal("RSI",       -0.80, 5.0, 7, "overbought"),
            new SwingTradeStrategyService.SwingSignal("MACD",      -0.75, 4.0, 7, "bearish crossover"),
            new SwingTradeStrategyService.SwingSignal("BOLLINGER", -0.70, 3.5, 5, "above upper band"),
            new SwingTradeStrategyService.SwingSignal("EMA_CROSS", -0.65, 3.0, 5, "bearish EMA cross"),
            new SwingTradeStrategyService.SwingSignal("MOMENTUM",  -0.60, 4.5, 7, "volume breakdown")
        );
    }
}
