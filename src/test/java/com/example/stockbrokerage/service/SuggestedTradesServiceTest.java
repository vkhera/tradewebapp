package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.StockPricePrediction;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.StockPricePredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.stockbrokerage.repository.NewsSentimentAnalysisRepository;

/**
 */
class SuggestedTradesServiceTest {

    private SuggestedTradesService service;
    private PortfolioRepository portfolioRepository;
    private StockPricePredictionRepository predictionRepository;
    private StockPriceService stockPriceService;
    private AtrService atrService;

    @BeforeEach
    void setUp() {
        portfolioRepository   = mock(PortfolioRepository.class);
        predictionRepository  = mock(StockPricePredictionRepository.class);
        stockPriceService     = mock(StockPriceService.class);
        atrService            = mock(AtrService.class);
        service = new SuggestedTradesService(
            portfolioRepository, predictionRepository, stockPriceService, atrService,
            mock(EtfActivityService.class), mock(NewsSentimentAnalysisRepository.class));
    }

    // ── Empty portfolio ───────────────────────────────────────────────────────

    @Test
    void emptyPortfolio_returnsEmptyList() {
        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of());
        assertThat(service.getSuggestedTrades(1L)).isEmpty();
    }

    // ── Sell signal: stock expected to drop > 2% ─────────────────────────────

    @Test
    void stockExpectedToDropMoreThan2Pct_generatesSellSuggestion() {
        Portfolio p = holding("NVDA", 10);
        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(p));

        BigDecimal currentPrice = BigDecimal.valueOf(200.00);
        BigDecimal atr          = BigDecimal.valueOf(5.00);
        when(stockPriceService.getCurrentPrice("NVDA")).thenReturn(currentPrice);
        when(atrService.computeAtr14("NVDA")).thenReturn(atr);

        // Predictions averaging 185 → (185-200)/200 = -7.5%
        List<StockPricePrediction> preds = predictions("NVDA", currentPrice, BigDecimal.valueOf(185), 8);
        when(predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(eq("NVDA"), any()))
            .thenReturn(preds);

        List<SuggestedTradeResponse> result = service.getSuggestedTrades(1L);
        assertThat(result).hasSize(1);
        SuggestedTradeResponse trade = result.get(0);
        assertThat(trade.getAction()).isEqualTo("SELL");
        assertThat(trade.getSuggestedSellPrice()).isEqualByComparingTo(currentPrice);
        assertThat(trade.getSuggestedBuyBackPrice()).isEqualByComparingTo(currentPrice.subtract(atr));
        assertThat(trade.getExpectedChangePct()).isNegative();
    }

    // ── No suggestion for stable stock ───────────────────────────────────────

    @Test
    void stableStock_generatesNoSuggestion() {
        Portfolio p = holding("AAPL", 5);
        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(p));

        BigDecimal currentPrice = BigDecimal.valueOf(150.00);
        when(stockPriceService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
        when(atrService.computeAtr14("AAPL")).thenReturn(BigDecimal.valueOf(1.00));

        // Predictions averaging 151 → +0.67% – below threshold
        List<StockPricePrediction> preds = predictions("AAPL", currentPrice, BigDecimal.valueOf(151), 8);
        when(predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(eq("AAPL"), any()))
            .thenReturn(preds);

        assertThat(service.getSuggestedTrades(1L)).isEmpty();
    }

    // ── Result is capped at 5 ────────────────────────────────────────────────

    @Test
    void moreThan5StocksDroppingMoreThan2Pct_returnsOnly5() {
        List<Portfolio> holdings = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            holdings.add(holding("SYM" + i, 10));
        }
        when(portfolioRepository.findByClientId(1L)).thenReturn(holdings);

        for (int i = 1; i <= 8; i++) {
            String sym = "SYM" + i;
            BigDecimal price = BigDecimal.valueOf(100);
            BigDecimal predicted = BigDecimal.valueOf(90); // -10%
            when(stockPriceService.getCurrentPrice(sym)).thenReturn(price);
            when(atrService.computeAtr14(sym)).thenReturn(BigDecimal.valueOf(3));
            List<StockPricePrediction> preds = predictions(sym, price, predicted, 8);
            when(predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(eq(sym), any()))
                .thenReturn(preds);
        }

        List<SuggestedTradeResponse> result = service.getSuggestedTrades(1L);
        assertThat(result).hasSize(5);
    }

    // ── ATR-only fallback (no predictions) ───────────────────────────────────

    @Test
    void noPredictions_highAtr_returnsWatchSuggestion() {
        Portfolio p = holding("TSLA", 20);
        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(p));

        BigDecimal currentPrice = BigDecimal.valueOf(100.00);
        // ATR = 3 → 3% of price → above 2% threshold
        when(stockPriceService.getCurrentPrice("TSLA")).thenReturn(currentPrice);
        when(atrService.computeAtr14("TSLA")).thenReturn(BigDecimal.valueOf(3.00));
        when(predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(eq("TSLA"), any()))
            .thenReturn(List.of());

        List<SuggestedTradeResponse> result = service.getSuggestedTrades(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("WATCH");
        assertThat(result.get(0).getSuggestedSellPrice()).isNull();
        assertThat(result.get(0).getSuggestedBuyBackPrice()).isNotNull();
    }

    // ── Suggestion includes symbol, quantity, reasoning ───────────────────────

    @Test
    void suggestion_containsExpectedFields() {
        Portfolio p = holding("JPM", 15);
        when(portfolioRepository.findByClientId(1L)).thenReturn(List.of(p));

        BigDecimal price = BigDecimal.valueOf(180);
        when(stockPriceService.getCurrentPrice("JPM")).thenReturn(price);
        when(atrService.computeAtr14("JPM")).thenReturn(BigDecimal.valueOf(4));
        List<StockPricePrediction> preds = predictions("JPM", price, BigDecimal.valueOf(170), 8);
        when(predictionRepository.findBySymbolAndTargetHourAfterOrderByTargetHourAsc(eq("JPM"), any()))
            .thenReturn(preds);

        SuggestedTradeResponse trade = service.getSuggestedTrades(1L).get(0);
        assertThat(trade.getSymbol()).isEqualTo("JPM");
        assertThat(trade.getQuantity()).isEqualTo(15);
        assertThat(trade.getCurrentPrice()).isEqualByComparingTo(price);
        assertThat(trade.getReasoning()).isNotBlank();
        assertThat(trade.getConfidence()).isBetween(0, 100);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Portfolio holding(String symbol, int qty) {
        Portfolio p = new Portfolio();
        p.setSymbol(symbol);
        p.setQuantity(qty);
        p.setAveragePrice(BigDecimal.valueOf(100));
        return p;
    }

    /**
     * Builds a list of {@code count} predictions for the symbol,
     * all with the same predicted price, spread over the next 8 hours.
     */
    private List<StockPricePrediction> predictions(String symbol, BigDecimal currentPrice,
                                                    BigDecimal predictedPrice, int count) {
        LocalDateTime now = LocalDateTime.now();
        List<StockPricePrediction> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            StockPricePrediction pred = new StockPricePrediction();
            pred.setSymbol(symbol);
            pred.setTechnique("Linear_Regression");
            pred.setTargetHour(now.plusHours(i));
            pred.setPredictedPrice(predictedPrice);
            list.add(pred);
        }
        return list;
    }
}
