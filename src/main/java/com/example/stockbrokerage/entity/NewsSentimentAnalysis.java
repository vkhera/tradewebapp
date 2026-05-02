package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_sentiment_analysis", uniqueConstraints = {
    @UniqueConstraint(name = "uq_nsa_symbol_external", columnNames = {"symbol", "external_news_id"})
}, indexes = {
    @Index(name = "idx_nsa_symbol_published", columnList = "symbol, published_at"),
    @Index(name = "idx_nsa_analyzed_at", columnList = "analyzed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsSentimentAnalysis {

    public enum Sentiment {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "external_news_id", nullable = false, length = 120)
    private String externalNewsId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(length = 120)
    private String publisher;

    @Column(name = "article_url", length = 1000)
    private String articleUrl;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sentiment sentiment;

    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    @Column(name = "analysis_reason", length = 2000)
    private String analysisReason;

    @Column(name = "llm_model", nullable = false, length = 80)
    private String llmModel;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.analyzedAt == null) {
            this.analyzedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
