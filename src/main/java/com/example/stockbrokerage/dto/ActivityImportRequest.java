package com.example.stockbrokerage.dto;

import lombok.Data;

@Data
public class ActivityImportRequest {
    private Long clientId;
    private String fileName;
}
