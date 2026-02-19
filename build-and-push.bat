@echo off
REM ─────────────────────────────────────────────────────────
REM  Build both Docker images and push to Docker Hub
REM  Usage: build-and-push.bat [tag]
REM  Default tag: latest
REM ─────────────────────────────────────────────────────────

set REPO=vkdocker
set BACKEND_IMAGE=%REPO%/stock-brokerage-backend
set FRONTEND_IMAGE=%REPO%/stock-brokerage-frontend
set ALLINONE_IMAGE=%REPO%/stock-brokerage-allinone
set TAG=%1
if "%TAG%"=="" set TAG=latest

echo.
echo =====================================================
echo  Stock Brokerage — Docker Build ^& Push
echo  Backend   : %BACKEND_IMAGE%:%TAG%
echo  Frontend  : %FRONTEND_IMAGE%:%TAG%
echo  All-in-one: %ALLINONE_IMAGE%:%TAG%
echo =====================================================
echo.

REM ─── Login check ─────────────────────────────────────
echo [1/4] Checking Docker Hub login...
docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker Desktop is not running.
    exit /b 1
)
docker login
if errorlevel 1 (
    echo ERROR: Docker Hub login failed.
    exit /b 1
)

REM ─── Build backend ───────────────────────────────────
echo.
echo [2/4] Building backend image...
docker build -f Dockerfile.backend -t %BACKEND_IMAGE%:%TAG% -t %BACKEND_IMAGE%:latest .
if errorlevel 1 (
    echo ERROR: Backend build failed.
    exit /b 1
)
echo OK — backend built.

REM ─── Build frontend ──────────────────────────────────
echo.
echo [3/5] Building frontend image...
docker build -f Dockerfile.frontend -t %FRONTEND_IMAGE%:%TAG% -t %FRONTEND_IMAGE%:latest .
if errorlevel 1 (
    echo ERROR: Frontend build failed.
    exit /b 1
)
echo OK — frontend built.

REM ─── Build all-in-one ────────────────────────────────────────────
echo.
echo [4/5] Building all-in-one image (this takes several minutes)...
docker build -f Dockerfile.allinone -t %ALLINONE_IMAGE%:%TAG% -t %ALLINONE_IMAGE%:latest .
if errorlevel 1 (
    echo ERROR: All-in-one build failed.
    exit /b 1
)
echo OK — all-in-one built.

REM ─── Push all ────────────────────────────────────────────────────
echo.
echo [5/5] Pushing images to Docker Hub...
docker push %BACKEND_IMAGE%:%TAG%
docker push %BACKEND_IMAGE%:latest
docker push %FRONTEND_IMAGE%:%TAG%
docker push %FRONTEND_IMAGE%:latest
docker push %ALLINONE_IMAGE%:%TAG%
docker push %ALLINONE_IMAGE%:latest

if errorlevel 1 (
    echo ERROR: Push failed.
    exit /b 1
)

echo.
echo =====================================================
echo  Done! Images are live on Docker Hub:
echo    docker pull %BACKEND_IMAGE%:%TAG%
echo    docker pull %FRONTEND_IMAGE%:%TAG%
echo    docker pull %ALLINONE_IMAGE%:%TAG%
echo.
echo  All-in-one quick start (zero config, runs standalone):
echo    docker run -d -p 80:80 %ALLINONE_IMAGE%:%TAG%
echo =====================================================
