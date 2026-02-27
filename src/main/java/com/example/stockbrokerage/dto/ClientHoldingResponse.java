package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single portfolio row as seen from the admin "Client Holdings" view.
 * Returned by {@code GET /api/admin/portfolio/all-holdings}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientHoldingResponse {
    private Long   clientId;
    private String clientName;
    private String symbol;
    private int    quantity;
}
