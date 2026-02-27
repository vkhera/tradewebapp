package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.StockPredictionWeight;
import com.example.stockbrokerage.entity.StockPredictionWeightHistory;
import com.example.stockbrokerage.entity.TrendPredictionWeight;
import com.example.stockbrokerage.entity.TrendPredictionWeightHistory;
import com.example.stockbrokerage.repository.StockPredictionWeightHistoryRepository;
import com.example.stockbrokerage.repository.StockPredictionWeightRepository;
import com.example.stockbrokerage.repository.TrendPredictionResultRepository;
import com.example.stockbrokerage.repository.TrendPredictionWeightHistoryRepository;
import com.example.stockbrokerage.repository.TrendPredictionWeightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that weight history is recorded when weights are updated via
 * {@link StockPricePredictionService#saveWeights} and {@link TrendAnalysisService}'s
 * private saveWeights method.
 *
 * CSV side-effects write to the project's stock_predictions / trend_predictions
 * directories (same locations used by the application), which are present in the workspace.
 */
class WeightHistoryTest {

    @BeforeEach
    void ensureDirsExist() throws Exception {
        Files.createDirectories(Path.of("stock_predictions"));
        Files.createDirectories(Path.of("trend_predictions"));
    }

    // ─── StockPricePredictionService ──────────────────────────────────────────

    @Test
    void saveWeights_whenWeightExistsAndChanged_recordsHistory() {
        StockPredictionWeightRepository weightRepo      = mock(StockPredictionWeightRepository.class);
        StockPredictionWeightHistoryRepository histRepo = mock(StockPredictionWeightHistoryRepository.class);

        // Simulate an existing weight for "AAPL" with technique "Linear_Regression"
        StockPredictionWeight existing = new StockPredictionWeight();
        existing.setSymbol("AAPL");
        existing.setTechnique("Linear_Regression");
        existing.setWeight(0.20);
        when(weightRepo.findBySymbolAndTechnique("AAPL", "Linear_Regression"))
            .thenReturn(Optional.of(existing));

        StockPricePredictionService svc = new StockPricePredictionService(
            mock(StockMarketDataService.class),
            mock(com.example.stockbrokerage.repository.StockPricePredictionRepository.class),
            weightRepo,
            histRepo,
            mock(MarketIndexService.class)
        );

        svc.saveWeights("AAPL", Map.of("Linear_Regression", 0.35));

        ArgumentCaptor<StockPredictionWeightHistory> captor =
            ArgumentCaptor.forClass(StockPredictionWeightHistory.class);
        verify(histRepo).save(captor.capture());

        StockPredictionWeightHistory recorded = captor.getValue();
        assertThat(recorded.getSymbol()).isEqualTo("AAPL");
        assertThat(recorded.getTechnique()).isEqualTo("Linear_Regression");
        assertThat(recorded.getPreviousWeight()).isEqualTo(0.20);
        assertThat(recorded.getNewWeight()).isEqualTo(0.35);
        assertThat(recorded.getChangedAt()).isNotNull();
    }

    @Test
    void saveWeights_whenNoExistingWeight_noHistoryRecorded() {
        StockPredictionWeightRepository weightRepo      = mock(StockPredictionWeightRepository.class);
        StockPredictionWeightHistoryRepository histRepo = mock(StockPredictionWeightHistoryRepository.class);

        when(weightRepo.findBySymbolAndTechnique(any(), any())).thenReturn(Optional.empty());

        StockPricePredictionService svc = new StockPricePredictionService(
            mock(StockMarketDataService.class),
            mock(com.example.stockbrokerage.repository.StockPricePredictionRepository.class),
            weightRepo,
            histRepo,
            mock(MarketIndexService.class)
        );

        svc.saveWeights("NEWSTOCK", Map.of("Linear_Regression", 0.20));

        verify(histRepo, never()).save(any());
    }

    // ─── TrendAnalysisService ─────────────────────────────────────────────────

    @Test
    void trendSaveWeights_whenWeightExistsAndChanged_recordsHistory() throws Exception {
        TrendPredictionWeightRepository trendWeightRepo      = mock(TrendPredictionWeightRepository.class);
        TrendPredictionWeightHistoryRepository trendHistRepo = mock(TrendPredictionWeightHistoryRepository.class);

        TrendPredictionWeight existing = new TrendPredictionWeight();
        existing.setSymbol("NVDA");
        existing.setTechnique("MA_Crossover");
        existing.setWeight(0.25);
        existing.setLastUpdated(LocalDate.now());
        when(trendWeightRepo.findBySymbolAndTechnique("NVDA", "MA_Crossover"))
            .thenReturn(Optional.of(existing));

        TrendAnalysisService trendSvc = new TrendAnalysisService(
            mock(StockPriceService.class),
            mock(TrendPredictionResultRepository.class),
            trendWeightRepo,
            trendHistRepo,
            mock(MarketIndexService.class)
        );

        // Invoke private saveWeights via reflection
        var method = TrendAnalysisService.class.getDeclaredMethod("saveWeights", String.class, Map.class);
        method.setAccessible(true);
        method.invoke(trendSvc, "NVDA", Map.of("MA_Crossover", 0.40));

        ArgumentCaptor<TrendPredictionWeightHistory> captor =
            ArgumentCaptor.forClass(TrendPredictionWeightHistory.class);
        verify(trendHistRepo).save(captor.capture());

        TrendPredictionWeightHistory hist = captor.getValue();
        assertThat(hist.getSymbol()).isEqualTo("NVDA");
        assertThat(hist.getTechnique()).isEqualTo("MA_Crossover");
        assertThat(hist.getPreviousWeight()).isEqualTo(0.25);
        assertThat(hist.getNewWeight()).isEqualTo(0.40);
    }

    @Test
    void trendSaveWeights_whenNoExistingWeight_noHistoryRecorded() throws Exception {
        TrendPredictionWeightRepository trendWeightRepo      = mock(TrendPredictionWeightRepository.class);
        TrendPredictionWeightHistoryRepository trendHistRepo = mock(TrendPredictionWeightHistoryRepository.class);

        when(trendWeightRepo.findBySymbolAndTechnique(any(), any())).thenReturn(Optional.empty());

        TrendAnalysisService trendSvc = new TrendAnalysisService(
            mock(StockPriceService.class),
            mock(TrendPredictionResultRepository.class),
            trendWeightRepo,
            trendHistRepo,
            mock(MarketIndexService.class)
        );

        var method = TrendAnalysisService.class.getDeclaredMethod("saveWeights", String.class, Map.class);
        method.setAccessible(true);
        method.invoke(trendSvc, "NEWTREND", Map.of("MA_Crossover", 0.30));

        verify(trendHistRepo, never()).save(any());
    }
}

