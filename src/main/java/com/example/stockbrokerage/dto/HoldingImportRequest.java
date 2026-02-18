package com.example.stockbrokerage.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HoldingImportRequest {
    private Long clientId;
    private String fileName;
}
