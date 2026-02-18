package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.Trade.TradeType;
import com.example.stockbrokerage.entity.Trade.OrderType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequest {
    
    @NotNull(message = "Client ID is required")
    private Long clientId;
    
    @NotBlank(message = "Symbol is required")
    @Size(max = 10, message = "Symbol must be at most 10 characters")
    private String symbol;
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;
    
    @NotNull(message = "Trade type is required")
    private TradeType type;
    
    private OrderType orderType; // MARKET or LIMIT, defaults to MARKET
}
