package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryResponse {
    private BigDecimal cashBalance;
    private BigDecimal reservedBalance;
    private BigDecimal availableBalance;
    private List<PortfolioResponse> holdings;
    private BigDecimal totalPortfolioValue;
    private BigDecimal totalInvestedValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalProfitLossPercent;
}
