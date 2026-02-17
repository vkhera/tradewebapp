@echo off
REM Stock Brokerage Application - Quick Setup Script for Windows
REM This script helps set up the application on a new machine

echo ========================================
echo Stock Brokerage Application Setup
echo ========================================
echo.

REM Check if Docker is running
echo [1/6] Checking Docker...
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not running. Please start Docker Desktop and try again.
    pause
    exit /b 1
)
echo Docker is running OK

REM Start database containers
echo.
echo [2/6] Starting database containers...
docker-compose up -d
if %errorlevel% neq 0 (
    echo ERROR: Failed to start Docker containers
    pause
    exit /b 1
)
echo Database containers started

REM Wait for databases to be ready
echo.
echo [3/6] Waiting for databases to be ready (15 seconds)...
timeout /t 15 /nobreak >nul

REM Build backend
echo.
echo [4/6] Building backend (this may take a few minutes)...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Backend build failed
    pause
    exit /b 1
)
echo Backend build successful

REM Install frontend dependencies
echo.
echo [5/6] Installing frontend dependencies...
cd frontend
call npm install
if %errorlevel% neq 0 (
    echo ERROR: Frontend npm install failed
    cd ..
    pause
    exit /b 1
)
cd ..
echo Frontend dependencies installed

REM Done
echo.
echo [6/6] Setup complete!
echo.
echo ========================================
echo Next Steps:
echo ========================================
echo.
echo 1. Start the backend:
echo    mvn spring-boot:run
echo.
echo 2. In a new terminal, start the frontend:
echo    cd frontend
echo    npm run dev
echo.
echo 3. Access the application:
echo    Frontend: http://localhost:4200
echo    Backend:  http://localhost:8080
echo.
echo Default login:
echo    Username: client1
echo    Password: pass1234
echo ========================================
echo.
pause
