package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.StockPricePrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPricePredictionRepository extends JpaRepository<StockPricePrediction, Long> {

    List<StockPricePrediction> findBySymbolAndTargetHourAfterOrderByTargetHourAsc(
        String symbol, LocalDateTime after);

    List<StockPricePrediction> findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
        String symbol, LocalDateTime from, LocalDateTime to);

    Optional<StockPricePrediction> findBySymbolAndTechniqueAndTargetHour(
        String symbol, String technique, LocalDateTime targetHour);

    @Query("SELECT p FROM StockPricePrediction p WHERE p.symbol = :symbol " +
           "AND p.targetHour <= :now AND p.actualPrice IS NULL ORDER BY p.targetHour ASC")
    List<StockPricePrediction> findUnresolvedPredictions(
        @Param("symbol") String symbol, @Param("now") LocalDateTime now);

    @Query("SELECT p FROM StockPricePrediction p WHERE p.symbol = :symbol " +
           "AND p.actualPrice IS NOT NULL ORDER BY p.targetHour DESC")
    List<StockPricePrediction> findResolvedPredictions(@Param("symbol") String symbol);

    @Query("SELECT DISTINCT p.symbol FROM StockPricePrediction p")
    List<String> findAllTrackedSymbols();
}
