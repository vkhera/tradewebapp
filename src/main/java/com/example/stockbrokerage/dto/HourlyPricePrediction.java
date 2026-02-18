package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HourlyPricePrediction {

    private LocalDateTime targetHour;

    /** Weighted-ensemble predicted price */
    private BigDecimal predictedPrice;

    /** Confidence score 0-1 based on technique agreement */
    private double confidence;

    /** Per-technique predicted prices */
    private Map<String, BigDecimal> techniqueBreakdown;

    /** Per-technique weights at time of prediction */
    private Map<String, Double> techniqueWeights;
}
