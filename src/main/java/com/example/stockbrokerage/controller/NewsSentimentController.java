package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.EtfChangeDto;
import com.example.stockbrokerage.dto.NewsSentimentDto;
import com.example.stockbrokerage.entity.NewsSentimentAnalysis;
import com.example.stockbrokerage.repository.NewsSentimentAnalysisRepository;
import com.example.stockbrokerage.service.EtfActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/news-sentiment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "News Sentiment", description = "News sentiment analysis results and ETF holdings activity")
@CrossOrigin(origins = "*")
public class NewsSentimentController {

    private final NewsSentimentAnalysisRepository repository;
    private final EtfActivityService etfActivityService;

    /**
     * Returns news sentiment records published within the last {@code lookbackDays} days.
     * Optionally filtered by stock symbol.
     */
    @GetMapping("/recent")
    @Operation(summary = "Get recent news sentiment analysis",
               description = "Returns LLM-analyzed news items from the last N days (default 1 = yesterday).")
    public ResponseEntity<List<NewsSentimentDto>> getRecent(
            @RequestParam(defaultValue = "1") int lookbackDays,
            @RequestParam(required = false)  String symbol) {

        LocalDateTime start = LocalDate.now().minusDays(lookbackDays).atStartOfDay();
        LocalDateTime end   = LocalDateTime.now();

        List<NewsSentimentAnalysis> entities;
        if (symbol != null && !symbol.isBlank()) {
            entities = repository.findBySymbolIgnoreCaseAndPublishedAtBetweenOrderByPublishedAtDesc(
                    symbol.trim().toUpperCase(), start, end);
        } else {
            entities = repository.findByPublishedAtBetweenOrderByPublishedAtDesc(start, end);
        }

        List<NewsSentimentDto> dtos = entities.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Returns all news sentiment records for a given symbol (no date filter), newest first.
     */
    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get all news sentiment for a symbol")
    public ResponseEntity<List<NewsSentimentDto>> getBySymbol(@PathVariable String symbol) {
        List<NewsSentimentAnalysis> entities =
                repository.findBySymbolIgnoreCaseOrderByPublishedAtDesc(symbol.trim().toUpperCase());
        return ResponseEntity.ok(entities.stream().map(this::toDto).toList());
    }

    /**
     * Returns ETF holdings changes (BUZZ, HDGE, MMTM) for a given stock symbol
     * from the local ETF dashboard service.
     */
    @GetMapping("/etf-activity/{symbol}")
    @Operation(summary = "Get ETF activity for a stock",
               description = "Queries the local ETF dashboard for BUZZ/HDGE/MMTM holdings changes affecting this stock.")
    public ResponseEntity<List<EtfChangeDto>> getEtfActivity(@PathVariable String symbol) {
        List<EtfChangeDto> changes = etfActivityService.getChangesForSymbol(symbol);
        return ResponseEntity.ok(changes);
    }

    /**
     * Returns all recent ETF holdings changes (BUZZ, HDGE, MMTM) across all stocks.
     */
    @GetMapping("/etf-activity")
    @Operation(summary = "Get all recent ETF holdings changes")
    public ResponseEntity<List<EtfChangeDto>> getAllEtfActivity() {
        return ResponseEntity.ok(etfActivityService.getAllRecentChanges());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private NewsSentimentDto toDto(NewsSentimentAnalysis e) {
        return new NewsSentimentDto(
                e.getId(),
                e.getSymbol(),
                e.getTitle(),
                e.getSummary(),
                e.getPublisher(),
                e.getArticleUrl(),
                e.getPublishedAt(),
                e.getSentiment() != null ? e.getSentiment().name() : null,
                e.getSentimentConfidence(),
                e.getAnalysisReason(),
                e.getLlmModel(),
                e.getAnalyzedAt()
        );
    }
}
