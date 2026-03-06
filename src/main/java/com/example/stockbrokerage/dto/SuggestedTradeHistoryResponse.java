package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.SuggestedTradeRecord.TradeOutcomeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO returned by the suggested-trade history endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedTradeHistoryResponse {

    private Long id;
    private Long clientId;
    private String symbol;
    private Integer quantity;
    private LocalDateTime suggestedDate;
    private String action;
    private BigDecimal currentPriceAtSuggestion;
    private BigDecimal atr14;
    private BigDecimal avgPredictedPrice;
    private BigDecimal expectedChangePct;
    private BigDecimal suggestedSellPrice;
    private BigDecimal suggestedBuyBackPrice;
    private Integer confidence;
    private String reasoning;
    private TradeOutcomeStatus status;
    private LocalDateTime resolvedDate;
}
