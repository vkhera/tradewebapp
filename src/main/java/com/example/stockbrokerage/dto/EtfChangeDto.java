package com.example.stockbrokerage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtfChangeDto(
        String etfName,
        String symbol,
        String action,         // "Added" or "Removed"
        LocalDate changeDate,
        Double priceAtChange,
        Double currentPrice,
        String result          // "Success" or null
) {}
