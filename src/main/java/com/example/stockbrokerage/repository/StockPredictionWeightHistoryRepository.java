package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.StockPredictionWeightHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockPredictionWeightHistoryRepository extends JpaRepository<StockPredictionWeightHistory, Long> {

    List<StockPredictionWeightHistory> findBySymbolOrderByChangedAtDesc(String symbol);

    List<StockPredictionWeightHistory> findBySymbolAndTechniqueOrderByChangedAtDesc(String symbol, String technique);
}
