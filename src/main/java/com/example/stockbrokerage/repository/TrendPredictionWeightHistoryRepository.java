package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.TrendPredictionWeightHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrendPredictionWeightHistoryRepository extends JpaRepository<TrendPredictionWeightHistory, Long> {

    List<TrendPredictionWeightHistory> findBySymbolOrderByChangedAtDesc(String symbol);

    List<TrendPredictionWeightHistory> findBySymbolAndTechniqueOrderByChangedAtDesc(String symbol, String technique);
}
