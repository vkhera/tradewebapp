@echo off
setlocal enabledelayedexpansion

REM ====================================================================
REM  Stock Brokerage - Full Application Shutdown
REM  Stop order: Frontend -> Backend -> Redis -> PostgreSQL (all Docker)
REM
REM  Usage:
REM    stop-app.bat          - stop the application only
REM    stop-app.bat obs      - also stop the observability stack
REM    stop-app.bat all      - stop everything (app + observability)
REM ====================================================================

REM ── Parse optional argument ───────────────────────────────────────────────
set STOP_OBS=0
if /I "%~1"=="obs" set STOP_OBS=1
if /I "%~1"=="all" set STOP_OBS=1

echo.
echo ====================================================================
echo   Stock Brokerage Application - Stopping All Services
echo ====================================================================
echo.

REM ── Step 1: Stop Frontend Docker container ────────────────────────
echo [1/4] Stopping Angular frontend container...
docker inspect stockbrokerage-frontend >nul 2>&1
if %errorlevel% equ 0 (
    docker stop stockbrokerage-frontend >nul 2>&1
    echo   Frontend container stopped.
) else (
    echo   Frontend container 'stockbrokerage-frontend' not found - skipping.
)

REM ── Step 2: Stop Backend Docker container ─────────────────────────
echo.
echo [2/4] Stopping Spring Boot backend container...
docker inspect stockbrokerage-backend >nul 2>&1
if %errorlevel% equ 0 (
    docker stop stockbrokerage-backend >nul 2>&1
    echo   Backend container stopped.
) else (
    echo   Backend container 'stockbrokerage-backend' not found - skipping.
)

REM ── Step 3: Stop Redis ────────────────────────────────────────────
echo.
echo [3/4] Stopping Redis Docker container...
docker inspect stockdb-redis >nul 2>&1
if %errorlevel% equ 0 (
    docker stop stockdb-redis >nul 2>&1
    echo   Redis stopped.
) else (
    echo   Redis container 'stockdb-redis' not found - skipping.
)

REM ── Step 4: Stop PostgreSQL ───────────────────────────────────────
echo.
echo [4/4] Stopping PostgreSQL Docker container...
docker inspect stockdb-postgres >nul 2>&1
if %errorlevel% equ 0 (
    docker stop stockdb-postgres >nul 2>&1
    echo   PostgreSQL stopped.
) else (
    echo   PostgreSQL container 'stockdb-postgres' not found - skipping.
)

REM ── Step 5 (optional): Stop Observability Stack ───────────────────
if "!STOP_OBS!"=="1" (
    echo.
    echo [obs] Stopping observability stack (Grafana / Prometheus / Loki / Tempo)...
    docker compose -f docker-compose.observability.yml down
    if !errorlevel! equ 0 (
        echo   Observability stack stopped.
    ) else (
        echo   Observability stack was not running or failed to stop.
    )
)

echo.
echo ====================================================================
echo   Services stopped.
echo ====================================================================
echo.
echo   TIP: Run 'start-app.bat' to start the application again.
echo   TIP: Run 'start-app.bat obs' to also start the observability stack.
echo   TIP: Docker volumes are preserved. Use 'docker compose down -v' to
echo        also wipe data (PostgreSQL, Redis).
echo.
pause
