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

    /** Weighted-ensemble predicted price (after index adjustment applied) */
    private BigDecimal predictedPrice;

    /** Confidence score 0-1 based on technique agreement */
    private double confidence;

    /** Per-technique predicted prices */
    private Map<String, BigDecimal> techniqueBreakdown;

    /** Per-technique weights at time of prediction */
    private Map<String, Double> techniqueWeights;

    /** Actual price recorded after the target hour passed (null for future predictions) */
    private BigDecimal actualPrice;

    /**
     * Raw ensemble price before the market-index adjustment was applied.
     * Allows the UI to show how much the indices shifted the prediction.
     */
    private BigDecimal priceBeforeIndexAdj;

    /**
     * Signed index adjustment factor (decimal) applied to the raw price.
     * e.g. 0.0043 means indices nudged the price up by 0.43%.
     */
    private double indexAdjustmentFactor;
}
