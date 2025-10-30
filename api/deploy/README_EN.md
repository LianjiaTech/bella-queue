# Bella Queue Deployment Guide

Provides two deployment methods: Docker one-click deployment and local development deployment.

## 🚀 Deployment Method Selection

### 🐳 Docker Deployment (Recommended for Production)

One-click startup of a complete service stack including MySQL, Redis, and API, suitable for production environments and
quick experience.

### 🔧 Local Development Deployment

Designed specifically for IDE development and debugging: MySQL and Redis run in Docker containers, applications start in
IDE.

---

## 🐳 Docker Deployment

### Prerequisites

- Docker installed and running
- docker-compose installed
- At least 4GB available memory
- bella-openapi project must be deployed and running properly

### Start Services

**Quick Start:**

```bash
cd /path/to/bella-batch/api/deploy/docker
./start.sh \
  --bella-openapi-host https://your-bella-openapi-service.com \
  --bella-openapi-key your_bella_openapi_secret_key
```

**Full Parameter Start:**

```bash
./start.sh \
  --api-port 8080 \
  --db-name "bella_queue" \
  --db-username "bella_user" \
  --db-password "your-db-password" \
  --db-root-password "your-db-root-password" \
  --redis-password "your-redis-password" \
  --queue-secret-key "your-queue-secret-key" \
  --batch-thread-size 1 \
  --bella-openapi-host https://your-bella-openapi-service.com \
  --bella-openapi-key your_bella_openapi_secret_key
```

**Access after startup:**

- Health check: http://localhost:${API_PORT}/actuator/health (default http://localhost:8080/actuator/health)

### Service Details

**Included Services:**

- API Service (Port: ${API_PORT}, default 8080)
- MySQL 8.0 (Port: 3306, local access only)
- Redis 7.0 (Port: 6379, local access only)

**Data Persistence:**

- MySQL data: `./data/mysql`
- Redis data: `./data/redis`
- Application logs: `./data/api/logs`

---

## 🔧 Local Development Deployment

Designed specifically for IDE development and debugging: MySQL and Redis run in Docker containers, applications start in
IDE.

### Prerequisites

- Docker installed and running
- docker-compose installed
- Java 11+ installed
- IDE (IntelliJ IDEA / Eclipse / VS Code, etc.)
- bella-openapi project must be deployed and running properly

### Startup Steps

**1. Start dependency services (MySQL + Redis):**

```bash
cd /path/to/bella-batch/api/deploy/local-dev
./start.sh
```

**Custom database configuration:**

```bash
./start.sh \
  --db-name "bella_queue" \
  --db-username "bella_user" \
  --db-password "your-db-password" \
  --db-root-password "your-db-root-password" \
  --redis-password "your-redis-password"
```

**2. Configure environment variables in IDE:**

After script execution, it will display environment variables needed for IDE configuration, copy to IDE's Run
Configuration:

**Required environment variables:**

```
BELLA_OPEN_API_BASE=https://your-bella-openapi-service.com
BELLA_OPEN_CONSOLE_KEY=your_bella_openapi_secret_key
SPRING_PROFILES_ACTIVE=local-dev
BELLA_DB_URL=jdbc:mysql://localhost:3306/bella_queue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
BELLA_DB_USERNAME=bella_user
BELLA_DB_PASSWORD=123456
QUEUE_REDIS_HOST=localhost
QUEUE_REDIS_PORT=6379
QUEUE_REDIS_PASSWORD=123456
QUEUE_SECRET_KEY=0123456789abcdef
BATCH_THREAD_SIZE=1
```

**Parameters that can use default values:**

```
BATCH_FILE_PATH=/tmp/bella-queue/batch
BATCH_MAX_SPLITTING=100
BELLA_QUEUE_LOAD_BATCH_SIZE=100
```

**Complete IDEA Environment variables configuration (copy ready):**

```
BELLA_OPEN_API_BASE=https://your-bella-openapi-service.com;
BELLA_OPEN_CONSOLE_KEY=your_bella_openapi_secret_key;
SPRING_PROFILES_ACTIVE=local-dev;
BELLA_DB_URL=jdbc:mysql://localhost:3306/bella_queue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true;
BELLA_DB_USERNAME=bella_user;
BELLA_DB_PASSWORD=123456;
QUEUE_REDIS_HOST=localhost;
QUEUE_REDIS_PORT=6379;
QUEUE_REDIS_PASSWORD=123456;
QUEUE_SECRET_KEY=0123456789abcdef;
BATCH_THREAD_SIZE=1;
BATCH_FILE_PATH=/tmp/bella-queue/batch;
BATCH_MAX_SPLITTING=100;
BELLA_QUEUE_LOAD_BATCH_SIZE=100
```

**3. Run Spring Boot main class in IDE**

Enjoy the complete IDE development experience: breakpoint debugging, hot reload, code hints, etc.

### Service Details

**Included Services:**

- Application (IDE local run)
- MySQL 8.0 (Docker container, port: 3306)
- Redis 7.0 (Docker container, port: 6379)

**Data Persistence:**

- MySQL data: `./data/mysql`
- Redis data: `./data/redis`

---

## 📋 Parameter Description

**Docker Deployment Parameters:**

| Parameter              | Description                   | Required | Default Value    |
|------------------------|-------------------------------|----------|------------------|
| `--bella-openapi-host` | Bella OpenAPI service URL     | ✅        | -                |
| `--bella-openapi-key`  | Bella OpenAPI key             | ✅        | -                |
| `--api-port`           | API service port              | ❌        | 8080             |
| `--db-name`            | Database name                 | ❌        | bella_queue      |
| `--db-username`        | Database username             | ❌        | bella_user       |
| `--db-password`        | Database password             | ❌        | 123456           |
| `--db-root-password`   | Database root password        | ❌        | 123456           |
| `--redis-password`     | Redis password                | ❌        | 123456           |
| `--queue-secret-key`   | Queue encryption key (16-bit) | ❌        | 0123456789abcdef |
| `--batch-thread-size`  | Batch thread count            | ❌        | 1                |
| `--build`              | Force rebuild image           | ❌        | -                |

**Local Development Deployment Parameters:**

| Parameter            | Description            | Required | Default Value |
|----------------------|------------------------|----------|---------------|
| `--db-name`          | Database name          | ❌        | bella_queue   |
| `--db-username`      | Database username      | ❌        | bella_user    |
| `--db-password`      | Database password      | ❌        | 123456        |
| `--db-root-password` | Database root password | ❌        | 123456        |
| `--redis-password`   | Redis password         | ❌        | 123456        |

**Environment Variables (Advanced configuration, recommend using default values):**

| Environment Variable          | Description               | Default Value          |
|-------------------------------|---------------------------|------------------------|
| `BELLA_QUEUE_BATCH_FILE_PATH` | Batch file storage path   | /tmp/bella-queue/batch |
| `BATCH_MAX_SPLITTING`         | Maximum batch split count | 100                    |
| `BELLA_QUEUE_LOAD_BATCH_SIZE` | Load batch size           | 100                    |

**Using custom environment variables:**

**Docker Deployment:**

```bash
export BELLA_QUEUE_BATCH_FILE_PATH="/custom/batch/path"
export BATCH_MAX_SPLITTING="200"
export BELLA_QUEUE_LOAD_BATCH_SIZE="1000"

cd docker && ./start.sh --bella-openapi-host https://your-bella-openapi-service.com --bella-openapi-key your_bella_openapi_secret_key
```

**Local Development Deployment:**
Add the following environment variables in IDE's Run Configuration:

```
BELLA_QUEUE_BATCH_FILE_PATH=/custom/batch/path
BATCH_MAX_SPLITTING=200
BELLA_QUEUE_LOAD_BATCH_SIZE=1000
```

## 📊 Service Management

```bash
# Check service status
docker-compose ps

# View logs
docker-compose logs -f
docker-compose logs -f api      # Docker deployment API logs

# Stop services
docker-compose down

# Rebuild and start (Docker deployment)
./start.sh --build

# Complete cleanup (including data)
docker-compose down -v && rm -rf data/

# Restart services
docker-compose restart
```

## 🔍 Health Check

```bash
# API health check
curl http://localhost:${API_PORT}/actuator/health

# Monitoring metrics  
curl http://localhost:${API_PORT}/actuator/prometheus
```

## 🔧 Troubleshooting

**Common Issues:**

1. **Port conflict** → `./start.sh --api-port 8090`
2. **Service startup failure** → Check `docker-compose logs`
3. **Missing API configuration** → Check `--bella-openapi-host` and `--bella-openapi-key` parameters
4. **Insufficient memory (Docker deployment)** → Ensure at least 4GB available memory
5. **IDE environment variable configuration error (Local development)** → Check IDE Run Configuration environment
   variable settings
6. **Database connection failure** → Ensure Docker containers are running properly

---

**Need help?** Check project Issues or submit bug reports.
