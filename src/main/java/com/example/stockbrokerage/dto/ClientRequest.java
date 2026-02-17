package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.Client.ClientStatus;
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
public class ClientRequest {
    
    @NotBlank(message = "Client code is required")
    @Size(max = 50, message = "Client code must be at most 50 characters")
    private String clientCode;
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Phone is required")
    private String phone;
    
    @NotNull(message = "Account balance is required")
    @DecimalMin(value = "0.00", message = "Account balance must be non-negative")
    private BigDecimal accountBalance;
    
    @NotNull(message = "Status is required")
    private ClientStatus status;
    
    @NotBlank(message = "Risk level is required")
    @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Risk level must be LOW, MEDIUM, or HIGH")
    private String riskLevel;
    
    private BigDecimal dailyTradeLimit;
}
