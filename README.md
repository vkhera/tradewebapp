# Stock Brokerage Application

A low-latency stock brokerage web application built with Spring Boot backend and Angular frontend.

## Features

- **Low-Latency Trading**: Optimized for high-speed trade execution with Yahoo Finance API integration
- **REST APIs**: Complete REST APIs with Swagger documentation
- **Rule Engine**: Drools-based rule engine for application-wide, client-specific, and trade-level rules
- **Fraud Detection**: Multi-layered fraud detection system with trading hours and limit checks
- **Portfolio Management**: Real-time portfolio tracking with automatic reconciliation
- **Limit Orders**: Automated limit order execution with 5-minute batch processing
- **Account Reconciliation**: Minute-by-minute reconciliation of portfolio and cash balances
- **Admin UI**: Comprehensive admin interface for managing clients, trades, and rules
- **Audit Logging**: Complete event logging for all system activities
- **High-Speed Database**: PostgreSQL with optimized connection pooling
- **Caching**: Redis-based caching for improved performance
- **Rate Limiting & Circuit Breaking**: Resilience4j throttle with hot-reloadable YAML config; per-service TPS overrides and circuit-breaker protection (60-second auto-reload, 1 TPS global default)
- **Price Prediction Popup**: Click-based fixed-position popup showing 8-hour hourly forecasts for each portfolio holding
- **Auth Guard**: Angular route protection redirecting unauthenticated users to login

## Quality Checks Toolkit

A project-level quality automation toolkit is available in [quality/README.md](quality/README.md).

It includes:

- General code quality checks (Java + Angular)
- Static security code review (Semgrep + dependency scan)
- Frontend post-build functional tests (Playwright)
- Frontend performance checks for all screens (Lighthouse CI)
- API functional and performance tests (k6)

## Technology Stack

### Backend
- Java 21 (JDK 21.0.8, compatible with 17+)
- Spring Boot 3.2.1
- Spring Data JPA
- Spring Security
- Spring Scheduling
- PostgreSQL 16
- Redis 7
- Drools Rule Engine
- Swagger/OpenAPI
- Jackson with JSR-310 (Java 8 date/time support)

### Frontend
- Angular 17
- TypeScript
- Standalone Components
- Reactive Forms
- Server-Side Rendering (SSR)

## Architecture

```mermaid
graph TB
    subgraph Browser["Browser (Angular 17 · port 4200)"]
        direction LR
        LOGIN[Login]
        PORTFOLIO[Portfolio\n+ Prediction Tooltip]
        TRADE[Trade]
        HISTORY[Order History]
        ADMIN[Admin UI\nClients · Rules · Trades]
    end

    subgraph Backend["Spring Boot Backend (Java 21 · port 8080)"]
        direction TB

        subgraph API["REST Layer"]
            AC[AuthController]
            AEC[AccountController]
            PC[PortfolioController]
            SC[StockController]
            TC[TradeController]
            TRC[TrendAnalysisController]
            PRC[StockPricePredictionController]
            IC[ImportController]
            CC[ClientController]
            CAC[ClientAdminController]
            TAC[TradeAdminController]
            RAC[RuleAdminController]
            RSC[ResilienceStatusController]
        end

        subgraph RESIL["Resilience Layer"]
            DTR[DynamicThrottleRegistry\nhot-reload every 60s\nconfig/throttle-config.yaml]
        end

        subgraph SVC["Business Services"]
            TS[TradeService]
            PS[PortfolioService]
            SPS[StockPriceService\nYahoo Finance 3-endpoint fallback]
            SMDS[StockMarketDataService\n5-min bars · CSV cache · 5 min TTL]
            SPPS[StockPricePredictionService\n5 techniques · adaptive weights\n50-min cache]
            TAS[TrendAnalysisService\n5 techniques · per-stock weights]
            IS[ImportService\nSchwab CSV holdings + activity]
            RS[ReconciliationService]
            LOS[LimitOrderService]
        end

        subgraph RULES["Drools Rule Engine"]
            FR[fraud-check-rules.drl\nTrading hours · daily limits\nclient status · size checks]
            CR[cash-validation-rules.drl\nAvailable balance · reserve checks]
        end

        subgraph SCHED["Schedulers"]
            TABS[TrendAnalysisBatchService\nDaily 4:30 PM ET cron]
            SPPBS[StockPricePredictionBatchService\nHourly · resolves actuals\nupdates weights]
            LOP[LimitOrderScheduler\nEvery 5 min]
            REC[ReconciliationScheduler\nEvery 1 min]
        end

        subgraph SEC["Security & Docs"]
            SS[Spring Security\nBasic Auth · RBAC]
            SW[Swagger / OpenAPI 3\nBearer JWT · 13 tags\nlocalhost:8080/swagger-ui.html]
        end
    end

    subgraph DATA["Data Layer (Docker)"]
        PG[(PostgreSQL 16\nport 5432\nstockdb)]
        RD[(Redis 7\nport 6379\nSession · Cache)]
    end

    subgraph CSV["CSV Filesystem"]
        TPC[trend_predictions/\nSYMBOL_trend.csv]
        SPC[stock_predictions/\nSYMBOL_pred_weights.csv\nSYMBOL_predictions.csv\nSYMBOL_bars.csv]
        IMP[importexport/\nholdings.csv · activity.csv]
    end

    subgraph EXT["External API (Free · No Key)"]
        YF[Yahoo Finance v8 Chart\ninterval=5m · range=60d\nUser-Agent header]
    end

    Browser -->|HTTP/JSON Bearer Auth| API
    API --> SVC
    API --> RULES
    SVC --> RULES
    SCHED --> SVC
    SVC --> DATA
    SVC --> CSV
    SMDS -->|HTTPS GET| YF
    SS --> API
    SW --> API
    API --> RESIL
    RESIL --> SVC
```

## Observability

The application ships with a full, free observability stack: **Metrics** (Prometheus + Grafana), **Distributed Logs** (Loki + Promtail), and **Traces** (Tempo). It lives in a **separate** `docker-compose.observability.yml` so you can reuse it for any number of applications.

### Component & Port Reference

| Component | Container | Host Port | Purpose |
|-----------|-----------|-----------|---------|
| Angular / nginx | `stockbrokerage-frontend` | **80** | Web UI |
| Spring Boot | `stockbrokerage-backend` | **8080** | REST API + Prometheus scrape endpoint |
| PostgreSQL 16 | `stockdb-postgres` | **5432** | Relational data |
| Redis 7 | `stockdb-redis` | **6379** | Cache + sessions |
| Prometheus | `obs-prometheus` | **9090** | Metrics collection |
| Grafana | `obs-grafana` | **3000** | Dashboards, logs & traces UI |
| Loki | `obs-loki` | **3100** (internal) | Log aggregation |
| Tempo | `obs-tempo` | **3200** (API) / **9411** (Zipkin) | Distributed tracing |
| Promtail | `obs-promtail` | — | Log shipper (tails `logs/*.json`) |

### Architecture with Observability

```mermaid
graph LR
    BROWSER["fa:fa-globe Browser"]

    subgraph APP["Application Stack  ·  docker-compose.yml"]
        direction TB
        FE["nginx + Angular\n:80"]
        BE["Spring Boot\n:8080\n/actuator/prometheus"]
        PG[("PostgreSQL 16\n:5432")]
        RD[("Redis 7\n:6379")]
    end

    LOGFILE["logs/\nstock-brokerage.json"]

    subgraph OBS["Observability Stack  ·  docker-compose.observability.yml"]
        direction TB
        PROM["Prometheus\n:9090"]
        GF["Grafana\n:3000"]
        LK["Loki\n:3100"]
        PRTL["Promtail"]
        TMPO["Tempo\n:3200 (API)\n:9411 (Zipkin)"]
    end

    BROWSER -->|":80"| FE
    BROWSER -->|":3000 dashboards"| GF
    FE -->|":8080"| BE
    BE --> PG
    BE --> RD
    BE -. "Brave spans\n:9411" .-> TMPO
    BE -->|"JSON logs"| LOGFILE
    PROM -->|"scrape /actuator/prometheus"| BE
    PRTL -->|"tail"| LOGFILE
    PRTL -->|"push :3100"| LK
    GF -->|"query"| PROM
    GF -->|"query"| LK
    GF -->|"query"| TMPO
```

### Quick Start

```powershell
# Application only
start-app.bat

# Application + observability
start-app.bat obs

# Observability stack only (works with any app)
docker compose -f docker-compose.observability.yml up -d

# Stop application + observability
stop-app.bat all
```

| URL | Purpose | Credentials |
|-----|---------|-------------|
| http://localhost | Web UI | see accounts below |
| http://localhost:8080/swagger-ui.html | API docs | — |
| http://localhost:3000 | Grafana dashboards | `admin` / `admin` |
| http://localhost:9090 | Prometheus query | — |
| http://localhost:8080/actuator/prometheus | Raw metrics | — |
| http://localhost:8080/actuator/health | Health check | — |

### Pre-built Grafana Dashboard

A **Spring Boot Overview** dashboard is auto-provisioned at:
`Dashboards → stock-brokerage → Spring Boot Overview`

Panels: HTTP Request Rate, Error Rate, Latency (P50/P95/P99), JVM Heap, Non-Heap, GC pauses, CPU, HikariCP pool, Log events by level, Thread count, Process uptime.

### Trace → Log Correlation

1. Open **Grafana → Explore → Tempo**, search recent traces
2. Click any span → **Logs for this span** link appears automatically
3. Loki shows the JSON log lines that share the same `traceId`

### Adding Another Application to the Observability Stack

1. Add a scrape job in `observability/prometheus/prometheus.yml`:
   ```yaml
   - job_name: 'my-other-app'
     static_configs:
       - targets: ['host.docker.internal:8081']
         labels:
           application: 'my-other-app'
     metrics_path: '/actuator/prometheus'
   ```
2. Add a Promtail pipeline in `observability/promtail/promtail-config.yml` pointing to the new app's JSON log file.
3. Reload Prometheus without restarting: `curl -X POST http://localhost:9090/-/reload`

### Single All-in-One Image with Observability

For environments where running separate compose files is not practical, a single Docker image bundles all nine services (app + observability):

```powershell
# Build
docker build -f Dockerfile.allinone-obs -t vkdocker/stock-brokerage-allinone-obs .

# Run (exposes all UI ports + app port)
docker run -d `
  -p 80:80 `
  -p 3000:3000 `
  -p 9090:9090 `
  --name stockapp-full `
  vkdocker/stock-brokerage-allinone-obs

# Wait ~90 s, then open:
#   http://localhost        – Web UI
#   http://localhost:3000   – Grafana (admin / admin)
#   http://localhost:9090   – Prometheus
```

## Prerequisites

- **JDK 17 or higher** (Java 21+ recommended)
- **Maven 3.9+**
- **Node.js 18+** and npm
- **Docker Desktop** (for PostgreSQL and Redis)
- **Git** (for version control)

## Complete Setup Instructions for New Machine

### 1. Install Prerequisites

#### Windows
```powershell
# Install Chocolatey (if not already installed)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Install tools
choco install openjdk -y
choco install maven -y
choco install nodejs -y
choco install docker-desktop -y
choco install git -y
```

#### macOS
```bash
# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install tools
brew install openjdk@23
brew install maven
brew install node
brew install --cask docker
brew install git
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-23-jdk maven nodejs npm docker.io docker-compose git -y
```

### 2. Extract Project Files

Extract the project zip file to your desired location:
```bash
# Example
unzip ws-trd-1.zip -d ~/projects/
cd ~/projects/ws-trd-1
```

### 3. Start Database Services (Docker)

Start PostgreSQL and Redis using Docker Compose:
```bash
docker-compose up -d
```

Verify services are running:
```bash
docker ps
```

You should see both `stockdb-postgres` and `stockdb-redis` containers running.

### 4. Configure Application (Optional)

The application is pre-configured with default settings. If needed, you can modify:

**Backend Configuration** (`src/main/resources/application.yml`):
- Database connection (default: localhost:5432)
- Redis connection (default: localhost:6379)
- Server port (default: 8080)

**Frontend Configuration** (`frontend/src/app/services/api.service.ts`):
- API base URL (default: http://localhost:8080/api)

### 5. Build and Run Backend

```bash
# Navigate to project root
cd ws-trd-1

# Clean and build (skips tests for faster build)
mvn clean package -DskipTests

# Run the backend
mvn spring-boot:run
```

The backend will start on http://localhost:8080

### 6. Build and Run Frontend

Open a new terminal:

```bash
# Navigate to frontend directory
cd ws-trd-1/frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend will start on http://localhost:4200

### 7. Access the Application

- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui.html

### 8. Default User Accounts

#### Admin Users
- Username: `admin1` / Password: `pass1234`
- Username: `admin2` / Password: `pass1234`

#### Client Users
- Username: `client1` / Password: `pass1234` (Alice Johnson - $100,000)
- Username: `client2` / Password: `pass1234` (Bob Smith - $50,000)
- Username: `client3` / Password: `pass1234` (Charlie Brown - $75,000)
- Username: `client4` / Password: `pass1234` (Diana Prince - $150,000)
- Username: `client5` / Password: `pass1234` (Eve Davis - $25,000)

## Key Features Explained

### Trading System
- **Market Orders**: Execute immediately at current market price
- **Limit Orders**: Placed as PENDING, auto-executed when price conditions met
- **Batch Processing**: Limit orders checked every 5 minutes
- **Yahoo Finance Integration**: Real-time stock prices with 3-endpoint fallback system
- **Fraud Detection**: Trading hours validation, daily limits, client status checks

### Portfolio Management
- **Real-time Tracking**: Live portfolio with current prices
- **Automatic Reconciliation**: Account balances reconciled every minute
- **Grouped Holdings**: One line per stock with accurate average prices
- **Cash Management**: 
  - Cash Balance: Total available funds
  - Reserved Balance: Funds allocated to pending limit orders
  - Available Balance: Cash Balance - Reserved Balance

### Rate Limiting & Circuit Breaking
- **Hot-Reloadable Config**: Edit `config/throttle-config.yaml` at the project root; the registry auto-reloads within 60 seconds (or use `POST /api/admin/resilience/reload` for immediate effect)
- **Global default**: 1 TPS with circuit breaker (50% failure threshold, 30 s open state)
- **Per-service overrides** (selected):
  | Service | TPS | Notes |
  |---|---|---|
  | TradeService | 5 | Core order execution |
  | AccountService | 5 | Cash operations |
  | PortfolioService | 10 | Real-time portfolio |
  | StockPriceService | 10 | Yahoo Finance proxy |
  | StockPricePredictionService | 2 | Compute-heavy |
  | TrendAnalysisService | 3 | ML weights |
  | AuthService | 3 | Login protection |
  | ReconciliationService | 1 | Background only |
  | Batch/Audit services | disabled | No throttle |
- **Responses**: 429 `Too Many Requests` when throttled; circuit breaker returns 503 when open

### Scheduled Jobs
- **Limit Order Processor**: Runs every 5 minutes
- **Account Reconciliation**: Runs every 1 minute
- **Data Initialization**: Creates default users on first startup
- **Throttle Config Reload**: Runs every 60 seconds (fixed-delay)

## Troubleshooting

### Backend won't start
```bash
# Check if port 8080 is available
netstat -ano | findstr :8080  # Windows
lsof -i :8080                  # macOS/Linux

# Check database connection
docker logs stockdb-postgres
docker logs stockdb-redis
```

### Frontend build errors
```bash
# Clear node modules and reinstall
cd frontend
rm -rf node_modules package-lock.json
npm install
```

### Database connection errors
```bash
# Restart Docker containers
docker-compose down
docker-compose up -d

# Wait for health checks
docker ps
```

### Trade execution fails
- Verify Yahoo Finance endpoints are accessible
- Check backend logs for rate limiting (429 errors)
- The system uses 3 fallback endpoints automatically

## API Documentation

### Authentication
```bash
# Login
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "client1",
  "password": "pass1234"
}
```

### Trading
```bash
# Execute Market Order
POST http://localhost:8080/api/trade/execute
Authorization: Basic {credentials}

{
  "clientId": 1,
  "symbol": "TQQQ",
  "quantity": 10,
  "price": 55.43,
  "type": "BUY",
  "orderType": "MARKET"
}

# Execute Limit Order
POST http://localhost:8080/api/trade/execute
Authorization: Basic {credentials}

{
  "clientId": 1,
  "symbol": "TECL",
  "quantity": 5,
  "price": 60.00,
  "type": "BUY",
  "orderType": "LIMIT"
}
```

### Portfolio
```bash
# Get Portfolio Summary
GET http://localhost:8080/api/portfolio/client/{clientId}/summary
Authorization: Basic {credentials}
```

## Project Structure

```
ws-trd-1/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/stockbrokerage/
│   │   │       ├── config/          # Configuration classes
│   │   │       ├── controller/      # REST controllers
│   │   │       ├── dto/            # Data transfer objects
│   │   │       ├── entity/         # JPA entities
│   │   │       ├── exception/      # Exception handling
│   │   │       ├── repository/     # Data repositories
│   │   │       └── service/        # Business logic
│   │   │           ├── TradeService.java
│   │   │           ├── PortfolioService.java
│   │   │           ├── LimitOrderScheduler.java
│   │   │           ├── ReconciliationService.java
│   │   │           └── StockPriceService.java
│   │   └── resources/
│   │       ├── application.yml     # App configuration
│   │       └── rules/              # Drools rule files
│   └── test/                       # Unit tests
├── frontend/
│   └── src/
│       └── app/
│           ├── components/         # Angular components
│           └── services/           # Angular services
├── docker-compose.yml                   # Application services (postgres, redis, backend, frontend)
├── docker-compose.observability.yml     # Observability stack (Prometheus, Grafana, Loki, Tempo, Promtail)
├── observability/
│   ├── prometheus/prometheus.yml        # Prometheus scrape config
│   ├── grafana/
│   │   ├── provisioning/datasources/    # Auto-provisioned Prometheus + Loki + Tempo
│   │   ├── provisioning/dashboards/
│   │   └── dashboards/spring-boot.json # Pre-built Spring Boot dashboard
│   ├── loki/loki-config.yml
│   ├── promtail/promtail-config.yml
│   └── tempo/tempo-config.yml
├── Dockerfile.allinone                  # All-in-one image (app only, 4 processes)
├── Dockerfile.allinone-obs              # All-in-one image with observability (9 processes)
├── start-app.bat                        # Windows: start app [+ observability]
├── stop-app.bat                         # Windows: stop app [+ observability]
├── pom.xml                              # Maven dependencies
└── README.md                            # This file
```

## Creating Portable Package

To move this project to another machine:

### Option 1: Using Git (Recommended)
```bash
# On current machine - commit and push
git add .
git commit -m "Latest changes"
git push origin main

# On new machine - clone
git clone <your-repo-url>
cd ws-trd-1
```

### Option 2: Manual ZIP
1. **Exclude these folders/files** (they will be regenerated):
   - `target/`
   - `frontend/node_modules/`
   - `frontend/dist/`
   - `frontend/.angular/`
   - `.git/` (if not using git)
   - `logs/`

2. **Create ZIP**:
   ```bash
   # Windows PowerShell
   Compress-Archive -Path ws-trd-1 -DestinationPath ws-trd-1-portable.zip

   # macOS/Linux
   zip -r ws-trd-1-portable.zip ws-trd-1 -x "*/target/*" "*/node_modules/*" "*/dist/*" "*/.angular/*" "*/logs/*"
   ```

3. **Transfer** the ZIP file to new machine

4. **Extract and follow** Setup Instructions above

## Important Notes

- **Yahoo Finance API**: Public API with rate limits (~1-2 requests/minute). System uses 3 fallback endpoints.
- **Initial Startup**: Takes ~30 seconds as database schema is created and sample data loaded
- **Reconciliation**: First reconciliation runs 1 minute after startup
- **Limit Orders**: First batch processing runs 5 minutes after startup
- **Data Persistence**: All data stored in Docker volumes, persists across restarts

## Production Deployment

For production deployment:

1. **Update Configuration**:
   - Change default passwords
   - Configure external database
   - Set up proper SSL/TLS
   - Configure CORS for production domain

2. **Build for Production**:
   ```bash
   # Backend
   mvn clean package

   # Frontend
   cd frontend
   npm run build
   ```

3. **Deploy**:
   - Backend JAR: `target/stock-brokerage-1.0-SNAPSHOT.jar`
   - Frontend: `frontend/dist/browser/`

## License

This project is for educational purposes.

## Support

For issues or questions:
1. Check the Troubleshooting section
2. Review backend logs: Check console output
3. Review frontend logs: Browser Developer Console (F12)
4. Check Docker logs: `docker logs stockdb-postgres` or `docker logs stockdb-redis`

Run the application:
```bash
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/stock-brokerage-1.0-SNAPSHOT.jar
```

The backend will start on `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend
npm install
npm start
```

The frontend will start on `http://localhost:4200`

## API Documentation

Once the backend is running, access Swagger UI at:
- http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/api-docs

## Key Endpoints

### Trading APIs
- `POST /api/trades` - Execute a new trade
- `GET /api/trades` - Get all trades
- `GET /api/trades/{id}` - Get trade by ID
- `GET /api/trades/client/{clientId}` - Get trades by client

### Admin APIs - Clients
- `GET /api/admin/clients` - Get all clients
- `POST /api/admin/clients` - Create new client
- `PUT /api/admin/clients/{id}` - Update client
- `DELETE /api/admin/clients/{id}` - Delete client

### Admin APIs - Rules
- `GET /api/admin/rules` - Get all rules
- `POST /api/admin/rules` - Create new rule
- `PUT /api/admin/rules/{id}` - Update rule
- `DELETE /api/admin/rules/{id}` - Delete rule

### Admin APIs - Resilience (ADMIN role required)
- `GET /api/admin/resilience/status` - Current throttle config, per-service overrides, and live circuit-breaker states
- `POST /api/admin/resilience/reload` - Force-reload `config/throttle-config.yaml` immediately (without waiting 60 s)

## Rule Engine

The application uses Drools rule engine for:
- Application-wide rules (apply to all trades)
- Client-specific rules (apply to specific clients)
- Trade-level rules (apply to individual trades)

Rule types include:
- `FRAUD_CHECK` - Fraud detection rules
- `RISK_LIMIT` - Risk management rules
- `TRADING_HOURS` - Trading time restrictions
- `POSITION_LIMIT` - Position size limits
- `PRICE_VALIDATION` - Price validation rules

## Fraud Detection

The system includes multiple fraud detection layers:
- Client status verification
- Trading hours validation
- Daily trade limit checks
- Unusual trade size detection
- Account balance verification

## Performance Optimizations

- Connection pooling (HikariCP)
- Redis caching for frequently accessed data
- Async audit logging
- Optimized database queries with indexes
- Batch processing for database operations

## Admin UI Features

1. **Client Management**
   - View all clients
   - Create/update/delete clients
   - Filter by status and risk level
   - View audit logs per client

2. **Rule Management**
   - View all rules
   - Create/update/delete rules
   - Filter by type and level
   - Activate/deactivate rules

3. **Trade Monitoring**
   - View all trades
   - Filter by status, client, symbol
   - View audit logs per trade

## Security

- Spring Security configured
- CORS enabled for Angular frontend
- Role-based access control (ADMIN role required for admin endpoints)

## Logging

All events are logged to:
- Console (for development)
- File: `logs/stock-brokerage.log`
- Database (audit_logs table)

## Future Enhancements

- WebSocket support for real-time updates
- Advanced analytics and reporting
- Integration with market data providers
- Mobile application
- Microservices architecture
