# Deployment Guide - Stock Brokerage Application

---

## ⭐ Recommended: Run from Docker Hub (Zero Build Required)

Both images are published on Docker Hub.  
Pull them on **any machine with Docker** — no Java, Maven, or Node.js needed.

### Images

| Image | Tag | Size |
|---|---|---|
| `vkdocker/stock-brokerage-backend` | `latest` | ~394 MB |
| `vkdocker/stock-brokerage-frontend` | `latest` | ~49 MB |

> **Private repositories:** Go to [hub.docker.com](https://hub.docker.com) › your repositories › Settings › Make Private for each image after pushing. Free Docker Hub accounts include 1 private repo; a Pro plan ($9/month) covers unlimited private repos.

---

### Step 1 — Prerequisites on the target machine

- **Docker Desktop** (Windows/Mac) or **Docker Engine** (Linux) — nothing else needed
- Internet connection to pull images from Docker Hub

### Step 2 — Get the compose file

Copy `docker-compose.yml` from this repo to the target machine, **or** create it inline:

```bash
# Option A: copy docker-compose.yml from this project (only 1 file needed)
# Option B: pull from git
git clone <your-repo-url> --depth 1
cd ws-trd-1
```

### Step 3 — (Optional) Create throttle config

The backend loads a live-editable rate-limit config.  
Place a `config/throttle-config.yaml` alongside `docker-compose.yml`:

```bash
mkdir config
# Then create config/throttle-config.yaml  — copy from this repo
# Without the file, built-in defaults apply (1 TPS global, per-service overrides as coded)
```

### Step 4 — Login and pull (private images)

```bash
docker login --username vkdocker   # enter password when prompted
docker pull vkdocker/stock-brokerage-backend:latest
docker pull vkdocker/stock-brokerage-frontend:latest
```

### Step 5 — Run the full stack

```bash
docker compose up -d
```

Docker will start 4 containers in dependency order:

```
postgres  →  redis  →  backend (port 8080)  →  frontend/nginx (port 80)
```

Wait ~90 seconds for the backend to initialise (schema creation + data seeding).

### Step 6 — Access the application

| URL | What |
|---|---|
| `http://localhost` | Angular frontend (login page) |
| `http://localhost/swagger-ui/` | Swagger UI (proxied through nginx) |
| `http://localhost:8080/api/...` | Backend REST API (direct) |

### Default login credentials

| Role | Username | Password |
|---|---|---|
| Admin | `admin1` | `pass1234` |
| Client | `client1` … `client5` | `pass1234` |

### Stop / restart

```bash
docker compose down          # stop (data volumes preserved)
docker compose down -v       # stop and DELETE all data
docker compose pull && docker compose up -d   # update to latest images
```

### JVM tuning via environment variable

Add `JAVA_OPTS` to the `backend` service in `docker-compose.yml`:

```yaml
environment:
  JAVA_OPTS: "-Xmx512m -Xms256m"
```

---

## Creating a Portable Package

### Method 1: Using Git (Recommended)

If you have access to a Git repository:

1. **On the original machine:**
   ```bash
   git add .
   git commit -m "Prepare for deployment"
   git push
   ```

2. **On the new machine:**
   ```bash
   git clone <repository-url>
   cd <repository-name>
   ```

### Method 2: Manual ZIP Package

If you don't have Git access, create a ZIP file:

#### On Windows:
```powershell
# Create a ZIP excluding build artifacts
$exclude = @('node_modules', 'target', 'dist', '.angular', 'logs', '.git')
$files = Get-ChildItem -Recurse | Where-Object { 
    $file = $_
    -not ($exclude | Where-Object { $file.FullName -like "*$_*" })
}
Compress-Archive -Path $files -DestinationPath stock-brokerage-package.zip
```

#### On macOS/Linux:
```bash
# Create a ZIP excluding build artifacts
zip -r stock-brokerage-package.zip . \
  -x "node_modules/*" \
  -x "target/*" \
  -x "frontend/dist/*" \
  -x "frontend/.angular/*" \
  -x "logs/*" \
  -x ".git/*"
```

## Setting Up on a New Machine

### Prerequisites

Ensure the following are installed on the new machine:

- **Java 23** (or Java 17+)
- **Maven 3.9+**
- **Node.js 18+** (includes npm)
- **Docker Desktop**

### Quick Setup

#### On Windows:
1. Extract the package to your desired location
2. Open Command Prompt as Administrator
3. Navigate to the project folder
4. Run: `setup.bat`
5. Follow the on-screen instructions

#### On macOS/Linux:
1. Extract the package to your desired location
2. Open Terminal
3. Navigate to the project folder
4. Make the script executable: `chmod +x setup.sh`
5. Run: `./setup.sh`
6. Follow the on-screen instructions

### Manual Setup

If you prefer to set up manually or the scripts don't work:

1. **Start Docker containers:**
   ```bash
   docker-compose up -d
   ```

2. **Wait for databases** (about 15-30 seconds)

3. **Build the backend:**
   ```bash
   mvn clean package -DskipTests
   ```

4. **Install frontend dependencies:**
   ```bash
   cd frontend
   npm install
   cd ..
   ```

5. **Run the backend:**
   ```bash
   mvn spring-boot:run
   ```

6. **In a new terminal, run the frontend:**
   ```bash
   cd frontend
   npm run dev
   ```

7. **Access the application:**
   - Frontend: http://localhost:4200
   - Backend API: http://localhost:8080

## What's Included

The portable package includes:

- ✅ Complete source code (backend + frontend)
- ✅ Docker Compose configuration for PostgreSQL and Redis
- ✅ Database schema (auto-created by JPA)
- ✅ Initial data (5 test clients, 2 admins, rules)
- ✅ All configuration files
- ✅ Setup automation scripts
- ✅ Complete documentation

## Default Test Accounts

### Clients (for trading):
| Username | Password | Initial Balance |
|----------|----------|-----------------|
| client1  | pass1234 | $10,000         |
| client2  | pass1234 | $10,000         |
| client3  | pass1234 | $10,000         |
| client4  | pass1234 | $10,000         |
| client5  | pass1234 | $10,000         |

### Admins:
| Username | Password | Role                |
|----------|----------|---------------------|
| admin1   | admin123 | Manage Clients      |
| admin2   | admin123 | Manage Rules        |

## Verifying the Setup

After starting both backend and frontend:

1. **Check Docker containers:**
   ```bash
   docker ps
   ```
   Should show: postgres (port 5432) and redis (port 6379)

2. **Check backend:**
   - Visit: http://localhost:8080/swagger-ui.html
   - Should see API documentation

3. **Check frontend:**
   - Visit: http://localhost:4200
   - Should see login page

4. **Test login:**
   - Use: client1 / pass1234
   - Should successfully log in and see portfolio

## Troubleshooting

### Docker containers won't start
```bash
# Stop any existing containers
docker-compose down

# Remove volumes and restart fresh
docker-compose down -v
docker-compose up -d
```

### Port already in use
Check if ports are occupied:
- 5432 (PostgreSQL)
- 6379 (Redis)
- 8080 (Backend)
- 4200 (Frontend)

Kill processes using these ports or modify the configuration.

### Backend build fails
```bash
# Clean Maven cache
mvn clean

# Try again with verbose output
mvn clean package -X
```

### Frontend build fails
```bash
cd frontend

# Clear npm cache
npm cache clean --force

# Remove node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

## Data Persistence

- **PostgreSQL data** is stored in Docker volume `postgres-data`
- **Redis data** is ephemeral (cache only)
- **To reset all data:** Run `docker-compose down -v` (WARNING: deletes all data)

## Production Deployment Notes

For production deployment:

1. **Update application.yml:**
   - Change database credentials
   - Use external database (not Docker)
   - Configure Redis cluster
   - Set proper CORS origins
   - Enable HTTPS

2. **Build production artifacts:**
   ```bash
   # Backend
   mvn clean package -Pprod

   # Frontend
   cd frontend
   npm run build:ssr
   ```

3. **Deploy artifacts:**
   - Backend: `target/stock-brokerage-1.0-SNAPSHOT.jar`
   - Frontend: `frontend/dist/` folder

4. **Environment variables:**
   Set these on the production server:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `SPRING_DATA_REDIS_HOST`
   - `SPRING_DATA_REDIS_PORT`

## Support

For issues or questions, refer to the main [README.md](README.md) file.
