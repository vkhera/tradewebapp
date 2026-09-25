package com.example.stockbrokerage.dto;

import lombok.Data;

@Data
public class HoldingImportRequest {
    private Long clientId;
    private String fileName;
}
