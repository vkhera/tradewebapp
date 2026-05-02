package com.example.stockbrokerage.dto;

import java.time.LocalDateTime;

/**
 * Canonical news payload used by backend services.
 */
public record NewsItem(
    String symbol,
    String externalId,
    String title,
    String summary,
    String publisher,
    String link,
    LocalDateTime publishedAt
) {
}
