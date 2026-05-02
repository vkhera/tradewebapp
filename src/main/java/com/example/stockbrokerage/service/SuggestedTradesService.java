package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.NewsSentimentDto;
import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.entity.NewsSentimentAnalysis;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.StockPricePrediction;
import com.example.stockbrokerage.repository.NewsSentimentAnalysisRepository;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.StockPricePredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Analyses the client's portfolio holdings and generates up to 5 suggested trades.
 *
 * A trade is suggested when a stock is expected to drop more than 2 % in the next 8 hours,
 * or when its ATR(14) implies a daily move of more than 2 %. The service recommends:
 *   - Sell price  →  current market price (take profit / cut loss now)
 *   - Buy-back    →  current price minus one ATR(14) (re-enter after the dip)
 *
 * Results are sorted by magnitude of expected downside and capped at 5.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestedTradesService {

    private static final int    MAX_SUGGESTIONS    = 5;
    private static final double MOVE_THRESHOLD_PCT = 2.0;   // trigger threshold (%)

    private final PortfolioRepository              portfolioRepository;
    private final StockPricePredictionRepository   predictionRepository;
    private final StockPriceService                stockPriceService;
    private final AtrService                       atrService;
    private final EtfActivityService               etfActivityService;
    private final NewsSentimentAnalysisRepository  newsSentimentRepository;

    /**
     * Returns up to {@value #MAX_SUGGESTIONS} suggested trades for the given client.
     *
     * @param clientId the client whose portfolio is analysed
     * @return list of suggested trades, sorted by expected downside magnitude (largest first)
     */
    public List<SuggestedTradeResponse> getSuggestedTrades(Long clientId) {
        List<Portfolio> holdings = portfolioRepository.findByClientId(clientId);
        if (holdings.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<SuggestedTradeResponse> candidates = new ArrayList<>();

        for (Portfolio holding : holdings) {
            String symbol = holding.getSymbol();
            try {
                SuggestedTradeResponse suggestion = evaluate(holding, now);
                if (suggestion != null) {
                    candidates.add(suggestion);
                }
            } catch (Exception e) {
                log.warn("Could not evaluate suggestion for {}: {}", symbol, e.getMessage());
            }
        }

        // Sort by expected downside magnitude (most negative first), then cap at MAX_SUGGESTIONS
        candidates.sort(Comparator.comparing(SuggestedTradeResponse::getExpectedChangePct));
        List<SuggestedTradeResponse> result = candidates.size() > MAX_SUGGESTIONS
            ? candidates.subList(0, MAX_SUGGESTIONS)
            : candidates;

        // Enrich each suggestion with ETF signal and recent news
        result.forEach(this::enrichWithMarketSignals);
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private SuggestedTradeResponse evaluate(Portfolio holding, LocalDateTime now) {
        String symbol  = holding.getSymbol();
        int    qty     = holding.getQuantity() != null ? holding.getQuantity() : 0;

        BigDecimal currentPrice = stockPriceService.getCurrentPrice(symbol);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No current price for {} – skipping", symbol);
            return null;
        }

        // ATR(14) – daily expected move
        BigDecimal atr14 = atrService.computeAtr14(symbol);
        if (atr14 == null) atr14 = BigDecimal.ZERO;

        // Future predictions (next 8 hours)
        LocalDateTime horizon = now.plusHours(8);
        List<StockPricePrediction> preds =
            predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(symbol, now)
                .stream()
                .filter(p -> !p.getTargetHour().isAfter(horizon))
                .toList();

        if (preds.isEmpty()) {
            // No prediction data – fall back to ATR-only assessment
            return evaluateAtrOnly(symbol, qty, currentPrice, atr14);
        }

        // Average predicted price weighted equally across all techniques / hours
        BigDecimal sumPredicted = preds.stream()
            .map(StockPricePrediction::getPredictedPrice)
            .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long validCount = preds.stream()
            .filter(p -> p.getPredictedPrice() != null && p.getPredictedPrice().compareTo(BigDecimal.ZERO) > 0)
            .count();

        if (validCount == 0) {
            return evaluateAtrOnly(symbol, qty, currentPrice, atr14);
        }

        BigDecimal avgPredicted = sumPredicted.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);

        // Expected change %
        BigDecimal changePct = avgPredicted.subtract(currentPrice)
            .divide(currentPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);

        // ATR as % of current price
        BigDecimal atrPct = atr14.compareTo(BigDecimal.ZERO) > 0
            ? atr14.divide(currentPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        boolean bigAtrMove  = atrPct.doubleValue() >= MOVE_THRESHOLD_PCT;
        boolean expectedDown = changePct.doubleValue() <= -MOVE_THRESHOLD_PCT;

        if (!expectedDown && !bigAtrMove) {
            return null; // no significant signal
        }

        // Count how many predictions agree on downward direction
        long downCount = preds.stream()
            .filter(p -> p.getPredictedPrice() != null && p.getPredictedPrice().compareTo(currentPrice) < 0)
            .count();
        int agreement = (int) Math.round((double) downCount / preds.size() * 100);

        // Confidence = blend of expected move magnitude and agreement
        int confidence = Math.min(100, (int) (Math.abs(changePct.doubleValue()) * 10) + agreement / 2);

        // Sell & buy-back prices
        BigDecimal sellPrice   = currentPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyBackPrice = currentPrice.subtract(atr14).setScale(2, RoundingMode.HALF_UP);
        if (buyBackPrice.compareTo(BigDecimal.ZERO) <= 0) {
            buyBackPrice = currentPrice.multiply(BigDecimal.valueOf(0.97)).setScale(2, RoundingMode.HALF_UP);
        }

        String action = expectedDown ? "SELL" : "WATCH";
        String reasoning = buildReasoning(symbol, changePct, atrPct, agreement, expectedDown, bigAtrMove);

        return SuggestedTradeResponse.builder()
            .symbol(symbol)
            .quantity(qty)
            .currentPrice(currentPrice)
            .atr14(atr14)
            .avgPredictedPrice(avgPredicted)
            .expectedChangePct(changePct)
            .action(action)
            .suggestedSellPrice(expectedDown ? sellPrice : null)
            .suggestedBuyBackPrice(expectedDown ? buyBackPrice : null)
            .confidence(confidence)
            .reasoning(reasoning)
            .build();
    }

    /** Fallback when no prediction data exists: rely solely on ATR signal. */
    private SuggestedTradeResponse evaluateAtrOnly(String symbol, int qty,
                                                    BigDecimal currentPrice, BigDecimal atr14) {
        BigDecimal atrPct = atr14.compareTo(BigDecimal.ZERO) > 0
            ? atr14.divide(currentPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        if (atrPct.doubleValue() < MOVE_THRESHOLD_PCT) return null;

        BigDecimal buyBackPrice = currentPrice.subtract(atr14).setScale(2, RoundingMode.HALF_UP);
        if (buyBackPrice.compareTo(BigDecimal.ZERO) <= 0) {
            buyBackPrice = currentPrice.multiply(BigDecimal.valueOf(0.97)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal zeroPct = BigDecimal.ZERO.setScale(2);
        return SuggestedTradeResponse.builder()
            .symbol(symbol)
            .quantity(qty)
            .currentPrice(currentPrice)
            .atr14(atr14)
            .avgPredictedPrice(null)
            .expectedChangePct(zeroPct)
            .action("WATCH")
            .suggestedSellPrice(null)
            .suggestedBuyBackPrice(buyBackPrice)
            .confidence(40)
            .reasoning("No prediction data available. ATR(" + atrPct.setScale(1, RoundingMode.HALF_UP) +
                "%) suggests high intraday volatility – monitor closely.")
            .build();
    }

    private String buildReasoning(String symbol, BigDecimal changePct, BigDecimal atrPct,
                                   int agreement, boolean expectedDown, boolean bigAtrMove) {
        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(": ");

        if (expectedDown) {
            sb.append("Predictions indicate a ").append(changePct.abs()).append("% decline over the next 8 hours");
            sb.append(" (").append(agreement).append("% of model outputs agree). ");
            sb.append("Suggested sell at current price; buy back after a ~").append(
                changePct.abs().setScale(1, RoundingMode.HALF_UP)).append("% dip.");
        } else {
            sb.append("High ATR (").append(atrPct.setScale(1, RoundingMode.HALF_UP))
              .append("% of price) indicates elevated intraday volatility – watch for entry.");
        }

        return sb.toString();
    }

    private void enrichWithMarketSignals(SuggestedTradeResponse response) {
        String symbol = response.getSymbol();
        try {
            // ETF signal from BUZZ/HDGE/MMTM
            String etfSignal = etfActivityService.getEtfSignal(symbol).name();
            response.setEtfSignal(etfSignal);
        } catch (Exception e) {
            log.debug("ETF signal unavailable for {}: {}", symbol, e.getMessage());
        }
        try {
            // Recent news: last 3 days, up to 5 items
            LocalDateTime since = LocalDateTime.now().minusDays(3);
            List<NewsSentimentAnalysis> newsItems =
                newsSentimentRepository.findBySymbolIgnoreCaseAndPublishedAtBetweenOrderByPublishedAtDesc(
                    symbol, since, LocalDateTime.now());
            if (!newsItems.isEmpty()) {
                List<NewsSentimentDto> dtos = newsItems.stream()
                    .limit(5)
                    .map(n -> new NewsSentimentDto(
                        n.getId(), n.getSymbol(), n.getTitle(), n.getSummary(),
                        n.getPublisher(), n.getArticleUrl(), n.getPublishedAt(),
                        n.getSentiment() != null ? n.getSentiment().name() : null,
                        n.getSentimentConfidence(), n.getAnalysisReason(),
                        n.getLlmModel(), n.getAnalyzedAt()))
                    .toList();
                response.setRecentNews(dtos);
            }
        } catch (Exception e) {
            log.debug("News enrichment failed for {}: {}", symbol, e.getMessage());
        }
    }
}
