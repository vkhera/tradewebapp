package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.TrendPredictionWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrendPredictionWeightRepository extends JpaRepository<TrendPredictionWeight, Long> {

    Optional<TrendPredictionWeight> findBySymbolAndTechnique(String symbol, String technique);

    List<TrendPredictionWeight> findBySymbol(String symbol);

    /** Upsert: update weight and date if row already exists. */
    @Modifying
    @Query(nativeQuery = true, value =
        "INSERT INTO trend_prediction_weight (symbol, technique, weight, last_updated) " +
        "VALUES (:symbol, :technique, :weight, :lastUpdated) " +
        "ON CONFLICT (symbol, technique) DO UPDATE " +
        "SET weight = EXCLUDED.weight, last_updated = EXCLUDED.last_updated")
    void upsertWeight(@Param("symbol") String symbol,
                      @Param("technique") String technique,
                      @Param("weight") Double weight,
                      @Param("lastUpdated") LocalDate lastUpdated);
}
