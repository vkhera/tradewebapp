@echo off
setlocal enabledelayedexpansion

REM ====================================================================
REM  Stock Brokerage - Full Application Shutdown
REM  Stop order: Angular -> Spring Boot -> Redis -> PostgreSQL
REM ====================================================================

echo.
echo ====================================================================
echo   Stock Brokerage Application - Stopping All Services
echo ====================================================================
echo.

REM ── Step 1: Stop Angular Frontend (port 4200) ─────────────────────
echo [1/4] Stopping Angular frontend (port 4200)...
set FE_FOUND=0
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":4200.*LISTENING"') do (
    taskkill /PID %%p /T /F >nul 2>&1
    echo   Stopped process PID %%p (Angular / node)
    set FE_FOUND=1
)
if "!FE_FOUND!"=="0" echo   Angular frontend was not running on port 4200.

REM Give node a moment to release the port
timeout /t 2 /nobreak >nul

REM ── Step 2: Stop Spring Boot Backend (port 8080) ──────────────────
echo.
echo [2/4] Stopping Spring Boot backend (port 8080)...
set BE_FOUND=0
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":8080.*LISTENING"') do (
    taskkill /PID %%p /T /F >nul 2>&1
    echo   Stopped process PID %%p (Java / Spring Boot)
    set BE_FOUND=1
)
if "!BE_FOUND!"=="0" echo   Spring Boot backend was not running on port 8080.

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

echo.
echo ====================================================================
echo   All services stopped.
echo ====================================================================
echo.
echo   Tip: Run start-app.bat to start everything again.
echo   Tip: Docker containers are stopped but data is preserved in volumes.
echo        Run 'docker compose down -v' to also remove volumes/data.
echo.
pause
