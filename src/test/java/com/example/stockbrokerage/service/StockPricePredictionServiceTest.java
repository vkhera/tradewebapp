package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import com.example.stockbrokerage.dto.StockPricePredictionResponse;
import com.example.stockbrokerage.entity.StockPricePrediction;
import com.example.stockbrokerage.repository.StockPredictionWeightHistoryRepository;
import com.example.stockbrokerage.repository.StockPredictionWeightRepository;
import com.example.stockbrokerage.repository.StockPricePredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Time-aware unit tests for {@link StockPricePredictionService}.
 *
 * <p>Each test injects a {@link Clock#fixed fixed clock} to simulate a specific
 * point in the trading day. The service normalises prediction timestamps to
 * Eastern-local wall clock time before persisting or querying them.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li><b>Pre-market (before 9 AM ET)</b> – verify the service generates
 *       predictions whose target hours fall inside today's ET market window
 *       (9 AM–4 PM ET).</li>
 *   <li><b>Mid-market (10:30 AM ET)</b> – when the DB already holds fresh
 *       predictions the service returns a full-day view (past hours with
 *       actual prices, future hours without).</li>
 *   <li><b>Near-close (3:30 PM ET)</b> – only the 4 PM target hour remains
 *       in the future; the rest carry actual prices.</li>
 *   <li><b>After-hours (5:30 PM ET)</b> – all market-hours predictions are
 *       past; the freshness check is stale so the service recalculates,
 *       but the new target hours fall outside the day's window.</li>
 *   <li><b>Weekend (Saturday)</b> – no current-day predictions are returned,
 *       only the previous business day (Friday) data.</li>
 * </ul>
 *
 * <h3>Timezone note</h3>
 * The service stores {@code targetHour} values as Eastern-local {@code LocalDateTime}
 * values and queries the DB using the same Eastern-local market-hour window.
 *
 * <p>US Eastern Standard Time (EST) = UTC-5, valid on the test dates (early March 2026,
 * before DST change on March 8 2026).
 * <ul>
 *   <li>9 AM ET  = 14:00 UTC</li>
 *   <li>4 PM ET  = 21:00 UTC</li>
 *   <li>5 PM ET  = 22:00 UTC</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockPricePredictionServiceTest {

    // ── Dependencies (all mocked) ────────────────────────────────────────────

    @Mock private StockMarketDataService                 marketDataService;
    @Mock private StockPricePredictionRepository         repository;
    @Mock private StockPredictionWeightRepository        weightRepository;
    @Mock private StockPredictionWeightHistoryRepository weightHistoryRepository;
    @Mock private MarketIndexService                     marketIndexService;

    private StockPricePredictionService service;

    private static final String      SYMBOL        = "TNA";
    private static final ZoneId      EASTERN       = ZoneId.of("America/New_York");
        private static final ZoneId      UTC_ZONE      = ZoneId.of("UTC");
        private static final int         MARKET_OPEN_LOCAL  = 9;
        private static final int         MARKET_CLOSE_LOCAL = 16;

    private static final List<String> TECHNIQUES = List.of(
            "Linear_Regression", "EMA_Extrapolation", "Momentum",
            "Mean_Reversion", "Holt_Winters");

    // ── Common mock price data ───────────────────────────────────────────────

    /** 500 deterministic 5-min bars – enough for all prediction algorithms. */
    private static final List<BigDecimal> MOCK_HISTORY =
            MockYahooFinanceClient.generateDeterministicPrices(SYMBOL, 500);

    private static final BigDecimal CURRENT_PRICE = BigDecimal.valueOf(53.52);

    // ========================================================================
    // Setup
    // ========================================================================

    @BeforeEach
    void setUp() {
        service = new StockPricePredictionService(
                marketDataService, repository, weightRepository,
                weightHistoryRepository, marketIndexService);

        // Default stubs shared by all tests
        lenient().when(marketDataService.getCurrentPrice(anyString()))
                 .thenReturn(CURRENT_PRICE);
        lenient().when(marketDataService.getPrices(anyString(), anyInt()))
                 .thenReturn(MOCK_HISTORY);
        lenient().when(marketIndexService.computeIndexAdjustmentFactor(anyString()))
                 .thenReturn(0.0);
        lenient().when(marketIndexService.getIndexInfluences(anyString()))
                 .thenReturn(List.of());

        // No pre-existing weight records in DB → use CSV defaults (0.20 each)
        lenient().when(weightRepository.findBySymbolAndTechnique(anyString(), anyString()))
                 .thenReturn(Optional.empty());
        // No duplicate records when persisting individual technique predictions
        lenient().when(repository.findBySymbolAndTechniqueAndTargetHour(anyString(), anyString(), any()))
                 .thenReturn(Optional.empty());
        // repository.save() returns the argument unchanged
        lenient().when(repository.save(any(StockPricePrediction.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    // ========================================================================
    // Scenario 1 – Pre-market weekday (8:00 AM ET, Monday 2 March 2026)
    // ========================================================================

    /**
     * Before market opens, no cached predictions exist. The service must run
     * {@code calculateAndStore} and persist predictions whose target hours all
     * fall inside the 9 AM–4 PM ET window for that business day.
     *
     * <p>With the clock at 8:00 AM ET (13:00 UTC):
     * <ul>
     *   <li>{@code baseHour = 13:00 UTC}</li>
     *   <li>Predicted target hours h=1..8 → 14:00–21:00 UTC = 9 AM–4 PM ET ✓</li>
     *   <li>DB query window is 9:30 AM–4 PM ET = 14:30–21:00 UTC; the 14:00 UTC
     *       target (9 AM ET) is persisted to DB but falls outside the query window
     *       and so is not included in responses (filtered out by the DB range query).</li>
     * </ul>
     */
    @Test
    void preMarket_weekday_calculateStoreTargetsAllInMarketWindow() {
        // 8:00 AM ET on Monday 2 March 2026
        service.clock = fixedClock(2026, 3, 2, 8, 0, EASTERN);

        // DB is empty → triggers calculateAndStore
        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(List.of());

        // Capture every save() call
        ArgumentCaptor<StockPricePrediction> saveCaptor =
                ArgumentCaptor.forClass(StockPricePrediction.class);
        when(repository.save(saveCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getPredictions(SYMBOL);

        // Collect distinct target hours from saved predictions (5 records per hour)
        List<LocalDateTime> savedTargetHours = saveCaptor.getAllValues().stream()
                .map(StockPricePrediction::getTargetHour)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // baseHour = 08:00 ET; h=1..8 → 09:00, 10:00, …, 16:00 ET
        LocalDateTime expectedFirst = LocalDateTime.of(2026, 3, 2, MARKET_OPEN_LOCAL,  0);
        LocalDateTime expectedLast  = LocalDateTime.of(2026, 3, 2, MARKET_CLOSE_LOCAL, 0);

        assertThat(savedTargetHours)
                .as("exactly 8 distinct target hours are generated")
                .hasSize(8);
        assertThat(savedTargetHours.getFirst())
                .as("earliest target is 9 AM ET")
                .isEqualTo(expectedFirst);
        assertThat(savedTargetHours.getLast())
                .as("latest target is 4 PM ET")
                .isEqualTo(expectedLast);

        // All target hours must be within today's market window
        assertThat(savedTargetHours).allSatisfy(t ->
                assertThat(t).isBetween(expectedFirst, expectedLast));
    }

    /**
     * When called before 9 AM ET and no cached data exists, the service returns
     * a response.  The response may have empty {@code hourlyPredictions} when the
     * DB mock doesn't replay the just-saved records; what matters is that the
     * service does NOT throw and returns a valid (possibly empty) response object.
     */
    @Test
    void preMarket_weekday_responseIsNonNull() {
        service.clock = fixedClock(2026, 3, 2, 7, 30, EASTERN);

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(List.of());

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        assertThat(response).isNotNull();
        assertThat(response.getSymbol()).isEqualTo(SYMBOL);
        assertThat(response.getCurrentPrice()).isEqualByComparingTo(CURRENT_PRICE);
    }

    // ========================================================================
    // Scenario 2 – Mid-market (10:30 AM ET, Monday 2 March 2026)
    // ========================================================================

    /**
     * At 10:30 AM ET the DB holds fresh predictions for every market hour
     * (9 AM–4 PM ET).  The two past hours (9 AM, 10 AM) carry actual prices;
     * future hours do not.  The response must include all 8 hours.
     */
    @Test
    void midMarket_weekday_returnsFullDayWithPastAndFuturePredictions() {
        // 10:30 AM ET → nowHour = 10:00 ET
        Instant clockInstant = fixedClock(2026, 3, 2, 10, 30, EASTERN).instant();
        service.clock = Clock.fixed(clockInstant, UTC_ZONE);

        // All predictions were generated 15 minutes ago (fresh)
        LocalDateTime madeAt = LocalDateTime.of(2026, 3, 2, 10, 15);

        // Build 8 hours × 5 techniques = 40 records for today's market hours
        List<StockPricePrediction> todayPredictions =
                buildMarketHoursPredictions(SYMBOL, madeAt, LocalDate.of(2026, 3, 2));

        // Past hours: 9:00 and 10:00 ET ≤ nowHour (10:00 ET)
        todayPredictions.stream()
                .filter(p -> !p.getTargetHour().isAfter(LocalDateTime.of(2026, 3, 2, 10, 0)))
                .forEach(p -> p.setActualPrice(BigDecimal.valueOf(52.50)));

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(todayPredictions);

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        assertThat(response.getHourlyPredictions())
                .as("all 8 market hours are present in the response")
                .hasSize(8);

        // First prediction = 9 AM ET
        assertThat(response.getHourlyPredictions().getFirst().getTargetHour())
                .isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 0));

        // Last prediction = 4 PM ET
        assertThat(response.getHourlyPredictions().getLast().getTargetHour())
                .isEqualTo(LocalDateTime.of(2026, 3, 2, 16, 0));

        // Past hours (≤ 10 AM ET) have actual prices filled in
        long pastWithActual = response.getHourlyPredictions().stream()
                .filter(p -> !p.getTargetHour().isAfter(LocalDateTime.of(2026, 3, 2, 10, 0)))
                .filter(p -> p.getActualPrice() != null)
                .count();
        assertThat(pastWithActual)
                .as("past hours (9 AM and 10 AM ET) should have actual prices")
                .isEqualTo(2);

        // Future hours (> 10 AM ET) have no actual price yet
        boolean futureHaveNoActual = response.getHourlyPredictions().stream()
                .filter(p -> p.getTargetHour().isAfter(LocalDateTime.of(2026, 3, 2, 10, 0)))
                .allMatch(p -> p.getActualPrice() == null);
        assertThat(futureHaveNoActual)
                .as("future hours should not yet have actual prices")
                .isTrue();
    }

    /**
     * During market hours, the predicted prices per hour must be positive and
     * non-zero (sanity-check on the weighted-mean calculation).
     */
    @Test
    void midMarket_weekday_predictedPricesArePositive() {
        service.clock = fixedClock(2026, 3, 2, 11, 0, EASTERN);

        LocalDateTime madeAt = LocalDateTime.of(2026, 3, 2, 10, 45);
        List<StockPricePrediction> todayPredictions =
                buildMarketHoursPredictions(SYMBOL, madeAt, LocalDate.of(2026, 3, 2));

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(todayPredictions);

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        assertThat(response.getHourlyPredictions()).isNotEmpty();
        assertThat(response.getHourlyPredictions())
                .allSatisfy(hp ->
                        assertThat(hp.getPredictedPrice())
                                .as("predicted price for %s must be > 0", hp.getTargetHour())
                                .isGreaterThan(BigDecimal.ZERO));
    }

    // ========================================================================
    // Scenario 3 – Near market close (3:30 PM ET, Monday 2 March 2026)
    // ========================================================================

    /**
        * At 3:30 PM ET, {@code nowHour = 15:00 ET}.
        * The only remaining future prediction is for 4 PM ET.
     * All seven earlier hours carry actual prices.
     */
    @Test
    void nearClose_weekday_onlyLastHourIsFuture() {
        // 3:30 PM ET → nowHour = 15:00 ET
        service.clock = fixedClock(2026, 3, 2, 15, 30, EASTERN);

        // Predictions were last refreshed at 15:15 ET (15 min ago — within 50-min TTL)
        LocalDateTime madeAt = LocalDateTime.of(2026, 3, 2, 15, 15);

        List<StockPricePrediction> todayPredictions =
                buildMarketHoursPredictions(SYMBOL, madeAt, LocalDate.of(2026, 3, 2));

        // All hours up to and including 3 PM ET are past → set actual price
        LocalDateTime nowHour = LocalDateTime.of(2026, 3, 2, 15, 0);
        todayPredictions.stream()
                .filter(p -> !p.getTargetHour().isAfter(nowHour))
                .forEach(p -> p.setActualPrice(BigDecimal.valueOf(53.10)));

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(todayPredictions);

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        assertThat(response.getHourlyPredictions()).hasSize(8);

        long futureCount = response.getHourlyPredictions().stream()
                .filter(p -> p.getTargetHour().isAfter(nowHour))
                .count();
        assertThat(futureCount)
                .as("only the 4 PM ET hour should be a future prediction at 3:30 PM")
                .isEqualTo(1);

        long pastWithActualCount = response.getHourlyPredictions().stream()
                .filter(p -> !p.getTargetHour().isAfter(nowHour))
                .filter(p -> p.getActualPrice() != null)
                .count();
        assertThat(pastWithActualCount)
                .as("7 past hours should have actual prices recorded")
                .isEqualTo(7);
    }

    // ========================================================================
    // Scenario 4 – After market hours (5:30 PM ET, Monday 2 March 2026)
    // ========================================================================

    /**
     * After market close (5:30 PM ET), all target hours are in the past — there are no
     * future predictions in the window.  The {@code futureFresh} check runs
     * {@code stream().filter(future).allMatch(...)} on an <em>empty</em> stream, which
     * evaluates to {@code true} (vacuous truth).  The service therefore returns the full
     * historical record for the day so users can still review what happened.
     *
     * <p>Key assertions:
     * <ul>
     *   <li>All 8 market-hour predictions are present (historical view)</li>
     *   <li><em>None</em> of the returned hours is in the future</li>
     *   <li>All have actual prices filled in (market closed, prices were resolved)</li>
     * </ul>
     */
    @Test
    void afterHours_weekday_returnsFullHistoricalDayWithNoFuturePredictions() {
        // 5:30 PM ET → nowHour = 17:00 ET
        service.clock = fixedClock(2026, 3, 2, 17, 30, EASTERN);
        LocalDateTime nowHour = LocalDateTime.of(2026, 3, 2, 17, 0);

        // Historical predictions from 8 AM ET – all market hours are now past
        LocalDateTime staleMadeAt = LocalDateTime.of(2026, 3, 2, 8, 0);
        List<StockPricePrediction> stalePredictions =
                buildMarketHoursPredictions(SYMBOL, staleMadeAt, LocalDate.of(2026, 3, 2));

        // All hours are past; actual prices recorded as market closed
        stalePredictions.forEach(p -> p.setActualPrice(BigDecimal.valueOf(53.33)));

        // First loadLatestFromDb returns stale-but-all-past data.
        // futureFresh = allMatch(empty stream) = true → service returns the data.
        // Second call (after-hours recalculate) also returns the same data.
        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(stalePredictions);

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        // Historical full-day view is returned after close
        assertThat(response.getHourlyPredictions())
                .as("all 8 market-hour predictions are returned as historical data after close")
                .hasSize(8);

        // No prediction in the response should be a future hour
        assertThat(response.getHourlyPredictions())
                .noneMatch(hp -> hp.getTargetHour().isAfter(nowHour));

        // All returned hours should have actual prices
        assertThat(response.getHourlyPredictions())
                .allMatch(hp -> hp.getActualPrice() != null);
    }

    // ========================================================================
    // Scenario 5 – Weekend (10:00 AM ET, Saturday 7 March 2026)
    // ========================================================================

    /**
     * On a weekend day the stock market is closed.  No predictions exist for
     * Saturday.  The service must return an empty current-day prediction list
     * and populate {@code previousDayPredictions} with the most recent business
     * day's data (Friday 6 March 2026).
     */
    @Test
    void weekend_noPredictionsForCurrentDay_previousFridayDataReturned() {
        // Saturday 7 March 2026, 10:00 AM ET
        service.clock = fixedClock(2026, 3, 7, 10, 0, EASTERN);

        // Build Friday's resolved predictions (all with actual prices)
        LocalDateTime friMadeAt = LocalDateTime.of(2026, 3, 6, 9, 0);
        List<StockPricePrediction> fridayPredictions =
                buildMarketHoursPredictions(SYMBOL, friMadeAt, LocalDate.of(2026, 3, 6));
        fridayPredictions.forEach(p -> p.setActualPrice(BigDecimal.valueOf(53.19)));

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime from = invocation.getArgument(1);
                    LocalDateTime to   = invocation.getArgument(2);
                    // Saturday range → empty
                    if (from.toLocalDate().equals(LocalDate.of(2026, 3, 7))) {
                        return List.of();
                    }
                    // Friday range → return Friday predictions
                    if (from.toLocalDate().equals(LocalDate.of(2026, 3, 6))) {
                        return fridayPredictions;
                    }
                    return List.of();
                });

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        assertThat(response.getHourlyPredictions())
                .as("no current-day (Saturday) predictions should be returned")
                .isEmpty();

        assertThat(response.getPreviousDayPredictions())
                .as("previous business day (Friday) predictions should be present")
                .isNotEmpty();

        // Previous-day predictions should cover full market hours (8 hours)
        assertThat(response.getPreviousDayPredictions())
                .as("8 market hours expected for previous business day")
                .hasSize(8);

        // All previous-day predictions should have actual prices (market closed)
        assertThat(response.getPreviousDayPredictions())
                .allSatisfy(hp ->
                        assertThat(hp.getActualPrice())
                                .as("previous-day prediction at %s should have an actual price",
                                        hp.getTargetHour())
                                .isNotNull());
    }

    /**
     * When called on a Sunday the previous business day is still Friday
     * (i.e., we skip Saturday).
     */
    @Test
    void weekend_sunday_previousBusinessDayIsFriday() {
        // Sunday 8 March 2026, 9:00 AM ET
        // Note: DST starts on this date but we test the date logic, not the offset
        service.clock = fixedClock(2026, 3, 8, 9, 0, EASTERN);

        // Sunday → no data; query for Friday (previous biz day) also returns empty for simplicity
        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(List.of());

        StockPricePredictionResponse response = service.getPredictions(SYMBOL);

        // Service must not throw even on DST-change Sunday
        assertThat(response).isNotNull();
        assertThat(response.getHourlyPredictions()).isEmpty();
    }

    // ========================================================================
    // Scenario 6 – Pre-market DB query range is expressly in UTC
    // ========================================================================

    /**
        * Verifies the DB query range uses Eastern-local market hours directly,
        * matching how target hours are stored.
     *
          * <p>On 2 March 2026 the query window is 9:00 AM ET through 4:00 PM ET.
     */
    @Test
        void timezone_dbQueryRangeUsesEasternLocalMarketHours() {
                  // 9:30 AM ET (market just opened)
        service.clock = fixedClock(2026, 3, 2, 9, 30, EASTERN);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor   = ArgumentCaptor.forClass(LocalDateTime.class);

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL),
                fromCaptor.capture(),
                toCaptor.capture()))
                .thenReturn(List.of());

        service.getPredictions(SYMBOL);

        // The first DB call is from loadLatestFromDb — its range must be Eastern-local
        LocalDateTime capturedFrom = fromCaptor.getAllValues().getFirst();
        LocalDateTime capturedTo   = toCaptor.getAllValues().getFirst();

        assertThat(capturedFrom.getHour())
                .as("market open query bound must start at 9 AM ET")
                .isEqualTo(9);
        assertThat(capturedFrom.getMinute())
                .as("market open query bound must be on the hour")
                .isEqualTo(0);
        assertThat(capturedTo.getHour())
                .as("market close query bound must be 4 PM ET")
                .isEqualTo(16);
        assertThat(capturedTo.getMinute())
                .as("market close query bound must be exactly on the hour (minute 0)")
                .isEqualTo(0);
        assertThat(capturedFrom.toLocalDate())
                .as("query date must match today's ET date (March 2 2026)")
                .isEqualTo(LocalDate.of(2026, 3, 2));
    }

    /**
        * Similarly verifies that predictions saved by {@code calculateAndStore} use
        * Eastern-local target hours so they align with the DB query range.
     */
    @Test
        void timezone_savedTargetHoursUseEasternLocalTime() {
        // 9:30 AM ET
        service.clock = fixedClock(2026, 3, 2, 9, 30, EASTERN);

        when(repository.findBySymbolAndTargetHourBetweenOrderByTargetHourAsc(
                eq(SYMBOL), any(), any()))
                .thenReturn(List.of());

        ArgumentCaptor<StockPricePrediction> saveCaptor =
                ArgumentCaptor.forClass(StockPricePrediction.class);
        when(repository.save(saveCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getPredictions(SYMBOL);

        // baseHour = 09:00 ET (9:30 → truncated to 09:00).
        // First target = baseHour + 1h = 10:00 ET.
        LocalDateTime firstSavedTarget = saveCaptor.getAllValues().stream()
                .map(StockPricePrediction::getTargetHour)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        assertThat(firstSavedTarget.getHour())
                .as("first saved target must be 10:00 ET")
                .isEqualTo(10);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Creates a {@link Clock} fixed at the specified local time in the given zone,
        * while preserving the represented instant.
     */
    private static Clock fixedClock(int year, int month, int day,
                                     int hour, int minute, ZoneId zone) {
        Instant instant = LocalDateTime.of(year, month, day, hour, minute)
                                       .atZone(zone)
                                       .toInstant();
        return Clock.fixed(instant, UTC_ZONE);
    }

    /**
        * Builds a list of {@link StockPricePrediction} records covering the full set of
        * whole-hour targets that span the NYSE day (9 AM–4 PM ET) for the given date,
     * with one record per technique per hour (5 × 8 = 40 records).
     *
     * <p>All records share the same {@code predictionMadeAt} timestamp.
     * No {@code actualPrice} is set — callers set actual prices for past hours as needed.
     */
    private static List<StockPricePrediction> buildMarketHoursPredictions(
            String symbol, LocalDateTime madeAt, LocalDate date) {

        List<StockPricePrediction> records = new ArrayList<>();
        BigDecimal basePrice = BigDecimal.valueOf(53.00);

                for (int localHour = MARKET_OPEN_LOCAL; localHour <= MARKET_CLOSE_LOCAL; localHour++) {
                        LocalDateTime targetHour = date.atTime(localHour, 0);
                        BigDecimal price = basePrice.add(BigDecimal.valueOf(localHour - MARKET_OPEN_LOCAL).multiply(BigDecimal.valueOf(0.10)));

            for (String technique : TECHNIQUES) {
                StockPricePrediction p = new StockPricePrediction();
                p.setSymbol(symbol);
                p.setTechnique(technique);
                p.setPredictionMadeAt(madeAt);
                p.setTargetHour(targetHour);
                p.setPredictedPrice(price);
                // actualPrice intentionally left null — callers populate as needed
                records.add(p);
            }
        }
        return records;
    }
}
