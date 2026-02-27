package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.MarketIndexWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexWeightRepository extends JpaRepository<MarketIndexWeight, Long> {

    List<MarketIndexWeight> findBySymbol(String symbol);

    Optional<MarketIndexWeight> findBySymbolAndIndexSymbol(String symbol, String indexSymbol);

    /**
     * Insert or update a weight row.  Uses PostgreSQL's ON CONFLICT clause so the
     * call is idempotent regardless of whether the row already exists.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO market_index_weight (symbol, index_symbol, weight, correlation, last_updated)
        VALUES (:symbol, :indexSymbol, :weight, :correlation, :lastUpdated)
        ON CONFLICT ON CONSTRAINT uq_miw_symbol_index
        DO UPDATE SET weight       = EXCLUDED.weight,
                      correlation  = EXCLUDED.correlation,
                      last_updated = EXCLUDED.last_updated
        """)
    void upsertWeight(@Param("symbol")      String symbol,
                      @Param("indexSymbol") String indexSymbol,
                      @Param("weight")      double weight,
                      @Param("correlation") double correlation,
                      @Param("lastUpdated") LocalDate lastUpdated);
}
