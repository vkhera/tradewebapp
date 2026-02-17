package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.Trade.TradeStatus;
import com.example.stockbrokerage.entity.Trade.TradeType;
import com.example.stockbrokerage.entity.Trade.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {
    
    private Long id;
    private Long clientId;
    private String symbol;
    private Integer quantity;
    private BigDecimal price;
    private TradeType type;
    private OrderType orderType;
    private TradeStatus status;
    private LocalDateTime tradeTime;
    private LocalDateTime expiryTime;
    private Boolean fraudCheckPassed;
    private String fraudCheckReason;
}
