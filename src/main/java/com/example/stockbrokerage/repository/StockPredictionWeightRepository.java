package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.StockPredictionWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPredictionWeightRepository extends JpaRepository<StockPredictionWeight, Long> {

    Optional<StockPredictionWeight> findBySymbolAndTechnique(String symbol, String technique);

    List<StockPredictionWeight> findBySymbol(String symbol);

    /** Upsert: update weight and timestamp if row already exists. */
    @Modifying
    @Query(nativeQuery = true, value =
        "INSERT INTO stock_prediction_weight (symbol, technique, weight, last_updated) " +
        "VALUES (:symbol, :technique, :weight, :lastUpdated) " +
        "ON CONFLICT (symbol, technique) DO UPDATE " +
        "SET weight = EXCLUDED.weight, last_updated = EXCLUDED.last_updated")
    void upsertWeight(@Param("symbol") String symbol,
                      @Param("technique") String technique,
                      @Param("weight") Double weight,
                      @Param("lastUpdated") LocalDateTime lastUpdated);
}
