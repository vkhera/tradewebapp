package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.NewsSentimentAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsSentimentAnalysisRepository extends JpaRepository<NewsSentimentAnalysis, Long> {

    boolean existsBySymbolAndExternalNewsId(String symbol, String externalNewsId);

        Optional<NewsSentimentAnalysis> findBySymbolAndExternalNewsId(String symbol, String externalNewsId);

    long countBySymbolAndPublishedAtAfterAndSentiment(String symbol,
                                                      LocalDateTime publishedAfter,
                                                      NewsSentimentAnalysis.Sentiment sentiment);

    List<NewsSentimentAnalysis> findByPublishedAtBetweenOrderByPublishedAtDesc(
            LocalDateTime start, LocalDateTime end);

    List<NewsSentimentAnalysis> findBySymbolIgnoreCaseAndPublishedAtBetweenOrderByPublishedAtDesc(
            String symbol, LocalDateTime start, LocalDateTime end);

    List<NewsSentimentAnalysis> findBySymbolIgnoreCaseOrderByPublishedAtDesc(String symbol);
}
