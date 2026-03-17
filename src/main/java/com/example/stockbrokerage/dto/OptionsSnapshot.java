package com.example.stockbrokerage.dto;

/**
 * Immutable snapshot of options-market data for a single symbol, fetched from
 * Yahoo Finance's options chain endpoint.
 *
 * <p>All values are derived from the <em>front-month</em> (nearest) expiry.
 *
 * @param symbol              Ticker symbol (e.g. "AAPL").
 * @param atmImpliedVolatility At-the-money implied volatility as a fraction
 *                            (e.g. 0.35 = 35%). Taken from the call contract
 *                            whose strike is closest to the current market price.
 * @param putCallRatioOI      Ratio of total put open-interest to total call
 *                            open-interest for the front-month expiry.
 *                            Values above 1.0 indicate more put activity
 *                            (bearish hedging); values below 1.0 indicate
 *                            more call activity (bullish speculation).
 * @param maxPain             Strike price at which the aggregate dollar value
 *                            of all outstanding options is minimised for option
 *                            buyers (i.e. maximised for option sellers).
 *                            Acts as a short-term price magnet near expiration.
 * @param dataAvailable       {@code false} when the fetch failed or the chain
 *                            contained insufficient data — callers must check
 *                            this before using the other fields.
 */
public record OptionsSnapshot(
        String symbol,
        double atmImpliedVolatility,
        double putCallRatioOI,
        double maxPain,
        boolean dataAvailable) {

    /** Convenience factory used when options data cannot be retrieved. */
    public static OptionsSnapshot unavailable(String symbol) {
        return new OptionsSnapshot(symbol, 0.0, 0.0, 0.0, false);
    }

    /** Returns {@code true} when IV ≥ 40% — options market is pricing in high uncertainty. */
    public boolean isHighIV() {
        return dataAvailable && atmImpliedVolatility >= 0.40;
    }

    /** Returns {@code true} when IV ≥ 60% — options market is pricing in extreme uncertainty. */
    public boolean isExtremeIV() {
        return dataAvailable && atmImpliedVolatility >= 0.60;
    }

    /** Returns {@code true} when PCR &gt; 1.5 — heavy put-buying signals bearish-hedging extremes (contrarian bullish). */
    public boolean isExtremeFear() {
        return dataAvailable && putCallRatioOI > 1.5;
    }

    /** Returns {@code true} when PCR &lt; 0.7 — heavy call-buying signals complacency (contrarian bearish). */
    public boolean isExtremeGreed() {
        return dataAvailable && putCallRatioOI < 0.7;
    }
}
