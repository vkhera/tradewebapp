package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents one market index's current state and its computed influence
 * on an individual stock's price prediction.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code indexSymbol}     – one of IWM, QQQ, VOO, DIA, VXVY</li>
 *   <li>{@code currentPrice}    – latest fetched price for the index</li>
 *   <li>{@code todayReturnPct}  – intraday return % (current vs first bar of day)</li>
 *   <li>{@code correlation}     – rolling Pearson correlation coefficient vs the stock
 *                                 (positive = moves with stock, negative = inverse)</li>
 *   <li>{@code weight}          – learned weight allocated to this index (0–1, sums to 1 across indices)</li>
 *   <li>{@code influencePct}    – net contribution to the price adjustment in percentage points
 *                                 = weight × correlation × todayReturnPct × 100 (dampened)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndexInfluence {

    /** Index ticker: IWM, QQQ, VOO, DIA, VXVY */
    private String indexSymbol;

    /** Last known price for this index */
    private BigDecimal currentPrice;

    /**
     * Today's intraday return for the index expressed as a percentage.
     * Positive = index is up today, negative = index is down.
     */
    private double todayReturnPct;

    /**
     * Rolling 24-hour Pearson correlation between the stock's 5-min returns
     * and this index's 5-min returns.  Range –1.0 to +1.0.
     */
    private double correlation;

    /**
     * Current learned weight for this index across all portfolio predictions
     * for the given stock.  All five index weights for a stock sum to 1.0.
     */
    private double weight;

    /**
     * Signed contribution to the price-adjustment factor as a percentage.
     * = weight × correlation × todayReturnPct (already dampened)
     * Positive → index nudges the predicted price upward.
     * Negative → index nudges the predicted price downward.
     */
    private double influencePct;
}
