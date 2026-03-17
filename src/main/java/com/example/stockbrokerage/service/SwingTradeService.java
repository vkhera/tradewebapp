package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import com.example.stockbrokerage.dto.SwingTradeSuggestionResponse;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.SwingStrategyWeight;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.SwingStrategyWeightRepository;
import com.example.stockbrokerage.service.SwingTradeStrategyService.SwingSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates multi-day swing trade suggestions for a client's held stocks.
 *
 * <p>For each holding the service:
 * <ol>
 *   <li>Computes per-strategy signals via {@link SwingTradeStrategyService}.</li>
 *   <li>Looks up (or initialises) adaptive strategy weights from the database.</li>
 *   <li>Calculates a weighted net signal in [-1, +1].</li>
 *   <li>Emits a suggestion only when |netSignal| ≥ 0.25 (moderate conviction).</li>
 *   <li>Derives action (HOLD or SELL), target price, stop-loss and confidence.</li>
 *   <li>Returns up to 5 suggestions ordered by highest predicted return.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwingTradeService {

    private static final int    MAX_SUGGESTIONS     = 5;
    private static final double SIGNAL_THRESHOLD    = 0.25;  // minimum |netSignal| to emit
    private static final double INITIAL_WEIGHT      = 0.20;  // equal start weight for 5 strategies
    private static final double STOP_LOSS_HOLD_PCT  = 0.03;  // 3% below entry for HOLD
    private static final double STOP_LOSS_SELL_PCT  = 0.02;  // 2% above re-entry for SELL

    private static final ZoneId EST = ZoneId.of("America/New_York");

    /** Ordered list of all strategy names – used when initialising default weights. */
    private static final List<String> STRATEGY_NAMES = List.of(
            SwingTradeStrategyService.RSI,
            SwingTradeStrategyService.MACD,
            SwingTradeStrategyService.BOLLINGER,
            SwingTradeStrategyService.EMA_CROSS,
            SwingTradeStrategyService.MOMENTUM);

    private final PortfolioRepository        portfolioRepository;
    private final SwingTradeStrategyService  strategyService;
    private final SwingStrategyWeightRepository weightRepository;
    private final StockPriceService          stockPriceService;
    private final OptionsDataService         optionsDataService;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns up to {@value #MAX_SUGGESTIONS} swing trade suggestions for the given client,
     * ordered by {@code predictedReturnPct} descending (highest potential return first).
     */
    public List<SwingTradeSuggestionResponse> getSwingTradeSuggestions(Long clientId) {
        List<Portfolio> holdings = portfolioRepository.findByClientId(clientId);
        if (holdings.isEmpty()) {
            log.debug("No holdings for client {} – skipping swing analysis", clientId);
            return List.of();
        }

        Map<String, Double> weights = loadWeights();
        List<SwingTradeSuggestionResponse> candidates = new ArrayList<>();

        for (Portfolio holding : holdings) {
            try {
                SwingTradeSuggestionResponse suggestion = evaluate(holding, weights);
                if (suggestion != null) {
                    candidates.add(suggestion);
                }
            } catch (Exception e) {
                log.warn("Swing analysis failed for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }

        // Sort by predictedReturnPct descending; take top MAX_SUGGESTIONS
        candidates.sort(Comparator.comparing(SwingTradeSuggestionResponse::getPredictedReturnPct).reversed());
        return candidates.size() > MAX_SUGGESTIONS
                ? candidates.subList(0, MAX_SUGGESTIONS)
                : candidates;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private SwingTradeSuggestionResponse evaluate(Portfolio holding, Map<String, Double> weights) {
        String symbol = holding.getSymbol();
        int qty = holding.getQuantity() != null ? holding.getQuantity() : 0;

        BigDecimal currentPrice = stockPriceService.getCurrentPrice(symbol);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No current price for {} – skipping", symbol);
            return null;
        }
        double price = currentPrice.doubleValue();

        List<SwingSignal> signals = strategyService.computeSignals(symbol);
        if (signals.isEmpty()) {
            log.debug("No swing signals for {} (insufficient data)", symbol);
            return null;
        }

        // Weighted net signal
        double netSignal       = 0;
        double weightedReturn  = 0;
        double weightTotal     = 0;
        double weightedHoldDays = 0;

        List<String> strongStrategies = new ArrayList<>();

        for (SwingSignal sig : signals) {
            double w = weights.getOrDefault(sig.strategyName(), INITIAL_WEIGHT);
            netSignal += sig.strength() * w;
            if (sig.hasSignal() && sig.targetReturnPct() > 0) {
                weightedReturn   += sig.targetReturnPct() * w * Math.abs(sig.strength());
                weightedHoldDays += sig.holdDays()        * w * Math.abs(sig.strength());
                weightTotal += w * Math.abs(sig.strength());
            }
            if (Math.abs(sig.strength()) >= 0.4) {
                strongStrategies.add(sig.strategyName());
            }
        }

        if (Math.abs(netSignal) < SIGNAL_THRESHOLD) {
            return null;  // insufficient conviction
        }

        boolean bullish = netSignal > 0;
        String action   = bullish ? "HOLD" : "SELL";

        double avgReturn  = weightTotal > 0 ? weightedReturn  / weightTotal : 3.0;
        double avgHoldDays = weightTotal > 0 ? weightedHoldDays / weightTotal : 7.0;
        int holdDays = Math.max(3, (int) Math.round(avgHoldDays));

        // Target and stop-loss prices
        double targetPriceVal;
        double stopLossVal;
        if (bullish) {
            targetPriceVal = price * (1.0 + avgReturn / 100.0);
            stopLossVal    = price * (1.0 - STOP_LOSS_HOLD_PCT);
        } else {
            // SELL: re-entry target is expected lower price; stop = slightly above re-entry
            targetPriceVal = price * (1.0 - avgReturn / 100.0);
            stopLossVal    = targetPriceVal * (1.0 + STOP_LOSS_SELL_PCT);
        }

        int confidence = Math.min(95, (int) Math.round(Math.abs(netSignal) * 100));
        String topStrategies = strongStrategies.isEmpty()
                ? signals.stream().max(Comparator.comparingDouble(s -> Math.abs(s.strength())))
                         .map(SwingSignal::strategyName).orElse("")
                : String.join(", ", strongStrategies);

        String reasoning = buildReasoning(symbol, bullish, netSignal, avgReturn, holdDays, signals);

        // ── Options market overlay ──────────────────────────────────────────────
        // Adjusts stop-loss width (IV), confidence (PCR alignment), target (max pain)
        // and appends a brief options context to the reasoning string.
        OptionsSnapshot opts = optionsDataService.getOptionsSnapshot(symbol);
        if (opts.dataAvailable()) {
            double iv         = opts.atmImpliedVolatility();
            double pcr        = opts.putCallRatioOI();
            double maxPainStrike = opts.maxPain();

            // 1. Dynamic stop-loss: widen proportionally when IV exceeds a 30% baseline.
            //    e.g. IV=50% → multiplier=1.4; IV=70% → multiplier=1.8.
            if (iv > 0.30) {
                double ivMultiplier = 1.0 + (iv - 0.30) * 2.0;
                stopLossVal = bullish
                        ? price * (1.0 - STOP_LOSS_HOLD_PCT * ivMultiplier)
                        : targetPriceVal * (1.0 + STOP_LOSS_SELL_PCT * ivMultiplier);
            }

            // 2. Confidence boost when PCR contrarian signal aligns with the technical direction.
            //    PCR>1.5 (extreme fear) is contrarian bullish; PCR<0.7 (greed) is contrarian bearish.
            if ((bullish && opts.isExtremeFear()) || (!bullish && opts.isExtremeGreed())) {
                confidence = Math.min(95, confidence + 8);
            }

            // 3. Max pain target anchor for short-duration suggestions (≤5 days).
            //    If max pain sits between current price and the computed target, snap
            //    the target to max pain — it acts as a nearer-term price magnet.
            if (holdDays <= 5 && maxPainStrike > 0) {
                if (bullish && maxPainStrike > price && maxPainStrike < targetPriceVal) {
                    targetPriceVal = maxPainStrike;
                } else if (!bullish && maxPainStrike < price && maxPainStrike > targetPriceVal) {
                    targetPriceVal = maxPainStrike;
                }
            }

            // 4. Append options context to reasoning.
            String pcrLabel = opts.isExtremeFear()  ? "extreme fear"
                            : opts.isExtremeGreed() ? "extreme greed"
                            :                          "neutral";
            String ivLabel  = opts.isExtremeIV() ? "extreme" : opts.isHighIV() ? "elevated" : "normal";
            reasoning += " Options market: IV=%.0f%% (%s), PCR=%.2f (%s)%s.".formatted(
                    iv * 100, ivLabel, pcr, pcrLabel,
                    maxPainStrike > 0 ? "; MaxPain=$%.2f".formatted(maxPainStrike) : "");
        }
        // ── End options overlay ─────────────────────────────────────────────────

        String strategySignalsJson = buildStrategySignalsJson(signals);
        LocalDateTime suggestedDate = ZonedDateTime.now(EST).toLocalDateTime();

        return SwingTradeSuggestionResponse.builder()
                .symbol(symbol)
                .quantity(qty)
                .currentPrice(bd(price))
                .action(action)
                .targetPrice(bd(targetPriceVal))
                .stopLoss(bd(stopLossVal))
                .predictedReturnPct(bd(avgReturn).setScale(2, RoundingMode.HALF_UP))
                .holdDaysEstimated(holdDays)
                .confidence(confidence)
                .topStrategies(topStrategies)
                .reasoning(reasoning)
                .suggestedDate(suggestedDate)
                .status("PENDING")
                .build();
    }

    private String buildReasoning(String symbol, boolean bullish, double netSignal,
                                  double returnPct, int holdDays, List<SwingSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(": ");
        sb.append(bullish ? "Bullish" : "Bearish").append(" swing signal (net strength ");
        sb.append("%.2f".formatted(Math.abs(netSignal))).append("). ");
        sb.append("Expected %.1f%%".formatted(returnPct));
        sb.append(bullish ? " upside" : " decline").append(" over ~").append(holdDays).append(" days. ");
        sb.append("Contributing strategies: ");
        sb.append(signals.stream()
                .filter(SwingSignal::hasSignal)
                .map(s -> "%s (%.2f)".formatted(s.strategyName(), s.strength()))
                .collect(Collectors.joining(", ")));
        sb.append(".");
        return sb.toString();
    }

    private String buildStrategySignalsJson(List<SwingSignal> signals) {
        // Simple JSON serialization without external library
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < signals.size(); i++) {
            SwingSignal s = signals.get(i);
            sb.append("{\"strategy\":\"").append(s.strategyName()).append("\"")
              .append(",\"strength\":").append("%.4f".formatted(s.strength()))
              .append(",\"targetReturn\":").append("%.2f".formatted(s.targetReturnPct()))
              .append(",\"holdDays\":").append(s.holdDays())
              .append("}");
            if (i < signals.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Loads strategy weights from DB.
     * If any weight is missing, initialises defaults (0.20 each) and persists them.
     */
    private Map<String, Double> loadWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        LocalDateTime now = ZonedDateTime.now(EST).toLocalDateTime();

        for (String name : STRATEGY_NAMES) {
            Optional<SwingStrategyWeight> opt = weightRepository.findByStrategyName(name);
            if (opt.isPresent()) {
                weights.put(name, opt.get().getWeight());
            } else {
                // Initialise default weight
                SwingStrategyWeight newWeight = SwingStrategyWeight.builder()
                        .strategyName(name)
                        .weight(INITIAL_WEIGHT)
                        .winCount(0)
                        .lossCount(0)
                        .lastUpdated(now)
                        .build();
                weightRepository.save(newWeight);
                weights.put(name, INITIAL_WEIGHT);
                log.info("Initialised default weight for strategy {} = {}", name, INITIAL_WEIGHT);
            }
        }
        return weights;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
