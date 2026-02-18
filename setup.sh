#!/bin/bash
# Stock Brokerage Application - Quick Setup Script for macOS/Linux
# This script helps set up the application on a new machine

set -e

echo "========================================"
echo "Stock Brokerage Application Setup"
echo "========================================"
echo ""

# Check if Docker is running
echo "[1/6] Checking Docker..."
if ! docker ps > /dev/null 2>&1; then
    echo "ERROR: Docker is not running. Please start Docker and try again."
    exit 1
fi
echo "✓ Docker is running"

# Start database containers
echo ""
echo "[2/6] Starting database containers..."
docker-compose up -d
echo "✓ Database containers started"

# Wait for databases to be ready
echo ""
echo "[3/6] Waiting for databases to be ready (15 seconds)..."
sleep 15

# Build backend
echo ""
echo "[4/6] Building backend (this may take a few minutes)..."
mvn clean package -DskipTests
echo "✓ Backend build successful"

# Install frontend dependencies
echo ""
echo "[5/6] Installing frontend dependencies..."
cd frontend
npm install
cd ..
echo "✓ Frontend dependencies installed"

# Done
echo ""
echo "[6/6] Setup complete!"
echo ""
echo "========================================"
echo "Next Steps:"
echo "========================================"
echo ""
echo "1. Start the backend:"
echo "   mvn spring-boot:run"
echo ""
echo "2. In a new terminal, start the frontend:"
echo "   cd frontend"
echo "   npm run dev"
echo ""
echo "3. Access the application:"
echo "   Frontend: http://localhost:4200"
echo "   Backend:  http://localhost:8080"
echo ""
echo "Default login:"
echo "   Username: client1"
echo "   Password: pass1234"
echo "========================================"
echo ""
