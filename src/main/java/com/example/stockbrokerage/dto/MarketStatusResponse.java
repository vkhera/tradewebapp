package com.example.stockbrokerage.dto;

/**
 * Current NYSE/NASDAQ market session status based on Eastern Time.
 *
 * @param estTime        Current time formatted as "h:mm a" in Eastern Time (e.g. "4:54 PM")
 * @param status         Machine-readable status: OPEN, PRE_MARKET, POST_MARKET, or CLOSED
 * @param statusLabel    Human-friendly label: "Open", "Pre-Market", "Post-Market", "Closed"
 * @param isRegularOpen  True only during regular session (9:30 AM – 4:00 PM ET, Mon–Fri)
 */
public record MarketStatusResponse(
        String estTime,
        String status,
        String statusLabel,
        boolean isRegularOpen
) {}
