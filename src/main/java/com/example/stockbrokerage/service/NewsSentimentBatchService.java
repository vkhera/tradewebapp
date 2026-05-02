package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsSentimentBatchService {

    public static final String JOB_NAME = "NEWS_SENTIMENT_BATCH";

    private final PortfolioRepository portfolioRepository;
    private final EtfSymbolService etfSymbolService;
    private final NewsSentimentService newsSentimentService;
    private final JobTrackerService jobTracker;

    @Value("${app.news-analysis.lookback-days:5}")
    private int lookbackDays;

    @Scheduled(cron = "${app.news-analysis.cron:0 15 2 * * *}")
    public void runDailyNewsAnalysis() {
        log.info("Starting scheduled news sentiment batch job");
        JobExecutionRecord job = jobTracker.startJob(JOB_NAME, LocalDateTime.now());

        try {
            List<Portfolio> holdings = portfolioRepository.findAll();
            if (holdings.isEmpty()) {
                log.info("No portfolio holdings found, skipping news sentiment batch");
                jobTracker.completeJob(job);
                return;
            }

            Set<String> symbols = holdings.stream()
                .map(Portfolio::getSymbol)
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .filter(s -> !etfSymbolService.isEtf(s))
                .collect(Collectors.toSet());

            if (symbols.isEmpty()) {
                log.info("Portfolio only contains ETFs, skipping news sentiment batch");
                jobTracker.completeJob(job);
                return;
            }

            int analyzedCount = 0;
            for (String symbol : symbols) {
                try {
                    analyzedCount += newsSentimentService.analyzeFreshNewsForSymbol(symbol, lookbackDays);
                } catch (Exception ex) {
                    log.warn("News sentiment analysis failed for {}: {}", symbol, ex.getMessage());
                }
            }

            log.info("Completed news sentiment batch for {} symbols ({} new articles analyzed)",
                symbols.size(), analyzedCount);
            jobTracker.completeJob(job);
        } catch (Exception ex) {
            jobTracker.failJob(job, ex.getMessage());
            log.error("Error in scheduled news sentiment batch", ex);
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
