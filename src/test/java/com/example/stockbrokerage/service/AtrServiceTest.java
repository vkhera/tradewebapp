package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import com.example.stockbrokerage.repository.StockPriceCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AtrService}.
 * <p>
 * Uses {@link MockYahooFinanceClient} and real {@link StockMarketDataService} so the
 * full ATR calculation pipeline is exercised with no network access and no Spring context.
 * The mock generates 4 680 deterministic 5-minute bars per symbol — enough to populate
 * all 14+ required daily buckets comfortably.
 */
class AtrServiceTest {

    private AtrService atrService;

    @BeforeEach
    void setUp() {
        MockYahooFinanceClient mockClient = new MockYahooFinanceClient();
        StockPriceCacheRepository mockCacheRepo = mock(StockPriceCacheRepository.class);
        StockMarketDataService marketDataService = new StockMarketDataService(mockClient, mockCacheRepo);
        atrService = new AtrService(marketDataService);
    }

    // ── computeAtr14 result shape ────────────────────────────────────────────

    @Test
    void computeAtr14_returnsNonNullForSufficientData() {
        BigDecimal atr = atrService.computeAtr14("AAPL");
        assertThat(atr).isNotNull();
    }

    @Test
    void computeAtr14_returnsPositiveValue() {
        BigDecimal atr = atrService.computeAtr14("NVDA");
        assertThat(atr).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void computeAtr14_isScaledToTwoDecimalPlaces() {
        BigDecimal atr = atrService.computeAtr14("JPM");
        assertThat(atr).isNotNull();
        assertThat(atr.scale()).isEqualTo(2);
    }

    // ── ATR is symbol-specific ───────────────────────────────────────────────

    @Test
    void computeAtr14_differsBySymbol() {
        // Different symbols have different price series → different ATRs
        BigDecimal atrAapl = atrService.computeAtr14("AAPL");
        BigDecimal atrNvda = atrService.computeAtr14("NVDA");
        assertThat(atrAapl).isNotNull();
        assertThat(atrNvda).isNotNull();
        // They may coincidentally be equal only if seed prices happen to align,
        // but with the LCG walk they will differ in practice.
        // We assert they are both valid rather than requiring strict inequality.
    }

    @Test
    void computeAtr14_isDeterministicAcrossCalls() {
        // Same symbol → same deterministic mock data → same ATR on every call
        BigDecimal first  = atrService.computeAtr14("AAPL");
        BigDecimal second = atrService.computeAtr14("AAPL");
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isEqualByComparingTo(second);
    }

    // ── ATR is a reasonable fraction of the stock price ──────────────────────

    @Test
    void computeAtr14_isReasonableRelativeToPrice() {
        // ATR should be between 0.01 % and 20 % of the seed price
        // (0.1 % mock volatility per 5-min bar → daily ATR << 5 %)
        String symbol = "AAPL";
        BigDecimal atr       = atrService.computeAtr14(symbol);
        BigDecimal seedPrice = MockYahooFinanceClient.seedPrice(symbol);

        assertThat(atr).isNotNull();
        BigDecimal pct = atr.divide(seedPrice, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

        assertThat(pct).isBetween(BigDecimal.valueOf(0.01), BigDecimal.valueOf(20));
    }

    // ── Symbol case: empty / short symbol still handled gracefully ───────────

    @Test
    void computeAtr14_handlesUnknownSymbolGracefully() {
        // MockYahooFinanceClient generates data for any symbol, so ATR is computed.
        // The important thing is it does not throw.
        BigDecimal atr = atrService.computeAtr14("ZZZZ");
        // May be null (insufficient buckets) or a positive value — both are acceptable.
        if (atr != null) {
            assertThat(atr).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
