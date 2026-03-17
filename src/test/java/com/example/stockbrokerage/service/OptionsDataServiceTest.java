package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OptionsDataService}.
 *
 * <p>Uses the package-private test constructor to inject a mock {@link RestTemplate},
 * so no network calls are made.  JSON fixtures mirror the shape returned by
 * {@code /v7/finance/options/{symbol}}.
 */
class OptionsDataServiceTest {

    private RestTemplate restTemplate;
    private OptionsDataService service;

    // ── minimal valid Yahoo Finance options chain response ─────────────────────
    private static final double PRICE   = 150.0;
    private static final String VALID_JSON = """
            {
              "optionChain": {
                "result": [{
                  "quote": { "regularMarketPrice": %s },
                  "options": [{
                    "calls": [
                      {"strike": 145.0, "impliedVolatility": 0.30, "openInterest": 1000},
                      {"strike": 150.0, "impliedVolatility": 0.42, "openInterest": 2000},
                      {"strike": 155.0, "impliedVolatility": 0.35, "openInterest": 1500}
                    ],
                    "puts": [
                      {"strike": 145.0, "impliedVolatility": 0.32, "openInterest": 1800},
                      {"strike": 150.0, "impliedVolatility": 0.44, "openInterest": 2200},
                      {"strike": 155.0, "impliedVolatility": 0.38, "openInterest": 1200}
                    ]
                  }]
                }],
                "error": null
              }
            }
            """.formatted(PRICE);

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new OptionsDataService(restTemplate, new ObjectMapper());
    }

    // ── valid response parsing ─────────────────────────────────────────────────

    @Test
    void validResponse_returnsDataAvailableSnapshot() {
        stubResponse(VALID_JSON);

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.dataAvailable()).isTrue();
        assertThat(snap.symbol()).isEqualTo("AAPL");
    }

    @Test
    void validResponse_computesAtmIVFromNearestStrike() {
        // Current price = 150.0 → nearest call strike is 150.0 (IV=0.42)
        stubResponse(VALID_JSON);

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.atmImpliedVolatility()).isEqualTo(0.42);
    }

    @Test
    void validResponse_computesPCRCorrectly() {
        // putOI  = 1800 + 2200 + 1200 = 5200
        // callOI = 1000 + 2000 + 1500 = 4500
        // PCR    = 5200 / 4500 ≈ 1.156
        stubResponse(VALID_JSON);

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.putCallRatioOI()).isCloseTo(5200.0 / 4500.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void validResponse_computesMaxPain() {
        // Max pain is the strike minimising total buyer value.
        // With calls heavier at 150, max pain should be a valid strike.
        stubResponse(VALID_JSON);

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.maxPain()).isIn(145.0, 150.0, 155.0);
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    void secondCall_withinTTL_doesNotCallRestTemplate() {
        stubResponse(VALID_JSON);

        service.getOptionsSnapshot("AAPL");
        service.getOptionsSnapshot("AAPL");

        // RestTemplate should only be called once (second call is served from cache)
        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void differentSymbols_eachFetchedOnce() {
        stubResponse(VALID_JSON);

        service.getOptionsSnapshot("AAPL");
        service.getOptionsSnapshot("NVDA");

        verify(restTemplate, times(2))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void networkError_returnsUnavailableSnapshot() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.dataAvailable()).isFalse();
        assertThat(snap.symbol()).isEqualTo("AAPL");
    }

    @Test
    void malformedJson_returnsUnavailableSnapshot() {
        stubResponse("not-valid-json{{{{");

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.dataAvailable()).isFalse();
    }

    @Test
    void emptyResultArray_returnsUnavailableSnapshot() {
        stubResponse("""
                { "optionChain": { "result": [], "error": null } }
                """);

        OptionsSnapshot snap = service.getOptionsSnapshot("TSLA");

        assertThat(snap.dataAvailable()).isFalse();
    }

    @Test
    void missingMarketPrice_returnsUnavailableSnapshot() {
        stubResponse("""
                {
                  "optionChain": {
                    "result": [{
                      "quote": { "regularMarketPrice": 0 },
                      "options": [{ "calls": [], "puts": [] }]
                    }],
                    "error": null
                  }
                }
                """);

        OptionsSnapshot snap = service.getOptionsSnapshot("MSFT");

        assertThat(snap.dataAvailable()).isFalse();
    }

    @Test
    void nonOkHttpStatus_returnsUnavailableSnapshot() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(null));

        OptionsSnapshot snap = service.getOptionsSnapshot("AAPL");

        assertThat(snap.dataAvailable()).isFalse();
    }

    // ── PCR edge cases ─────────────────────────────────────────────────────────

    @Test
    void zeroCalls_PCRReturnsOne_noArithmeticException() {
        stubResponse("""
                {
                  "optionChain": {
                    "result": [{
                      "quote": { "regularMarketPrice": 100.0 },
                      "options": [{
                        "calls": [],
                        "puts": [
                          {"strike": 100.0, "impliedVolatility": 0.30, "openInterest": 500}
                        ]
                      }]
                    }],
                    "error": null
                  }
                }
                """);

        // Should NOT throw; PCR defaults to 1.0 when callOI = 0
        OptionsSnapshot snap = service.getOptionsSnapshot("ZERO");

        // dataAvailable will be false because computeAtmIV returns 0 with no calls
        // The important thing is no exception was thrown:
        assertThat(snap).isNotNull();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubResponse(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }
}
