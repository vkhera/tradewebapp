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

    /** All market-hours predictions for today (9:30 AM – 4:00 PM) */
    private List<HourlyPricePrediction> hourlyPredictions;

    /** Current per-stock technique weights */
    private Map<String, Double> techniqueWeights;

    /** Overall prediction confidence */
    private double overallConfidence;

    /** Whether data is from cache or freshly calculated */
    private boolean cached;

    private LocalDateTime calculatedAt;

    /** Previous business day hourly predictions with actual prices */
    private List<HourlyPricePrediction> previousDayPredictions;

    /**
     * Current influence of each tracked market index (IWM, QQQ, VOO, DIA, VXVY)
     * on this stock's price prediction.  Populated on every fresh calculation.
     */
    private List<MarketIndexInfluence> indexInfluences;
}
