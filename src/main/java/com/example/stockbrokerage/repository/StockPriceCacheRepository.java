package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.StockPriceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StockPriceCacheRepository extends JpaRepository<StockPriceCache, Long> {

    /** Latest bar timestamp for the symbol – used to determine which bars are new. */
    @Query("SELECT MAX(s.barTime) FROM StockPriceCache s WHERE s.symbol = :symbol")
    Optional<LocalDateTime> findLatestBarTime(@Param("symbol") String symbol);

       boolean existsBySymbolAndBarTime(String symbol, LocalDateTime barTime);

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

       /** Removes bars older than the provided timestamp and returns deleted row count. */
       @Transactional
       @Modifying
       @Query("DELETE FROM StockPriceCache s WHERE s.barTime < :cutoff")
       int deleteByBarTimeBefore(@Param("cutoff") LocalDateTime cutoff);
}
