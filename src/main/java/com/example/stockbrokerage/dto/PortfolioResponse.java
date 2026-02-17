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
}
