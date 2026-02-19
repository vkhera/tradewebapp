@echo off
setlocal enabledelayedexpansion

REM ====================================================================
REM  Stock Brokerage - Full Application Startup
REM  Start order: PostgreSQL -> Redis -> Spring Boot -> Angular (port 4200)
REM
REM  Usage:
REM    start-app.bat          - start the application only
REM    start-app.bat obs      - also start the observability stack
 REM                            (Grafana :3000, Prometheus :9090, Loki :3100,
 REM                             Tempo :3200/:9411)
REM ====================================================================

set SCRIPT_DIR=%~dp0
set FRONTEND_DIR=%SCRIPT_DIR%frontend

REM ── Auto-detect JAVA_HOME ──────────────────────────────────────────
if "%JAVA_HOME%"=="" (
    if exist "C:\Users\normaluser\.jdk\jdk-21.0.8" (
        set JAVA_HOME=C:\Users\normaluser\.jdk\jdk-21.0.8
        echo   [INFO] JAVA_HOME set to C:\Users\normaluser\.jdk\jdk-21.0.8
    ) else (
        echo   [WARN] JAVA_HOME is not set and default JDK path not found.
        echo         Set JAVA_HOME before running this script.
    )
)

REM ── Auto-detect Maven ─────────────────────────────────────────────
set MAVEN_CMD=mvn
if exist "C:\Users\normaluser\.maven\maven-3.9.12\bin\mvn.cmd" (
    set MAVEN_CMD=C:\Users\normaluser\.maven\maven-3.9.12\bin\mvn.cmd
)

REM ── Parse optional argument ───────────────────────────────────────
set START_OBS=0
if /I "%~1"=="obs" set START_OBS=1

echo.
echo ====================================================================
echo   Stock Brokerage Application - Starting All Services
echo ====================================================================
echo.

REM ── Step 1: Check Docker ──────────────────────────────────────────
echo [1/4] Checking Docker...
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo   ERROR: Docker Desktop is not running.
    echo   Please start Docker Desktop and try again.
    echo.
    pause & exit /b 1
)
echo   Docker is running.

REM ── Step 2: Start PostgreSQL + Redis (+ optional observability) ───
echo.
echo [2/4] Starting PostgreSQL and Redis via Docker...
docker compose up -d postgres redis
if %errorlevel% neq 0 (
    echo   ERROR: Failed to start Docker containers.
    pause & exit /b 1
)

if "!START_OBS!"=="1" (
    echo.
    echo [obs] Starting observability stack (Grafana / Prometheus / Loki / Tempo)...
    docker compose -f docker-compose.observability.yml up -d
    if !errorlevel! neq 0 (
        echo   WARNING: Observability stack failed to start. Continuing anyway.
    ) else (
        echo   Observability stack starting in background.
    )
)

REM Poll PostgreSQL health
echo   Waiting for PostgreSQL to become healthy...
:wait_pg
timeout /t 3 /nobreak >nul
docker inspect --format "{{.State.Health.Status}}" stockdb-postgres 2>nul | findstr /i "healthy" >nul
if %errorlevel% neq 0 goto wait_pg
echo   PostgreSQL is ready.

REM Poll Redis health
echo   Waiting for Redis to become healthy...
:wait_redis
timeout /t 2 /nobreak >nul
docker inspect --format "{{.State.Health.Status}}" stockdb-redis 2>nul | findstr /i "healthy" >nul
if %errorlevel% neq 0 goto wait_redis
echo   Redis is ready.

REM ── Step 3: Start Spring Boot Backend ─────────────────────────────
echo.
echo [3/4] Starting Spring Boot backend (port 8080)...

REM Check if port 8080 is already in use
netstat -ano 2>nul | findstr ":8080.*LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo   Port 8080 is already in use - skipping backend start.
    echo   Assuming backend is already running.
    goto backend_ready
)

start "Spring Boot Backend" cmd /k "cd /d "%SCRIPT_DIR%" && set JAVA_HOME=%JAVA_HOME% && "%MAVEN_CMD%" spring-boot:run"

REM Poll actuator/health until backend responds with UP
echo   Waiting for backend to start (typically 20-40 seconds)...
:wait_backend
timeout /t 5 /nobreak >nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop; if ($r.Content -match 'UP') { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if %errorlevel% neq 0 goto wait_backend

:backend_ready
echo   Backend is UP at http://localhost:8080

REM ── Step 4: Start Angular Frontend ────────────────────────────────
echo.
echo [4/4] Starting Angular frontend (port 4200)...

REM Check if port 4200 is already in use
netstat -ano 2>nul | findstr ":4200.*LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo   Port 4200 is already in use - skipping frontend start.
    echo   Assuming frontend is already running.
    goto all_done
)

start "Angular Frontend" cmd /k "cd /d "%FRONTEND_DIR%" && npm start -- --host 0.0.0.0"
echo   Frontend starting in background window (takes ~15 seconds to compile)...

:all_done
echo.
echo ====================================================================
echo   All services started successfully!
echo ====================================================================
echo.
echo   APPLICATION
echo   -----------
echo   Frontend     :  http://localhost:4200
echo   Backend API  :  http://localhost:8080
echo   Swagger UI   :  http://localhost:8080/swagger-ui/index.html
echo   Actuator     :  http://localhost:8080/actuator/health
echo   Prometheus   :  http://localhost:8080/actuator/prometheus
echo.
echo   INFRASTRUCTURE
echo   --------------
echo   PostgreSQL   :  localhost:5432  (container: stockdb-postgres)
echo   Redis        :  localhost:6379  (container: stockdb-redis)
echo.
if "!START_OBS!"=="1" (
echo   OBSERVABILITY
echo   -------------
echo   Grafana      :  http://localhost:3000   login: admin / admin
echo   Prometheus   :  http://localhost:9090
echo   Loki         :  http://localhost:3100   (queried via Grafana)
echo   Tempo        :  http://localhost:3200   (queried via Grafana)
echo   Zipkin ingest:  localhost:9411          (apps send traces here)
echo.
)
echo   DEFAULT LOGINS
echo   --------------
echo   admin1  / pass1234
echo   client1 / pass1234  (Alice Johnson)
echo   client2 / pass1234  (Bob Smith)
echo.
echo   TIP: Run 'start-app.bat obs' to also start the observability stack.
echo.
pause
