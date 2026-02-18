package com.example.stockbrokerage.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImportResponse {
    private boolean success;
    private String message;
    private int recordsProcessed;
    private int recordsImported;
    private int recordsSkipped;
    private List<String> errors;
    
    public ImportResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
