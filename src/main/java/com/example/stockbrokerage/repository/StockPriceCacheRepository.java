package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.StockPriceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StockPriceCacheRepository extends JpaRepository<StockPriceCache, Long> {

    /** Latest bar timestamp for the symbol – used to determine which bars are new. */
    @Query("SELECT MAX(s.barTime) FROM StockPriceCache s WHERE s.symbol = :symbol")
    Optional<LocalDateTime> findLatestBarTime(@Param("symbol") String symbol);

    /** Insert a single bar, silently ignoring conflicts on (symbol, bar_time). */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value =
        "INSERT INTO stock_price_cache (symbol, bar_time, close_price, synced_at) " +
        "VALUES (:symbol, :barTime, :closePrice, :syncedAt) " +
        "ON CONFLICT (symbol, bar_time) DO NOTHING")
    void upsertBar(@Param("symbol") String symbol,
                   @Param("barTime") LocalDateTime barTime,
                   @Param("closePrice") BigDecimal closePrice,
                   @Param("syncedAt") LocalDateTime syncedAt);

    long countBySymbol(String symbol);

    /** Last 5-minute bar for a symbol on a given trading day. */
    @Query("SELECT s FROM StockPriceCache s WHERE s.symbol = :symbol " +
           "AND s.barTime >= :dayStart AND s.barTime < :dayEnd " +
           "ORDER BY s.barTime DESC LIMIT 1")
    Optional<StockPriceCache> findLastBarOfDay(@Param("symbol") String symbol,
                                               @Param("dayStart") LocalDateTime dayStart,
                                               @Param("dayEnd") LocalDateTime dayEnd);

    /** Last 5-minute bar for a symbol *before* a given timestamp (for previous-day close). */
    @Query("SELECT s FROM StockPriceCache s WHERE s.symbol = :symbol " +
           "AND s.barTime >= :from AND s.barTime < :to " +
           "ORDER BY s.barTime DESC LIMIT 1")
    Optional<StockPriceCache> findLastBarBefore(@Param("symbol") String symbol,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);
}
