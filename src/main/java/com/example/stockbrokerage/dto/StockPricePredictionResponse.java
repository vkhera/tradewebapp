package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPricePredictionResponse {

    private String symbol;

    private BigDecimal currentPrice;

    private LocalDateTime currentPriceAsOf;

    /** Next 8 hours of predicted prices */
    private List<HourlyPricePrediction> hourlyPredictions;

    /** Current per-stock technique weights */
    private Map<String, Double> techniqueWeights;

    /** Overall prediction confidence */
    private double overallConfidence;

    /** Whether data is from cache or freshly calculated */
    private boolean cached;

    private LocalDateTime calculatedAt;
}
