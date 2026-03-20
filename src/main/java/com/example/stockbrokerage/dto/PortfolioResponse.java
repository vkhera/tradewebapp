package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {
    private Long id;
    private String symbol;
    private Integer quantity;
    private BigDecimal averagePrice;
    private BigDecimal currentPrice;
    private BigDecimal totalValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    /** Average True Range (14-period) – null when insufficient data. */
    private BigDecimal atr14;
    /** 75th-percentile of the 14 daily True Ranges used in ATR(14) – typical spike magnitude. */
    private BigDecimal atr75;
    /** 90th-percentile of the 14 daily True Ranges used in ATR(14) – tail-risk day magnitude. */
    private BigDecimal atr90;
    /**
     * After-hours (post-market) price. {@code null} during regular market hours or when
     * the exchange does not report post-market data for this symbol.
     */
    private BigDecimal postMarketPrice;
}
