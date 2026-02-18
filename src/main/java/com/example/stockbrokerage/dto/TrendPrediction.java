package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPrediction {
    private String symbol;
    private LocalDate predictionDate;
    private TrendDirection overallTrend;
    private Map<String, TrendDirection> techniqueResults;
    private Map<String, Double> techniqueWeights;
    private double confidence;
    
    public enum TrendDirection {
        UPTREND,
        DOWNTREND,
        SIDEWAYS
    }
}
