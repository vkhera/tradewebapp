# Architecture Diagrams

Five diagrams covering deployment topology, service wiring, real-time data flow, scheduled batch processing, and observability.

---

## 1. System Deployment Topology

All Docker containers with ports, network connections, volumes, and external systems.

```mermaid
graph TB
    Browser["🌐 Browser"]
    YF["Yahoo Finance API\nquery1/query2.finance.yahoo.com"]
    DH["Docker Hub\nvkdocker/"]

    subgraph DockerCompose["Docker Compose  ·  stock-net"]
        subgraph FETier["Frontend Tier"]
            NGINX["nginx + Angular SPA\n:80 HTTP  /  :443 HTTPS\nnginx.conf  |  SSL termination"]
        end

        subgraph BETier["Backend Tier"]
            SB["Spring Boot 3.2 · Java 21\n:8080\n~16 Controllers  |  30+ Services\nActuator /metrics /health"]
        end

        subgraph DataTier["Data Tier"]
            PG["PostgreSQL 16\n:5432  ·  stockdb\n21+ tables  ·  JPA"]
            RD["Redis 7\n:6379\nSpring @Cacheable\nTTL = 10 min"]
        end

        subgraph ObsTier["Observability Stack  (docker-compose.observability.yml)"]
            PROM["Prometheus\n:9090"]
            LOKI["Loki\n:3100"]
            PT["Promtail"]
            TEMPO["Tempo\n:9411 Zipkin\n:3200 HTTP"]
            GRAF["Grafana\n:3000"]
        end

        QS["Quality Scheduler\n(quality/Dockerfile.scheduler)\nPlaywright UI · k6 load · Chaos"]
    end

    Browser -->|"HTTPS :443 / HTTP :80"| NGINX
    NGINX -->|"/api/** proxy_pass :8080"| SB

    SB -->|"JDBC / JPA"| PG
    SB -->|"Lettuce client"| RD
    SB -->|"HTTPS  v7/v8/v6 chart API"| YF

    PROM -->|"scrape /actuator/prometheus  every 15 s"| SB
    PT -->|"tail logs/ directory"| SB
    PT -->|"Loki push"| LOKI
    SB -->|"Brave/Zipkin spans  :9411"| TEMPO
    GRAF -->|query| PROM
    GRAF -->|query| LOKI
    GRAF -->|query| TEMPO

    QS -->|"Playwright E2E  :80"| NGINX
    QS -->|"k6 API tests  :8080"| SB

    DH -.->|"docker pull"| SB
    DH -.->|"docker pull"| NGINX
```

---

## 2. Backend Service Architecture

All controllers grouped by domain, wired through security and resilience layers into service groups, down to data stores and external APIs.

```mermaid
graph TB
    subgraph SecurityLayer["Security Layer"]
        JWT["JwtAuthFilter\n+ AuthService\n+ UserDetailsService"]
    end

    subgraph Controllers["REST API Controllers  (:8080/api)"]
        direction LR
        C1["AuthController\n/auth"]
        C2["PortfolioController\n/portfolio"]
        C3["TradeController\n/trades"]
        C4["MarketController\n/market"]
        C5["StockController\n/stocks"]
        C6["PredictionController\n/predictions"]
        C7["TrendController\n/trends"]
        C8["SwingTradeController\n/swing"]
        C9["SuggestedTradesController\n/suggested"]
        C10["ClientController\n/clients"]
        C11["ImportController\n/import"]
        C12["*AdminControllers\n/admin/*"]
    end

    subgraph ResilienceLayer["Resilience  (AOP)"]
        RA["ResilienceAspect\n@Around annotated endpoints"]
        TR["DynamicThrottleRegistry\nconfig/throttle-config.yaml\nper-user rate limits"]
    end

    subgraph TradingCore["Trading Core"]
        TS["TradeService\n+ LimitOrderScheduler"]
        PS["PortfolioService\n+ AccountService"]
        FD["FraudDetectionService"]
        RE["RuleEngineService\nDrools .drl rules"]
        RS["ReconciliationService"]
        AU["AuditService\nAuditLog table"]
        CS["ClientService"]
    end

    subgraph MarketData["Market & Price Data"]
        MS["MarketService\nIndices cache 1 min"]
        SS["StockPriceService"]
        YC["RealYahooFinanceClient\nin-mem cache 5 min\n4-endpoint cascade\nv7→v8(1d)→v6→v8(query2)"]
        MD["StockMarketDataService\nCSV cache 60 min\n5-min OHLCV bars"]
        OD["OptionsDataService\ncache 30 min\nATM IV · PCR · Max Pain"]
    end

    subgraph Analytics["Analytics & Predictions"]
        TA["TrendAnalysisService\nTrendAnalysisBatchService"]
        PP["StockPricePredictionBatchService\n+ PredictionScoringService"]
        SW["SwingTradeService\n+ SwingTradeTrackingService\n+ SwingTradeStrategyService\n+ AtrService"]
        SG["SuggestedTradesService\n+ SuggestedTradeTrackingService"]
        TRS["TradingRulesService"]
    end

    subgraph DataLayer["Data Layer"]
        PG["PostgreSQL 16\nstockdb\n(21+ JPA Repositories)"]
        RD["Redis 7\nSpring @Cacheable"]
        FS["CSV Files\nstock_predictions/\ntrend_predictions/"]
        YF["Yahoo Finance API\n(external HTTPS)"]
    end

    JWT --> C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C10

    C3 --> RA --> TS
    C2 --> PS
    C4 --> MS
    C5 --> SS
    C6 --> PP
    C7 --> TA
    C8 --> SW
    C9 --> SG
    C10 --> CS

    TS --> FD --> RE
    TS --> PS & AU & RS

    PS --> SS
    MS --> YC
    SS --> YC
    MD --> YC
    OD --> YC
    YC --> YF

    TA --> MD & OD & MS
    PP --> MD & SS & FS
    SW --> MD & TA
    SG --> TA & TRS

    TS --> PG
    PS --> PG & RD
    CS --> PG & RD
    TA --> PG & FS
    PP --> PG & FS
    SW --> PG
    AU --> PG
    RS --> PG
```

---

## 3. Real-time Data Flow (User-triggered)

End-to-end flows for the four main user interactions, with cache hit/miss decision points and TTL labels.

```mermaid
flowchart TD
    User(["User / Browser"])

    subgraph PortfolioFlow["Portfolio Load  GET /api/portfolio/client/{id}/summary"]
        PF1["PortfolioController"]
        PF2["PortfolioService\nconvertToResponse()"]
        PF3{"Redis cache\nhit?"}
        PF4["PostgreSQL\nload holdings"]
        PF5{"Price cache\nhit?  TTL 5 min"}
        PF6["YahooFinanceClient\nGET v7/quote\n→ v8/chart 1d fallbacks"]
        PF7{"Post-market\nwindow?  4-8 PM ET"}
        PF8["YahooFinanceClient\nGET v8/chart 1m\n(walk back timestamps)"]
        PF9["Return portfolio\nwith prices + P&L\n+ postMarketPrice"]
    end

    subgraph TradeFlow["Place Trade  POST /api/trades"]
        TF1["TradeController"]
        TF2["ResilienceAspect\nthrottle-config.yaml\nrate-limit check"]
        TF3["TradeService\nexecuteTrade()"]
        TF4["FraudDetection\n+ Drools rules eval"]
        TF5{"Rules\npassed?"}
        TF6["PortfolioService\ndebit/credit cash"]
        TF7["AuditService\nwrite AuditLog"]
        TF8["ReconciliationService\nbalance check"]
        TF9X["Reject trade\n403 / error"]
        TF9["Trade persisted\nPostgreSQL → Redis evict"]
    end

    subgraph MarketFlow["Market Indices  GET /api/market/indices"]
        MF1["MarketController"]
        MF2{"Indices cache\nhit?  TTL 1 min"}
        MF3["MarketService\nfetchIndexQuote()"]
        MF4["YahooFinanceClient\nGET v8/chart 1d  ×5 symbols"]
        MF5["getPostMarketPrice()\nv8/chart 1m  ×5 symbols"]
        MF6["Return ^GSPC ^DJI ^IXIC GC=F ^RUT\nwith price + postMarketPrice"]
    end

    subgraph PredictFlow["Price Prediction  GET /api/predictions/{sym}"]
        PR1["PredictionController"]
        PR2{"DB fresh?\n< 50 min old"}
        PR3["PostgreSQL\nstock_price_prediction table"]
        PR4["Return 8-hour\nhourly forecast array"]
    end

    User -->|"load dashboard"| PF1
    PF1 --> PF2 --> PF3
    PF3 -->|"MISS"| PF4
    PF3 -->|"HIT"| PF9
    PF4 --> PF5
    PF5 -->|"HIT"| PF9
    PF5 -->|"MISS"| PF6
    PF6 --> PF7
    PF7 -->|"YES"| PF8 --> PF9
    PF7 -->|"NO"| PF9

    User -->|"buy / sell"| TF1
    TF1 --> TF2
    TF2 -->|"rate-limited"| TF9X
    TF2 -->|"allowed"| TF3
    TF3 --> TF4 --> TF5
    TF5 -->|"FAIL"| TF9X
    TF5 -->|"PASS"| TF6 --> TF7 --> TF8 --> TF9

    User -->|"view market bar"| MF1
    MF1 --> MF2
    MF2 -->|"HIT"| MF6
    MF2 -->|"MISS"| MF3 --> MF4 --> MF5 --> MF6

    User -->|"open prediction chart"| PR1
    PR1 --> PR2
    PR2 -->|"FRESH"| PR3 --> PR4
    PR2 -->|"STALE → wait for\nnext hourly batch"| PR4
```

---

## 4. Scheduled Batch Processing Pipeline

All 9 schedulers grouped by frequency, showing what Yahoo Finance endpoints they call and where they write output.

```mermaid
flowchart LR
    YF(["Yahoo Finance API"])
    PG(["PostgreSQL"])
    FS(["CSV Files\nstock_predictions/\ntrend_predictions/"])

    subgraph Every1min["Every  1 min"]
        R1["ReconciliationService\nreconcileAllPortfolios()\nVerify cash + holding balances\nFix discrepancies in DB"]
        HB["SystemHeartbeatService\nwrite timestamp\nto system_heartbeat table"]
    end

    subgraph Every5min["Every  5 min"]
        LO["LimitOrderScheduler\ncheckAndExecuteLimitOrders()\nScan open limit orders\nExecute if price crossed trigger\n→ TradeService.executeTrade()"]
    end

    subgraph Every10min["Every  10 min  (+10 s startup)"]
        TA["TrendAnalysisBatchService\nrunTrendAnalysis()\nFor each tracked symbol:\n  7 techniques: MA/RSI/MACD/\n  Momentum/Volume/\n  IndexMomentum/OptionsSentiment\nWeighted vote → BULLISH/BEARISH/NEUTRAL\nAdaptive weight update\nSave to PostgreSQL + CSV"]
    end

    subgraph Every1hr["Every  1 hour  (+30 s startup)"]
        PP1["StockPricePredictionBatchService\ngeneratePredictions()\nFor each symbol:\n  Fetch 5-min bars (CSV or Yahoo)\n  ARIMA + weighted features\n  Generate 8h × hourly forecast\n  Save to stock_price_prediction table"]
        PP2["resolveActuals()\n  Compare past predictions vs actual price\n  Calculate accuracy score\n  Update StockPredictionWeight\n  (adaptive learning)"]
    end

    subgraph Daily2AM["Daily  2:00 AM  ET"]
        DS["DataSyncBatchService\nsyncHistoricalPrices()\nFetch 60-day 1d bars for all symbols\nUpsert into PostgreSQL\n(StockPriceCache table)"]
    end

    subgraph Daily6AM["Daily  6:00 AM"]
        SG["SuggestedTradeTrackingService\ncheckPendingSuggestions()\nEvaluate open trade suggestions\nvs current price / trend\nUpdate status in DB"]
    end

    subgraph Daily630AM["Daily  6:30 AM"]
        SW["SwingTradeTrackingService\ncheckSwingTradeOutcomes()\nEvaluate open swing predictions\nvs actual price movement\nUpdate weights + P&L in DB"]
    end

    subgraph Daily6PM["Daily  6:00 PM  ET  (weekdays)"]
        SC["PredictionScoringService\nscoreYesterdaysPredictions()\nFetch yesterday's predictions\nCompare vs closing prices\nCompute MAE / direction accuracy\nSave PredictionDailyScore"]
    end

    YF -->|"v8/chart 5m 60d  ×N symbols"| TA
    YF -->|"v8/chart 5m 60d + current price"| PP1
    YF -->|"v8/chart 1d  ×all symbols"| DS
    YF -->|"current price per symbol"| LO

    TA --> PG
    TA --> FS
    PP1 --> PG
    PP1 --> FS
    PP2 --> PG
    DS --> PG
    SG --> PG
    SW --> PG
    SC --> PG
    R1 --> PG
    HB --> PG
    LO --> PG
```

---

## 5. Observability & Monitoring Stack

Three signal pipelines (metrics, logs, traces) converging into Grafana, plus the quality scheduler for automated testing.

```mermaid
graph TB
    subgraph App["Application (Spring Boot)"]
        ACT["Actuator\n/actuator/prometheus\n/actuator/health\n/actuator/info"]
        BRAVE["Brave Tracer\n(spring-cloud-sleuth)\nHTTP span instrumentation"]
        LOG["Logback JSON\nlogs/ directory\n(stock-brokerage.json\nbatch-runs.txt)"]
        METER["Micrometer\nCustom metrics:\ntrades_executed_total\nportfolio_value_gauge\napi_request_duration"]
    end

    subgraph MetricsPipeline["Metrics Pipeline"]
        PROM["Prometheus\n:9090\nScrapes every 15 s\nRetention: 15 days\nRules: alerting.yml"]
    end

    subgraph LogPipeline["Log Pipeline"]
        PT["Promtail\npromtail/config.yml\nTails logs/ directory\nAdds labels: job, instance"]
        LOKI["Loki\n:3100\nLog aggregation\nLabel-based indexing"]
    end

    subgraph TracePipeline["Trace Pipeline"]
        TEMPO["Tempo\n:9411  Zipkin receiver\n:3200  HTTP API\nTrace storage + search"]
    end

    subgraph Visualization["Visualization"]
        GRAF["Grafana\n:3000\nDashboards:\n• JVM / Spring metrics\n• Trade activity\n• Prediction accuracy\n• System health"]
    end

    METER --> ACT
    ACT -->|"GET /actuator/prometheus\nevery 15 s"| PROM
    BRAVE -->|"Zipkin spans POST :9411"| TEMPO
    LOG -->|"tail file"| PT
    PT -->|"Loki remote write\nHTTP :3100"| LOKI

    PROM --> GRAF
    LOKI --> GRAF
    TEMPO --> GRAF

    PROM -.->|"Alertmanager\n(if configured)"| ALERT["Alerts\n(email / Slack)"]

    style GRAF fill:#f46800,color:#fff
    style PROM fill:#e6522c,color:#fff
    style LOKI fill:#f5a623,color:#000
    style TEMPO fill:#7b61ff,color:#fff
```
