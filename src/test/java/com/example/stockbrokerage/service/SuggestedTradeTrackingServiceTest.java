package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.SuggestedTradeHistoryResponse;
import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.dto.TradeSuccessRateResponse;
import com.example.stockbrokerage.entity.SuggestedTradeRecord;
import com.example.stockbrokerage.entity.SuggestedTradeRecord.TradeOutcomeStatus;
import com.example.stockbrokerage.repository.SuggestedTradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SuggestedTradeTrackingService}.
 *
 * Verifies persistence de-duplication, history queries, success-rate calculation,
 * and the daily scheduler logic (SUCCESS / FAILED / still PENDING outcomes).
 */
class SuggestedTradeTrackingServiceTest {

    private SuggestedTradeTrackingService service;
    private SuggestedTradeRecordRepository repository;
    private StockPriceService stockPriceService;

    @BeforeEach
    void setUp() {
        repository      = mock(SuggestedTradeRecordRepository.class);
        stockPriceService = mock(StockPriceService.class);
        service = new SuggestedTradeTrackingService(repository, stockPriceService);
    }

    // ── saveSuggestions ───────────────────────────────────────────────────────

    @Test
    void saveSuggestions_persistsNewSuggestion() {
        when(repository.existsByClientIdAndSymbolAndSuggestedDateBetween(
                eq(1L), eq("AAPL"), any(), any())).thenReturn(false);

        SuggestedTradeResponse s = buildResponse("AAPL", "SELL",
                BigDecimal.valueOf(200), BigDecimal.valueOf(195));

        service.saveSuggestions(1L, List.of(s));

        verify(repository, times(1)).save(any(SuggestedTradeRecord.class));
    }

    @Test
    void saveSuggestions_skipsDuplicateSameDaySuggestion() {
        // Already stored today
        when(repository.existsByClientIdAndSymbolAndSuggestedDateBetween(
                eq(1L), eq("AAPL"), any(), any())).thenReturn(true);

        SuggestedTradeResponse s = buildResponse("AAPL", "SELL",
                BigDecimal.valueOf(200), BigDecimal.valueOf(195));

        service.saveSuggestions(1L, List.of(s));

        verify(repository, never()).save(any());
    }

    @Test
    void saveSuggestions_watchSuggestionWithNullPrices_persisted() {
        when(repository.existsByClientIdAndSymbolAndSuggestedDateBetween(
                eq(1L), eq("TSLA"), any(), any())).thenReturn(false);

        SuggestedTradeResponse s = buildResponse("TSLA", "WATCH", null, BigDecimal.valueOf(95));

        service.saveSuggestions(1L, List.of(s));

        verify(repository, times(1)).save(argThat(r ->
                "TSLA".equals(r.getSymbol()) && "WATCH".equals(r.getAction())
        ));
    }

    // ── getRecentHistory ─────────────────────────────────────────────────────

    @Test
    void getRecentHistory_returnsHistoryMappedToDto() {
        SuggestedTradeRecord record = buildRecord(1L, "NVDA", TradeOutcomeStatus.SUCCESS,
                LocalDateTime.now().minusDays(1));

        when(repository.findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(eq(1L), any()))
                .thenReturn(List.of(record));

        List<SuggestedTradeHistoryResponse> history = service.getRecentHistory(1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSymbol()).isEqualTo("NVDA");
        assertThat(history.get(0).getStatus()).isEqualTo(TradeOutcomeStatus.SUCCESS);
    }

    @Test
    void getRecentHistory_emptyWhenNoRecords() {
        when(repository.findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(anyLong(), any()))
                .thenReturn(List.of());

        assertThat(service.getRecentHistory(42L)).isEmpty();
    }

    // ── getSuccessRate ────────────────────────────────────────────────────────

    @Test
    void getSuccessRate_computesCorrectPercentage() {
        List<SuggestedTradeRecord> records = List.of(
                buildRecord(1L, "AAPL", TradeOutcomeStatus.SUCCESS, LocalDateTime.now().minusDays(2)),
                buildRecord(2L, "NVDA", TradeOutcomeStatus.SUCCESS, LocalDateTime.now().minusDays(3)),
                buildRecord(3L, "TSLA", TradeOutcomeStatus.FAILED,  LocalDateTime.now().minusDays(4)),
                buildRecord(4L, "MSFT", TradeOutcomeStatus.PENDING, LocalDateTime.now().minusDays(1))
        );
        when(repository.findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(eq(1L), any()))
                .thenReturn(records);

        TradeSuccessRateResponse rate = service.getSuccessRate(1L);

        assertThat(rate.getSuccessCount()).isEqualTo(2);
        assertThat(rate.getFailedCount()).isEqualTo(1);
        assertThat(rate.getPendingCount()).isEqualTo(1);
        assertThat(rate.getTotalResolved()).isEqualTo(3);
        // 2/3 = 66.7%
        assertThat(rate.getSuccessRatePct()).isEqualTo(66.7);
    }

    @Test
    void getSuccessRate_returnsZeroWhenNoResolvedRecords() {
        when(repository.findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(anyLong(), any()))
                .thenReturn(List.of());

        TradeSuccessRateResponse rate = service.getSuccessRate(99L);

        assertThat(rate.getSuccessRatePct()).isEqualTo(0.0);
        assertThat(rate.getTotalResolved()).isEqualTo(0);
    }

    // ── checkPendingSuggestions (daily scheduler) ─────────────────────────────

    @Test
    void scheduler_marksRecordSuccessWhenPriceHitsTarget() {
        SuggestedTradeRecord record = buildRecord(1L, "AAPL", TradeOutcomeStatus.PENDING,
                LocalDateTime.now().minusDays(2));
        record.setSuggestedBuyBackPrice(BigDecimal.valueOf(190));

        when(repository.findByStatusAndSuggestedDateAfter(eq(TradeOutcomeStatus.PENDING), any()))
                .thenReturn(List.of(record));
        // Current price dropped to/below the target
        when(stockPriceService.getCurrentPrice("AAPL")).thenReturn(BigDecimal.valueOf(188));

        service.checkPendingSuggestions();

        verify(repository).save(argThat(r -> r.getStatus() == TradeOutcomeStatus.SUCCESS));
    }

    @Test
    void scheduler_marksRecordFailedWhenOlderThan7DaysAndTargetNotHit() {
        SuggestedTradeRecord record = buildRecord(1L, "NVDA", TradeOutcomeStatus.PENDING,
                LocalDateTime.now().minusDays(8));
        record.setSuggestedBuyBackPrice(BigDecimal.valueOf(400));

        when(repository.findByStatusAndSuggestedDateAfter(eq(TradeOutcomeStatus.PENDING), any()))
                .thenReturn(List.of(record));
        // Current price is still above the target
        when(stockPriceService.getCurrentPrice("NVDA")).thenReturn(BigDecimal.valueOf(450));

        service.checkPendingSuggestions();

        verify(repository).save(argThat(r -> r.getStatus() == TradeOutcomeStatus.FAILED));
    }

    @Test
    void scheduler_leavesPendingWhenWithin7DaysAndTargetNotHit() {
        SuggestedTradeRecord record = buildRecord(1L, "MSFT", TradeOutcomeStatus.PENDING,
                LocalDateTime.now().minusDays(3));
        record.setSuggestedBuyBackPrice(BigDecimal.valueOf(300));

        when(repository.findByStatusAndSuggestedDateAfter(eq(TradeOutcomeStatus.PENDING), any()))
                .thenReturn(List.of(record));
        // Price has not dropped to target yet
        when(stockPriceService.getCurrentPrice("MSFT")).thenReturn(BigDecimal.valueOf(350));

        service.checkPendingSuggestions();

        // No save should occur – record remains PENDING
        verify(repository, never()).save(any());
    }

    @Test
    void scheduler_handlesStocksWithNoBuyBackTarget() {
        SuggestedTradeRecord record = buildRecord(1L, "AMZN", TradeOutcomeStatus.PENDING,
                LocalDateTime.now().minusDays(9));
        record.setSuggestedBuyBackPrice(null); // WATCH with no target

        when(repository.findByStatusAndSuggestedDateAfter(eq(TradeOutcomeStatus.PENDING), any()))
                .thenReturn(List.of(record));
        when(stockPriceService.getCurrentPrice("AMZN")).thenReturn(BigDecimal.valueOf(180));

        service.checkPendingSuggestions();

        // No target to compare → still expires by date (8 days > 7 day expiry)
        // However since target is null, no SUCCESS path — only FAILED by age applies
        verify(repository).save(argThat(r -> r.getStatus() == TradeOutcomeStatus.FAILED));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SuggestedTradeResponse buildResponse(String symbol, String action,
                                                   BigDecimal sellPrice, BigDecimal buyBackPrice) {
        return SuggestedTradeResponse.builder()
                .symbol(symbol)
                .quantity(10)
                .currentPrice(BigDecimal.valueOf(200))
                .atr14(BigDecimal.valueOf(5))
                .avgPredictedPrice(BigDecimal.valueOf(185))
                .expectedChangePct(BigDecimal.valueOf(-7.5))
                .action(action)
                .suggestedSellPrice(sellPrice)
                .suggestedBuyBackPrice(buyBackPrice)
                .confidence(75)
                .reasoning("Test reasoning")
                .build();
    }

    private SuggestedTradeRecord buildRecord(long id, String symbol,
                                              TradeOutcomeStatus status,
                                              LocalDateTime suggestedDate) {
        SuggestedTradeRecord r = new SuggestedTradeRecord();
        r.setId(id);
        r.setClientId(1L);
        r.setSymbol(symbol);
        r.setQuantity(10);
        r.setSuggestedDate(suggestedDate);
        r.setAction("SELL");
        r.setCurrentPriceAtSuggestion(BigDecimal.valueOf(200));
        r.setAtr14(BigDecimal.valueOf(5));
        r.setExpectedChangePct(BigDecimal.valueOf(-7.5));
        r.setSuggestedSellPrice(BigDecimal.valueOf(200));
        r.setSuggestedBuyBackPrice(BigDecimal.valueOf(195));
        r.setConfidence(70);
        r.setReasoning("Test reasoning");
        r.setStatus(status);
        return r;
    }
}
