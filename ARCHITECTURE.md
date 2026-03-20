# Architecture Diagrams

Eleven diagrams and models: deployment topology, service architecture, real-time data flow, scheduled batch processing, observability stack, use cases for clients, admins, and the automated scheduler, plus a security architecture view, a STRIDE threat model, and a data-classification security matrix.

## Table of Contents

1. [System Deployment Topology](#1-system-deployment-topology)
2. [Backend Service Architecture](#2-backend-service-architecture)
3. [Real-time Data Flow](#3-real-time-data-flow-user-triggered)
4. [Scheduled Batch Processing](#4-scheduled-batch-processing-pipeline)
5. [Observability & Monitoring](#5-observability--monitoring-stack)
6. [Client / Investor Use Cases](#6-client--investor-use-cases)
7. [Admin Use Cases](#7-admin-use-cases)
8. [System Scheduler Use Cases](#8-system-scheduler-use-cases)
9. [Security Architecture Diagram](#9-security-architecture-diagram)
10. [Threat Model (STRIDE)](#10-threat-model-stride)
11. [Data Classification & Encryption Controls](#11-data-classification--encryption-controls)

---

## 1. System Deployment Topology

All containers with ports, network connections, and external systems. Databases use cylinder shapes; external services use pill shapes.

```mermaid
graph TB
    classDef browser fill:#0D47A1,stroke:#01579B,color:#fff
    classDef frontend fill:#0277BD,stroke:#01579B,color:#fff
    classDef backend fill:#2E7D32,stroke:#1B5E20,color:#fff
    classDef database fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef cache fill:#C62828,stroke:#B71C1C,color:#fff
    classDef external fill:#6A1B9A,stroke:#4A148C,color:#fff
    classDef observability fill:#E65100,stroke:#BF360C,color:#fff
    classDef quality fill:#4527A0,stroke:#311B92,color:#fff

    Browser(["🌐 Browser"]):::browser
    YF(["☁️ Yahoo Finance\nquery1 / query2.finance.yahoo.com"]):::external
    DH(["🐋 Docker Hub  vkdocker/"]):::external

    subgraph DockerCompose["🐋 Docker Compose · stock-net"]
        subgraph FETier["Frontend Tier"]
            NGINX["🔀 nginx\n:80 HTTP · :443 HTTPS\n📦 Angular 17 SPA  ·  SSL termination"]:::frontend
        end

        subgraph BETier["Backend Tier"]
            SB["🌿 Spring Boot 3.2 · ☕ Java 21\n:8080\n16 Controllers · 30+ Services\n📊 /actuator/metrics /health"]:::backend
        end

        subgraph DataTier["Data Tier"]
            PG[("🐘 PostgreSQL 16\n:5432 · stockdb\n21+ tables · JPA")]:::database
            RD[("⚡ Redis 7\n:6379\nSpring @Cacheable · TTL 10 min")]:::cache
        end

        subgraph ObsTier["Observability Stack"]
            PROM["🔥 Prometheus\n:9090\nscrape every 15 s"]:::observability
            LOKI["📋 Loki\n:3100\nlog aggregation"]:::observability
            PT["Promtail\ntail logs/"]:::observability
            TEMPO["🔮 Tempo\n:9411 Zipkin · :3200"]:::observability
            GRAF["📊 Grafana\n:3000\ndashboards"]:::observability
        end

        QS["🧪 Quality Scheduler\nPlaywright · k6 · Chaos"]:::quality
    end

    Browser -->|"HTTPS :443 / HTTP :80"| NGINX
    NGINX -->|"/api/** → :8080"| SB
    SB -->|"JDBC / JPA"| PG
    SB -->|"Lettuce client"| RD
    SB -->|"HTTPS REST"| YF
    PROM -->|"GET /actuator/prometheus  every 15 s"| SB
    PT -->|"tail logs/"| SB
    PT -->|"Loki push"| LOKI
    SB -->|"Zipkin spans :9411"| TEMPO
    GRAF -->|query| PROM
    GRAF -->|query| LOKI
    GRAF -->|query| TEMPO
    QS -->|"Playwright E2E :80"| NGINX
    QS -->|"k6 API tests :8080"| SB
    DH -.->|"docker pull"| SB
    DH -.->|"docker pull"| NGINX

    style FETier fill:#E3F2FD,stroke:#1565C0
    style BETier fill:#E8F5E9,stroke:#1B5E20
    style DataTier fill:#FCE4EC,stroke:#880E4F
    style ObsTier fill:#FFF3E0,stroke:#E65100
```

---

## 2. Backend Service Architecture

Controllers route through JWT security and AOP resilience into domain service groups, down to data stores and external APIs.

```mermaid
graph TB
    classDef security fill:#B71C1C,stroke:#7F0000,color:#fff
    classDef controller fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef resilience fill:#E65100,stroke:#BF360C,color:#fff
    classDef trading fill:#00695C,stroke:#004D40,color:#fff
    classDef market fill:#2E7D32,stroke:#1B5E20,color:#fff
    classDef analytics fill:#4527A0,stroke:#311B92,color:#fff
    classDef datastore fill:#37474F,stroke:#263238,color:#fff
    classDef external fill:#6A1B9A,stroke:#4A148C,color:#fff

    subgraph SecurityLayer["🔒 Security Layer"]
        JWT["🔐 JwtAuthFilter\n+ AuthService\n+ UserDetailsService"]:::security
    end

    subgraph Controllers["🎮 REST Controllers  (:8080/api)"]
        direction LR
        C2["📊 PortfolioController\n/portfolio"]:::controller
        C3["💹 TradeController\n/trades"]:::controller
        C4["📈 MarketController\n/market"]:::controller
        C5["📉 StockController\n/stocks"]:::controller
        C6["🔮 PredictionController\n/predictions"]:::controller
        C7["📡 TrendController\n/trends"]:::controller
        C8["🌊 SwingTradeController\n/swing"]:::controller
        C9["💡 SuggestedTradesController\n/suggested"]:::controller
        C10["👤 ClientController\n/clients"]:::controller
        C11["📂 ImportController\n/import"]:::controller
        C12["⚙️ AdminControllers\n/admin/*"]:::controller
    end

    subgraph ResilienceLayer["🛡️ Resilience  (AOP)"]
        RA{{"🛡️ ResilienceAspect\n@Around endpoints"}}:::resilience
        TR["DynamicThrottleRegistry\nthrottle-config.yaml\nper-user TPS limits"]:::resilience
    end

    subgraph TradingCore["💹 Trading Core"]
        TS["💹 TradeService\n+ LimitOrderScheduler"]:::trading
        PS["📊 PortfolioService\n+ AccountService"]:::trading
        FD["🚨 FraudDetectionService"]:::trading
        RE["⚖️ RuleEngineService\nDrools .drl rules"]:::trading
        RS["🔄 ReconciliationService"]:::trading
        AU["📝 AuditService"]:::trading
        CS["👤 ClientService"]:::trading
    end

    subgraph MarketData["📡 Market & Price Data"]
        MS["📈 MarketService\nIndices cache 1 min"]:::market
        SS["💲 StockPriceService"]:::market
        YC["☁️ RealYahooFinanceClient\nin-mem cache 5 min\nv7→v8(1d)→v6→v8(query2)"]:::market
        MD["📂 StockMarketDataService\nCSV cache 60 min · 5-min bars"]:::market
        OD["📊 OptionsDataService\ncache 30 min\nATM IV · PCR · Max Pain"]:::market
    end

    subgraph Analytics["📈 Analytics & Predictions"]
        TA["📡 TrendAnalysisBatchService\n7 techniques · adaptive weights"]:::analytics
        PP["🔮 PredictionBatchService\n+ PredictionScoringService"]:::analytics
        SW["🌊 SwingTradeService\n+ AtrService"]:::analytics
        SG["💡 SuggestedTradesService"]:::analytics
    end

    subgraph DataLayer["💾 Data Layer"]
        PG[("🐘 PostgreSQL 16\n21+ JPA Repositories")]:::datastore
        RD[("⚡ Redis 7\nSpring @Cacheable")]:::datastore
        FS[("📁 CSV Files\nstock_predictions/\ntrend_predictions/")]:::datastore
        YF(["☁️ Yahoo Finance\n(external HTTPS)"]):::external
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
    SG --> TA
    TS --> PG
    PS --> PG & RD
    CS --> PG & RD
    TA --> PG & FS
    PP --> PG & FS
    SW --> PG
    AU --> PG
    RS --> PG

    style SecurityLayer fill:#FFEBEE,stroke:#B71C1C
    style Controllers fill:#E3F2FD,stroke:#1565C0
    style ResilienceLayer fill:#FFF3E0,stroke:#E65100
    style TradingCore fill:#E0F2F1,stroke:#00695C
    style MarketData fill:#E8F5E9,stroke:#2E7D32
    style Analytics fill:#EDE7F6,stroke:#4527A0
    style DataLayer fill:#ECEFF1,stroke:#37474F
```

---

## 3. Real-time Data Flow (User-triggered)

End-to-end flows with cache decision points (diamonds), TTL labels, success/error outcomes.

```mermaid
flowchart TD
    classDef user fill:#0D47A1,stroke:#01579B,color:#fff
    classDef controller fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef service fill:#2E7D32,stroke:#1B5E20,color:#fff
    classDef decision fill:#E65100,stroke:#BF360C,color:#fff
    classDef db fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef external fill:#6A1B9A,stroke:#4A148C,color:#fff
    classDef success fill:#1B5E20,stroke:#004D40,color:#fff
    classDef error fill:#B71C1C,stroke:#7F0000,color:#fff

    User(["🌐 User / Browser"]):::user

    subgraph PortfolioFlow["📊 Portfolio Load  GET /api/portfolio/client/{id}/summary"]
        PF1["🎮 PortfolioController"]:::controller
        PF2["📊 PortfolioService\nconvertToResponse()"]:::service
        PF3{"⚡ Redis cache\nhit?"}:::decision
        PF4[("🐘 PostgreSQL\nload holdings")]:::db
        PF5{"⏱️ Price cache\nhit?  TTL 5 min"}:::decision
        PF6["☁️ YahooFinanceClient\nGET v7/quote\n→ v8/chart 1d fallbacks"]:::external
        PF7{"🌙 Post-market\nwindow?\n4–8 PM ET"}:::decision
        PF8["☁️ YahooFinanceClient\nGET v8/chart 1m\nwalk back timestamps"]:::external
        PF9["✅ Portfolio + prices\nP&L + postMarketPrice"]:::success
    end

    subgraph TradeFlow["💹 Place Trade  POST /api/trades"]
        TF1["🎮 TradeController"]:::controller
        TF2{{"🛡️ ResilienceAspect\nrate-limit check"}}:::decision
        TF3["💹 TradeService\nexecuteTrade()"]:::service
        TF4["🚨 FraudDetection\n+ Drools rules eval"]:::service
        TF5{"⚖️ Rules\npassed?"}:::decision
        TF6["📊 PortfolioService\ndebit / credit cash"]:::service
        TF7["📝 AuditService\nwrite AuditLog"]:::service
        TF8["🔄 ReconciliationService\nbalance check"]:::service
        TF9X["❌ Reject trade\n403 / error"]:::error
        TF9["✅ Trade persisted\nPostgreSQL → Redis evict"]:::success
    end

    subgraph MarketFlow["📈 Market Indices  GET /api/market/indices"]
        MF1["🎮 MarketController"]:::controller
        MF2{"⏱️ Indices cache\nhit?  TTL 1 min"}:::decision
        MF3["📈 MarketService\nfetchIndexQuote()"]:::service
        MF4["☁️ YahooFinanceClient\nGET v8/chart 1d  ×5 symbols"]:::external
        MF5["☁️ getPostMarketPrice()\nv8/chart 1m  ×5 symbols"]:::external
        MF6["✅ ^GSPC ^DJI ^IXIC\nGC=F ^RUT + postMarket"]:::success
    end

    subgraph PredictFlow["🔮 Price Prediction  GET /api/predictions/{sym}"]
        PR1["🎮 PredictionController"]:::controller
        PR2{"⏱️ DB record\nfresh?  < 50 min"}:::decision
        PR3[("🐘 PostgreSQL\nstock_price_prediction")]:::db
        PR4["✅ 8-hour hourly\nforecast array"]:::success
    end

    User -->|"load dashboard"| PF1
    PF1 --> PF2 --> PF3
    PF3 -->|"HIT"| PF9
    PF3 -->|"MISS"| PF4 --> PF5
    PF5 -->|"HIT"| PF9
    PF5 -->|"MISS"| PF6 --> PF7
    PF7 -->|"YES"| PF8 --> PF9
    PF7 -->|"NO"| PF9

    User -->|"buy / sell"| TF1
    TF1 --> TF2
    TF2 -->|"rate-limited"| TF9X
    TF2 -->|"allowed"| TF3 --> TF4 --> TF5
    TF5 -->|"FAIL"| TF9X
    TF5 -->|"PASS"| TF6 --> TF7 --> TF8 --> TF9

    User -->|"view market bar"| MF1
    MF1 --> MF2
    MF2 -->|"HIT"| MF6
    MF2 -->|"MISS"| MF3 --> MF4 --> MF5 --> MF6

    User -->|"open prediction chart"| PR1
    PR1 --> PR2
    PR2 -->|"FRESH"| PR3 --> PR4
    PR2 -->|"STALE"| PR4
```

---

## 4. Scheduled Batch Processing Pipeline

All 9 schedulers grouped by frequency, color-coded by cadence, showing Yahoo endpoints and persistence targets.

```mermaid
flowchart LR
    classDef external fill:#6A1B9A,stroke:#4A148C,color:#fff
    classDef db fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef csv fill:#E65100,stroke:#BF360C,color:#fff

    YF(["☁️ Yahoo Finance API"]):::external
    PG[("🐘 PostgreSQL")]:::db
    FS[("📁 CSV Files\nstock_predictions/\ntrend_predictions/")]:::csv

    subgraph Every1min["⏱️ Every 1 min"]
        R1["🔄 ReconciliationService\nVerify cash + holding balances\nFix discrepancies in DB"]
        HB["💓 SystemHeartbeatService\nWrite timestamp to heartbeat table"]
    end

    subgraph Every5min["⏱️ Every 5 min"]
        LO["📋 LimitOrderScheduler\nScan open limit orders\nExecute if price crossed trigger\n→ TradeService.executeTrade()"]
    end

    subgraph Every10min["⏱️ Every 10 min  (+10 s startup)"]
        TA["📡 TrendAnalysisBatchService\nFor each symbol:\n  MA / RSI / MACD / Momentum\n  Volume / IndexMomentum / OptionsSentiment\nWeighted vote → BULLISH/BEARISH/NEUTRAL\nAdaptive weight update"]
    end

    subgraph Every1hr["⏱️ Every 1 hour  (+30 s startup)"]
        PP1["🔮 PredictionBatchService\ngeneratePredictions()\n  Fetch 5-min bars\n  ARIMA + weighted features\n  8h hourly forecast → DB"]
        PP2["🎯 resolveActuals()\n  Compare predictions vs actual\n  Update StockPredictionWeight\n  (adaptive learning)"]
    end

    subgraph Daily2AM["🌙 Daily  2:00 AM  ET"]
        DS["📡 DataSyncBatchService\nFetch 60-day daily bars  ×all symbols\nUpsert into StockPriceCache table"]
    end

    subgraph Daily6AM["🌅 Daily  6:00 AM"]
        SG["💡 SuggestedTradeTrackingService\nEvaluate open trade suggestions\nvs current price / trend"]
    end

    subgraph Daily630AM["🌅 Daily  6:30 AM"]
        SW["🌊 SwingTradeTrackingService\nEvaluate swing predictions\nvs actual price movement\nUpdate weights + P&L"]
    end

    subgraph Daily6PM["🌆 Daily  6:00 PM  ET  (weekdays)"]
        SC["🎯 PredictionScoringService\nCompare predictions vs closing\nCompute MAE / direction accuracy\nSave PredictionDailyScore"]
    end

    YF -->|"v8/chart 5m 60d ×N"| TA
    YF -->|"v8/chart 5m 60d + price"| PP1
    YF -->|"v8/chart 1d ×all symbols"| DS
    YF -->|"current price ×symbols"| LO

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

    style Every1min fill:#E8F5E9,stroke:#2E7D32
    style Every5min fill:#E3F2FD,stroke:#1565C0
    style Every10min fill:#FFF3E0,stroke:#E65100
    style Every1hr fill:#EDE7F6,stroke:#4527A0
    style Daily2AM fill:#E8EAF6,stroke:#283593
    style Daily6AM fill:#FFF8E1,stroke:#FF8F00
    style Daily630AM fill:#FFF8E1,stroke:#FF8F00
    style Daily6PM fill:#FCE4EC,stroke:#880E4F
```

---

## 5. Observability & Monitoring Stack

Three signal pipelines (metrics → Prometheus, logs → Loki, traces → Tempo) all converging into Grafana.

```mermaid
graph LR
    subgraph App["🌿 Spring Boot Application"]
        METER["📊 Micrometer\ntrades_executed_total\nportfolio_value_gauge\napi_request_duration"]
        ACT["⚙️ Actuator\n/actuator/prometheus\n/actuator/health"]
        BRAVE["🔮 Brave Tracer\nHTTP span instrumentation"]
        LOG["📋 Logback JSON\nlogs/stock-brokerage.json\nlogs/batch-runs.txt"]
    end

    subgraph MetricsPipeline["📊 Metrics Pipeline"]
        PROM["🔥 Prometheus\n:9090\nScrape every 15 s\nRetention 15 days"]
    end

    subgraph LogPipeline["📋 Log Pipeline"]
        PT["Promtail\ntail logs/ · add labels"]
        LOKI["📋 Loki  :3100\nLabel-based indexing"]
    end

    subgraph TracePipeline["🔮 Trace Pipeline"]
        TEMPO["🔮 Tempo\n:9411 Zipkin receiver\n:3200 HTTP API"]
    end

    GRAF["📊 Grafana  :3000\nMetrics · Logs · Traces\nJVM · Trades · Predictions"]

    METER --> ACT
    ACT -->|"GET /actuator/prometheus  every 15 s"| PROM
    LOG -->|"tail"| PT
    PT -->|"Loki push  :3100"| LOKI
    BRAVE -->|"Zipkin spans  POST :9411"| TEMPO

    PROM -->|"PromQL"| GRAF
    LOKI -->|"LogQL"| GRAF
    TEMPO -->|"TraceQL"| GRAF

    PROM -.->|"Alertmanager (optional)"| ALERT(["🚨 Alerts\nemail / Slack"])

    style App fill:#E8F5E9,stroke:#2E7D32
    style MetricsPipeline fill:#FBE9E7,stroke:#BF360C
    style LogPipeline fill:#FFF8E1,stroke:#FF8F00
    style TracePipeline fill:#EDE7F6,stroke:#4527A0
    style GRAF fill:#f46800,color:#fff,stroke:#e65100
    style PROM fill:#e6522c,color:#fff,stroke:#bf360c
    style LOKI fill:#f5a623,color:#000,stroke:#e65100
    style TEMPO fill:#7b61ff,color:#fff,stroke:#4527A0
```

---

## 6. Client / Investor Use Cases

All actions available to a logged-in retail investor.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1565C0", "tertiaryColor": "#fff"}}}%%
graph LR
    actor1(["👤 Client\n(Investor)"])

    subgraph Authentication["🔒 Authentication"]
        UC1["Register / Login"]
        UC2["JWT token refresh"]
    end

    subgraph Portfolio["📊 Portfolio Management"]
        UC3["View portfolio summary\n(prices + P&L + post-market)"]
        UC4["View account balance\n& cash position"]
        UC5["Download holdings CSV"]
        UC6["Import holdings from\nSchwab CSV"]
    end

    subgraph Trading["💹 Trading"]
        UC7["Place market order\n(buy / sell)"]
        UC8["Place limit order\n(trigger price)"]
        UC9["View trade history"]
        UC10["Cancel pending\nlimit order"]
    end

    subgraph MarketInfo["📈 Market Information"]
        UC11["View market indices\n(^GSPC ^DJI ^IXIC GC=F ^RUT)"]
        UC12["View real-time\nstock price"]
        UC13["View post-market price\n(4 PM – 8 PM ET)"]
    end

    subgraph Intelligence["🔮 Predictive Intelligence"]
        UC14["View 8-hour price\nforecast popup"]
        UC15["View trend direction\n(BULLISH/BEARISH/NEUTRAL)"]
        UC16["View swing trade\nsuggestions"]
        UC17["View options data\n(ATM IV · PCR · Max Pain)"]
    end

    actor1 --> UC1 & UC2
    actor1 --> UC3 & UC4 & UC5 & UC6
    actor1 --> UC7 & UC8 & UC9 & UC10
    actor1 --> UC11 & UC12 & UC13
    actor1 --> UC14 & UC15 & UC16 & UC17

    style Authentication fill:#FFEBEE,stroke:#B71C1C
    style Portfolio fill:#E3F2FD,stroke:#1565C0
    style Trading fill:#E8F5E9,stroke:#2E7D32
    style MarketInfo fill:#FFF3E0,stroke:#E65100
    style Intelligence fill:#EDE7F6,stroke:#4527A0
```

---

## 7. Admin Use Cases

Actions available exclusively to system administrators.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E8F5E9", "primaryBorderColor": "#2E7D32"}}}%%
graph LR
    actor2(["🛡️ System\nAdministrator"])

    subgraph ClientAdmin["👤 Client Administration"]
        A1["Create / update\nclient accounts"]
        A2["View all clients\n& portfolios"]
        A3["Adjust cash balance\n(manual credit/debit)"]
        A4["Force portfolio\nreconciliation"]
    end

    subgraph TradeAdmin["💹 Trade Administration"]
        A5["View all trades\nacross all clients"]
        A6["Override / cancel\nany trade"]
        A7["View audit log\n(all events)"]
    end

    subgraph RuleAdmin["⚖️ Rule Engine"]
        A8["Create / update trading\nrules (Drools)"]
        A9["Set per-client\nrisk rules"]
        A10["Enable / disable rules\nwithout restart"]
    end

    subgraph JobAdmin["⚙️ Job Administration"]
        A11["View job execution\nrecords + status"]
        A12["Trigger manual\nbatch run"]
        A13["View DB backup\nstatus / trigger"]
    end

    subgraph ResilienceAdmin["🛡️ Resilience"]
        A14["View resilience /\ncircuit-breaker status"]
        A15["Reload throttle\nconfig (hot-reload)"]
    end

    actor2 --> A1 & A2 & A3 & A4
    actor2 --> A5 & A6 & A7
    actor2 --> A8 & A9 & A10
    actor2 --> A11 & A12 & A13
    actor2 --> A14 & A15

    style ClientAdmin fill:#E3F2FD,stroke:#1565C0
    style TradeAdmin fill:#E8F5E9,stroke:#2E7D32
    style RuleAdmin fill:#FFF3E0,stroke:#E65100
    style JobAdmin fill:#EDE7F6,stroke:#4527A0
    style ResilienceAdmin fill:#FFEBEE,stroke:#B71C1C
```

---

## 8. System Scheduler Use Cases

Automated use cases performed by the Spring `@Scheduled` subsystem with no human interaction.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#EDE7F6", "primaryBorderColor": "#4527A0"}}}%%
graph TB
    actor3(["⏰ System Scheduler\n(Spring @Scheduled)"])

    subgraph HighFreq["⚡ High-frequency  (1–5 min)"]
        S1["Reconcile all portfolio\nand cash balances  every 1 min"]
        S2["Write system heartbeat\nto DB  every 1 min"]
        S3["Execute triggered\nlimit orders  every 5 min"]
    end

    subgraph MedFreq["🔄 Medium-frequency  (10 min – 1 hr)"]
        S4["Run 7-technique trend analysis\nfor all symbols  every 10 min"]
        S5["Generate 8-hour price\nforecasts  every 1 hour"]
        S6["Resolve prediction actuals\n& update weights  every 1 hour"]
    end

    subgraph DailyJobs["📅 Daily Jobs"]
        S7["Sync 60-day OHLCV bars\nfrom Yahoo Finance  2:00 AM"]
        S8["Evaluate pending\ntrade suggestions  6:00 AM"]
        S9["Evaluate swing trade\noutcomes + P&L  6:30 AM"]
        S10["Score yesterday's price\npredictions (MAE)  6:00 PM weekdays"]
    end

    subgraph Outputs["💾 Outputs"]
        O1[("🐘 PostgreSQL\n(persistent state)")]
        O2[("📁 CSV Files\nweights + predictions")]
        O3[("☁️ Yahoo Finance\n(read-only API calls)")]
    end

    actor3 --> S1 & S2 & S3
    actor3 --> S4 & S5 & S6
    actor3 --> S7 & S8 & S9 & S10

    S1 & S2 & S3 --> O1
    S4 --> O1 & O2
    S5 --> O1 & O2
    S6 --> O1
    S7 --> O1
    S8 & S9 & S10 --> O1

    S3 & S4 & S5 & S7 --> O3

    style HighFreq fill:#E8F5E9,stroke:#2E7D32
    style MedFreq fill:#EDE7F6,stroke:#4527A0
    style DailyJobs fill:#FFF3E0,stroke:#E65100
    style Outputs fill:#ECEFF1,stroke:#37474F
```

---

## 9. Security Architecture Diagram

Trust boundaries, authN/authZ checkpoints, and data-protection controls across the full request path.

```mermaid
flowchart LR
    classDef edge fill:#0D47A1,stroke:#01579B,color:#fff
    classDef app fill:#2E7D32,stroke:#1B5E20,color:#fff
    classDef sec fill:#B71C1C,stroke:#7F0000,color:#fff
    classDef data fill:#1565C0,stroke:#0D47A1,color:#fff
    classDef ext fill:#6A1B9A,stroke:#4A148C,color:#fff
    classDef obs fill:#E65100,stroke:#BF360C,color:#fff

    User(["User Browser\nJWT client"]):::edge
    Internet(["Public Internet"]):::edge

    subgraph DMZ["Trust Boundary A: Edge / DMZ"]
        NGINX["Nginx\nTLS termination :443\nHTTP->HTTPS redirect\nsecurity headers"]:::sec
    end

    subgraph AppZone["Trust Boundary B: Application Zone (private docker network)"]
        JWT["JwtAuthFilter\nToken validation\nrole extraction"]:::sec
        CTRL["REST Controllers\n/api/**"]:::app
        RES["ResilienceAspect\nthrottle + abuse control"]:::sec
        SVC["Domain Services\ntrades, portfolio, market, prediction"]:::app
    end

    subgraph DataZone["Trust Boundary C: Data Zone"]
        PG[("PostgreSQL\nPII + financial state\nJPA parameterized queries")]:::data
        RD[("Redis\ncache/session-like data\nTTL + key scoping")]:::data
        FS[("CSV prediction artifacts\nleast-write paths")]:::data
    end

    subgraph ExternalZone["Trust Boundary D: External Services"]
        YF(["Yahoo Finance API\nHTTPS outbound only"]):::ext
    end

    subgraph ObsZone["Trust Boundary E: Observability"]
        PROM["Prometheus\nmetrics scrape"]:::obs
        LOKI["Loki\nstructured logs"]:::obs
        TEMPO["Tempo\ntraces"]:::obs
        GRAF["Grafana\nRBAC dashboards"]:::obs
    end

    User -->|HTTPS| Internet --> NGINX
    NGINX -->|/api/** only| JWT --> CTRL --> RES --> SVC
    SVC --> PG
    SVC --> RD
    SVC --> FS
    SVC -->|egress allowlist + TLS| YF

    SVC -->|/actuator/prometheus| PROM
    SVC -->|JSON logs| LOKI
    SVC -->|Zipkin spans| TEMPO
    PROM --> GRAF
    LOKI --> GRAF
    TEMPO --> GRAF
```

Security control map:

- Edge: TLS at Nginx, redirect cleartext to TLS, minimal exposed ports.
- Identity: JWT validation at filter layer before controller access; role-based endpoint protection.
- Abuse resistance: dynamic throttling and resilience aspect to limit excessive or burst traffic.
- Data protection: least-privilege DB credentials, cache TTL boundaries, and scoped persistence for artifacts.
- Egress hardening: outbound API calls only to known finance endpoints over HTTPS.
- Auditability: centralized logs, metrics, and traces for forensics and anomaly detection.

---

## 10. Threat Model (STRIDE)

Scope: browser -> nginx -> spring boot -> postgres/redis/filesystem -> external APIs and observability stack.

| STRIDE | Threat | Example in this system | Primary mitigations | Detection signals |
|---|---|---|---|---|
| Spoofing | Token/session impersonation | Stolen JWT used to call `/api/trades` as another user | Short JWT expiry, strong signing keys, key rotation, role checks per endpoint, optional token jti denylist | Spike in auth failures, geo/IP anomalies, unusual role usage |
| Tampering | API payload or order manipulation | Modified trade quantity/price in transit or replayed request body | TLS end-to-end at edge, request validation, idempotency keys for trade submission, server-side business validation | Signature/validation errors, duplicate idempotency key collisions |
| Repudiation | User denies sensitive action | User denies placing a high-value trade | Immutable audit trail with actor id, timestamp, endpoint, request hash; synchronized server time | Gaps in audit events, missing correlation IDs |
| Information Disclosure | Sensitive data leak | PII/portfolio data exposed via logs, debug endpoints, or misconfigured Grafana | Log redaction, actuator endpoint restrictions, secrets via env/secret store, least-privileged dashboard RBAC | DLP/log scans, unauthorized dashboard access events |
| Denial of Service | Traffic flood or expensive query abuse | Burst requests to market/prediction endpoints degrade response | Per-user throttling, resilience aspect, cache TTL strategy, connection pool limits, WAF/rate limit at edge | Elevated 429/503 rates, saturation of thread pool/DB pool |
| Elevation of Privilege | Regular user gains admin capability | JWT claims abused to access `/admin/*` | Strict role-based authorization, deny-by-default route policy, admin endpoint segregation, defense-in-depth checks in service layer | Forbidden-to-success pattern anomalies, admin action alerts |

High-priority abuse cases:

1. Automated credential stuffing against login endpoints.
2. Trade replay attempts during network retries.
3. Data exfiltration via overly verbose logs and observability labels.
4. Resource exhaustion through high-cardinality market/prediction requests.

Recommended verification checklist:

1. Confirm all `/admin/**` paths enforce role checks at both controller and method level.
2. Confirm actuator endpoints exposed publicly are limited to health/metrics only.
3. Confirm all security-relevant events include correlation ID and actor ID.
4. Confirm trade creation path supports idempotency and replay detection.
5. Confirm dashboards/log queries do not expose raw secrets, tokens, or PII.

---

## 11. Data Classification & Encryption Controls

Classification legend:

- Restricted: highly sensitive user or financial data requiring strict access controls and minimization.
- Confidential: operational data that can increase attack impact if exposed.
- Internal: routine telemetry and service metadata for internal operations.
- Public: intentionally exposed, low-risk information.

| Data asset | Example fields | Classification | At rest controls | In transit controls | Access and retention controls |
|---|---|---|---|---|---|
| Identity and auth data | username, password hash, roles, JWT metadata | Restricted | PostgreSQL disk encryption, strong password hashing (Argon2/bcrypt), secret-backed signing keys | HTTPS at edge, internal private network between containers | Least-privileged DB role, rotate signing keys, short token TTL |
| Client profile and account state | client id, account balance, holdings, portfolio valuation | Restricted | PostgreSQL encryption at rest, backups encrypted, row-level ownership checks in app layer | TLS from browser to nginx, private app/data network | Role-based access checks, strict audit trail, backup retention policy |
| Trade and order data | order type, symbol, quantity, price, status, timestamps | Restricted | PostgreSQL encryption, immutable audit records, integrity constraints | TLS and signed JWT identity context on each request | Idempotency on create, reconciliation jobs, non-repudiation logging |
| Market cache data | latest quotes, index snapshots, derived indicators | Confidential | Redis protected in private network, key TTL, no persistence for sensitive keys unless required | Encrypted egress to Yahoo Finance, private network in-cluster | Cache namespace isolation, eviction policy, bounded retention |
| Prediction artifacts | model outputs, trend scores, weight files, CSV artifacts | Confidential | Encrypted volume/storage for CSV and DB tables, write-limited service account | Internal network only, controlled export paths | Least-write filesystem permissions, scheduled cleanup/versioning |
| Logs and traces | request ids, endpoint names, error codes, spans | Internal | Loki/Tempo storage encryption, log retention limits | TLS between components where supported, isolated observability network | Redaction for secrets/PII, RBAC in Grafana, alert on sensitive field leakage |
| Metrics | latency, throughput, error rates, JVM stats | Internal | Prometheus TSDB with protected storage | Scrape endpoints on private network, secured dashboard access | Minimize label cardinality, deny external scrape access |
| Public API metadata | health/status endpoints intended for availability checks | Public | Minimal stored state | HTTPS only | Expose only non-sensitive data, no debug internals |

Key management and operational guardrails:

1. Store all application secrets and JWT signing material outside source control; inject via environment or secret manager.
2. Rotate credentials and signing keys on a defined cadence and on incident triggers.
3. Enforce encryption for backups and verify restore procedures quarterly.
4. Redact tokens, passwords, account numbers, and PII fields before logs are shipped.
5. Review role mappings for admin and support users at least monthly.
