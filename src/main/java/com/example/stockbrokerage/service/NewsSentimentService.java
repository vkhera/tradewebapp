package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import com.example.stockbrokerage.dto.NewsItem;
import com.example.stockbrokerage.dto.TrendPrediction.TrendDirection;
import com.example.stockbrokerage.entity.NewsSentimentAnalysis;
import com.example.stockbrokerage.entity.NewsSentimentAnalysis.Sentiment;
import com.example.stockbrokerage.repository.NewsSentimentAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsSentimentService {

    private static final int DEFAULT_LOOKBACK_DAYS = 5;

    private final YahooFinanceClient yahooFinanceClient;
    private final NewsSentimentAnalysisRepository sentimentRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.news-analysis.ollama.url:http://localhost:11434/api/chat}")
    private String ollamaUrl;

    @Value("${app.news-analysis.ollama.fallback-url:http://host.docker.internal:11434/api/chat}")
    private String ollamaFallbackUrl;

    @Value("${app.news-analysis.ollama.model:gemma3:1b}")
    private String ollamaModel;

    @Value("${app.news-analysis.ollama.temperature:0.2}")
    private double ollamaTemperature;

    @Value("${app.news-analysis.ollama.max-retries:2}")
    private int maxRetries;

    @Value("${app.news-analysis.ollama.retry-delay-ms:350}")
    private long retryDelayMs;

    @Value("${app.news-analysis.ollama.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.news-analysis.ollama.read-timeout-ms:15000}")
    private int readTimeoutMs;

    public int analyzeFreshNewsForSymbol(String symbol, int lookbackDays) {
        String normalizedSymbol = normalizeSymbol(symbol);
        int effectiveLookback = lookbackDays > 0 ? lookbackDays : DEFAULT_LOOKBACK_DAYS;
        List<NewsItem> newsItems = yahooFinanceClient.getRecentNews(normalizedSymbol, effectiveLookback);

        if (newsItems.isEmpty()) {
            return 0;
        }

        int analyzed = 0;
        for (NewsItem item : newsItems) {
            Optional<NewsSentimentAnalysis> existing = sentimentRepository
                .findBySymbolAndExternalNewsId(normalizedSymbol, item.externalId());

            if (existing.isPresent() && !isRetryableFailure(existing.get().getAnalysisReason())) {
                continue;
            }

            AnalysisResult analysis = analyzeViaOllama(item);
            persist(item, analysis, existing.orElse(null));
            analyzed++;
        }

        return analyzed;
    }

    public TrendDirection calculateNewsTrend(String symbol, int lookbackDays) {
        String normalizedSymbol = normalizeSymbol(symbol);
        int effectiveLookback = lookbackDays > 0 ? lookbackDays : DEFAULT_LOOKBACK_DAYS;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(effectiveLookback);

        long positives = sentimentRepository.countBySymbolAndPublishedAtAfterAndSentiment(
            normalizedSymbol, cutoff, Sentiment.POSITIVE);
        long negatives = sentimentRepository.countBySymbolAndPublishedAtAfterAndSentiment(
            normalizedSymbol, cutoff, Sentiment.NEGATIVE);
        long neutrals = sentimentRepository.countBySymbolAndPublishedAtAfterAndSentiment(
            normalizedSymbol, cutoff, Sentiment.NEUTRAL);

        long total = positives + negatives + neutrals;
        if (total == 0L) {
            return TrendDirection.SIDEWAYS;
        }

        double score = (double) (positives - negatives) / total;
        if (score >= 0.20) {
            return TrendDirection.UPTREND;
        }
        if (score <= -0.20) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
    }

    private void persist(NewsItem item, AnalysisResult analysis, NewsSentimentAnalysis existing) {
        NewsSentimentAnalysis entity = existing != null ? existing : new NewsSentimentAnalysis();
        entity.setSymbol(item.symbol());
        entity.setExternalNewsId(item.externalId());
        entity.setTitle(trimTo(item.title(), 500));
        entity.setSummary(trimTo(item.summary(), 1000));
        entity.setPublisher(trimTo(item.publisher(), 120));
        entity.setArticleUrl(trimTo(item.link(), 1000));
        entity.setPublishedAt(item.publishedAt() == null ? LocalDateTime.now() : item.publishedAt());
        entity.setSentiment(analysis.sentiment());
        entity.setSentimentConfidence(analysis.confidence());
        entity.setAnalysisReason(trimTo(analysis.reason(), 2000));
        entity.setLlmModel(ollamaModel);
        entity.setAnalyzedAt(LocalDateTime.now());

        try {
            sentimentRepository.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Duplicate: another thread or a previous run already stored this news item — safe to skip
            log.debug("Skipping duplicate news item [{}/{}]: {}", item.symbol(), item.externalId(), ex.getMessage());
        }
    }

    private boolean isRetryableFailure(String reason) {
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("llm call failed");
    }

    private AnalysisResult analyzeViaOllama(NewsItem item) {
        String prompt = buildPrompt(item);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", ollamaModel);
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        payload.put("stream", false);
        payload.put("temperature", ollamaTemperature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        List<String> candidateUrls = resolveCandidateUrls();
        int totalAttempts = Math.max(0, maxRetries) + 1;
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            for (String url : candidateUrls) {
                try {
                    String response = buildRestTemplate().postForObject(url, request, String.class);

                    if (response == null || response.isBlank()) {
                        return new AnalysisResult(Sentiment.NEUTRAL, 0.50, "Empty LLM response");
                    }

                    JsonNode root = objectMapper.readTree(response);
                    String content = root.path("message").path("content").asText("");
                    if (content.isBlank()) {
                        return new AnalysisResult(Sentiment.NEUTRAL, 0.50, "Missing content in LLM response");
                    }
                    return parseOllamaContent(content);
                } catch (Exception ex) {
                    lastException = ex;
                    log.warn(
                        "Ollama analysis attempt {}/{} failed for {} via {}: {}",
                        attempt,
                        totalAttempts,
                        item.symbol(),
                        url,
                        conciseError(ex)
                    );
                }
            }

            if (attempt < totalAttempts && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.warn("Ollama analysis failed for {} after {} attempt(s)", item.symbol(), totalAttempts);
        return new AnalysisResult(Sentiment.NEUTRAL, 0.50, "LLM call failed after retries");
    }

    private AnalysisResult parseOllamaContent(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            Sentiment sentiment = parseSentiment(parsed.path("sentiment").asText("NEUTRAL"));
            double confidence = clamp(parsed.path("confidence").asDouble(0.50));
            String reason = parsed.path("reason").asText("No reason provided");
            return new AnalysisResult(sentiment, confidence, reason);
        } catch (Exception ignored) {
            String normalized = content.toUpperCase(Locale.ROOT);
            if (normalized.contains("POSITIVE")) {
                return new AnalysisResult(Sentiment.POSITIVE, 0.60, trimTo(content, 2000));
            }
            if (normalized.contains("NEGATIVE")) {
                return new AnalysisResult(Sentiment.NEGATIVE, 0.60, trimTo(content, 2000));
            }
            return new AnalysisResult(Sentiment.NEUTRAL, 0.50, trimTo(content, 2000));
        }
    }

    private Sentiment parseSentiment(String raw) {
        String normalized = raw == null ? "NEUTRAL" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSITIVE" -> Sentiment.POSITIVE;
            case "NEGATIVE" -> Sentiment.NEGATIVE;
            default -> Sentiment.NEUTRAL;
        };
    }

    private String buildPrompt(NewsItem item) {
        return "Classify this stock news from an investment perspective.\n"
            + "Return ONLY JSON with keys sentiment, confidence, reason.\n"
            + "sentiment must be POSITIVE, NEGATIVE, or NEUTRAL.\n"
            + "confidence must be between 0 and 1.\n"
            + "Keep reason under 30 words.\n\n"
            + "Ticker: " + item.symbol() + "\n"
            + "Title: " + trimTo(nullToEmpty(item.title()), 300) + "\n"
            + "Summary: " + trimTo(nullToEmpty(item.summary()), 1200) + "\n"
            + "Publisher: " + nullToEmpty(item.publisher()) + "\n"
            + "Published: " + item.publishedAt();
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
        return new RestTemplate(requestFactory);
    }

    private List<String> resolveCandidateUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(ollamaUrl);

        // Common Docker case: app runs in container and localhost points to itself.
        if (ollamaUrl != null && ollamaUrl.contains("localhost")
            && ollamaFallbackUrl != null && !ollamaFallbackUrl.isBlank()
            && !ollamaFallbackUrl.equalsIgnoreCase(ollamaUrl)) {
            urls.add(ollamaFallbackUrl);
        }
        return urls;
    }

    private String conciseError(Exception ex) {
        if (ex instanceof HttpStatusCodeException httpEx) {
            String body = trimTo(httpEx.getResponseBodyAsString(), 180);
            return httpEx.getStatusCode() + (body == null || body.isBlank() ? "" : " body=" + body);
        }
        if (ex instanceof ResourceAccessException) {
            return "resource access error: " + ex.getMessage();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private String trimTo(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record AnalysisResult(Sentiment sentiment, double confidence, String reason) {
    }
}
