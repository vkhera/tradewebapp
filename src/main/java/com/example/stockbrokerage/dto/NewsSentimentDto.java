package com.example.stockbrokerage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsSentimentDto(
        Long id,
        String symbol,
        String title,
        String summary,
        String publisher,
        String articleUrl,
        LocalDateTime publishedAt,
        String sentiment,            // POSITIVE / NEGATIVE / NEUTRAL
        Double sentimentConfidence,
        String analysisReason,
        String llmModel,
        LocalDateTime analyzedAt
) {}
