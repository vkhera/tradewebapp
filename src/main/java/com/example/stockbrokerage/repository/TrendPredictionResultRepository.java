package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.TrendPredictionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrendPredictionResultRepository extends JpaRepository<TrendPredictionResult, Long> {

    Optional<TrendPredictionResult> findBySymbolAndPredictionDate(String symbol, LocalDate date);

    /** Latest result for a symbol (used for fast portfolio read without re-analysis). */
    @Query("SELECT t FROM TrendPredictionResult t WHERE t.symbol = :symbol ORDER BY t.predictionDate DESC LIMIT 1")
    Optional<TrendPredictionResult> findLatestBySymbol(@Param("symbol") String symbol);

    /** All trend predictions for a specific date (one per symbol). */
    List<TrendPredictionResult> findByPredictionDate(LocalDate predictionDate);

    /** Full upsert – updates all fields when the same (symbol, date) is re-analysed. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value =
        "INSERT INTO trend_prediction_result " +
        "  (symbol, prediction_date, overall_trend, confidence, ma_crossover, rsi, macd, price_momentum, volume_trend, created_at) " +
        "VALUES (:symbol, :predictionDate, :overallTrend, :confidence, :maCrossover, :rsi, :macd, :priceMomentum, :volumeTrend, :createdAt) " +
        "ON CONFLICT (symbol, prediction_date) DO UPDATE " +
        "SET overall_trend = EXCLUDED.overall_trend, " +
        "    confidence = EXCLUDED.confidence, " +
        "    ma_crossover = EXCLUDED.ma_crossover, " +
        "    rsi = EXCLUDED.rsi, " +
        "    macd = EXCLUDED.macd, " +
        "    price_momentum = EXCLUDED.price_momentum, " +
        "    volume_trend = EXCLUDED.volume_trend")
    void upsertResult(@Param("symbol") String symbol,
                      @Param("predictionDate") LocalDate predictionDate,
                      @Param("overallTrend") String overallTrend,
                      @Param("confidence") Double confidence,
                      @Param("maCrossover") String maCrossover,
                      @Param("rsi") String rsi,
                      @Param("macd") String macd,
                      @Param("priceMomentum") String priceMomentum,
                      @Param("volumeTrend") String volumeTrend,
                      @Param("createdAt") LocalDateTime createdAt);
}
