# Bella Queue 部署指南

提供两种部署方式：Docker 一键部署和本地开发部署。

## 🚀 部署方式选择

### 🐳 Docker 部署（推荐生产环境）

一键启动包含 MySQL、Redis、API 的完整服务栈，适合生产环境和快速体验。

### 🔧 本地开发部署

专为IDE开发调试设计：Docker容器运行MySQL和Redis，应用程序在IDE中启动。

---

## 🐳 Docker 部署

### 前置条件

- Docker 已安装并运行
- docker-compose 已安装
- 至少 4GB 可用内存
- 必须部署 bella-openapi 项目并确保其正常运行

### 启动服务

**最简启动：**

```bash
cd /path/to/bella-batch/api/deploy/docker
./start.sh \
  --bella-openapi-host https://your-bella-openapi-service.com \
  --bella-openapi-key your_bella_openapi_secret_key
```

**完整参数启动：**

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

**启动成功后访问：**

- 健康检查: http://localhost:${API_PORT}/actuator/health (默认 http://localhost:8080/actuator/health)

### 服务详情

**包含服务：**

- API服务 (端口: ${API_PORT}，默认 8080)
- MySQL 8.0 (端口: 3306，仅本地访问)
- Redis 7.0 (端口: 6379，仅本地访问)

**数据持久化：**

- MySQL数据: `./data/mysql`
- Redis数据: `./data/redis`
- 应用日志: `./data/api/logs`

---

## 🔧 本地开发部署

专为IDE开发调试设计：Docker容器运行MySQL和Redis，应用程序在IDE中启动。

### 前置条件

- Docker 已安装并运行
- docker-compose 已安装
- Java 11+ 已安装
- IDE（IntelliJ IDEA / Eclipse / VS Code等）
- 必须部署 bella-openapi 项目并确保其正常运行

### 启动步骤

**1. 启动依赖服务（MySQL + Redis）：**

```bash
cd /path/to/bella-batch/api/deploy/local-dev
./start.sh
```

**自定义数据库配置：**

```bash
./start.sh \
  --db-name "bella_queue" \
  --db-username "bella_user" \
  --db-password "your-db-password" \
  --db-root-password "your-db-root-password" \
  --redis-password "your-redis-password"
```

**2. 在IDE中配置环境变量：**

脚本执行后会显示需要在IDE中配置的环境变量，复制到IDE的Run Configuration中：

**必需配置的环境变量：**

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

**以下参数可使用默认值：**

```
BATCH_FILE_PATH=/tmp/bella-queue/batch
BATCH_MAX_SPLITTING=100
BELLA_QUEUE_LOAD_BATCH_SIZE=100
```

**IDEA Environment variables 完整配置（复制即可）：**

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

**3. 在IDE中运行Spring Boot主类**

享受完整的IDE开发体验：断点调试、热重载、代码提示等。

### 服务详情

**包含服务：**

- 应用程序 (IDE本地运行)
- MySQL 8.0 (Docker容器，端口: 3306)
- Redis 7.0 (Docker容器，端口: 6379)

**数据持久化：**

- MySQL数据: `./data/mysql`
- Redis数据: `./data/redis`

---

## 📋 参数说明

**Docker部署参数：**

| 参数                     | 说明                | 必需 | 默认值              |
|------------------------|-------------------|----|------------------|
| `--bella-openapi-host` | Bella OpenAPI服务地址 | ✅  | -                |
| `--bella-openapi-key`  | Bella OpenAPI密钥   | ✅  | -                |
| `--api-port`           | API服务端口           | ❌  | 8080             |
| `--db-name`            | 数据库名称             | ❌  | bella_queue      |
| `--db-username`        | 数据库用户名            | ❌  | bella_user       |
| `--db-password`        | 数据库密码             | ❌  | 123456           |
| `--db-root-password`   | 数据库root密码         | ❌  | 123456           |
| `--redis-password`     | Redis密码           | ❌  | 123456           |
| `--queue-secret-key`   | 队列加密密钥(16位)       | ❌  | 0123456789abcdef |
| `--batch-thread-size`  | 批处理拆分线程数          | ❌  | 1                |
| `--build`              | 强制重新构建镜像          | ❌  | -                |

**本地开发部署参数：**

| 参数                   | 说明        | 必需 | 默认值         |
|----------------------|-----------|----|-------------| 
| `--db-name`          | 数据库名称     | ❌  | bella_queue |
| `--db-username`      | 数据库用户名    | ❌  | bella_user  |
| `--db-password`      | 数据库密码     | ❌  | 123456      |
| `--db-root-password` | 数据库root密码 | ❌  | 123456      |
| `--redis-password`   | Redis密码   | ❌  | 123456      |

**环境变量（高级配置，建议使用默认值）：**

| 环境变量                          | 说明        | 默认值                    |
|-------------------------------|-----------|------------------------|
| `BELLA_QUEUE_BATCH_FILE_PATH` | 批处理文件存储路径 | /tmp/bella-queue/batch |
| `BATCH_MAX_SPLITTING`         | 批处理最大分割数  | 100                    |
| `BELLA_QUEUE_LOAD_BATCH_SIZE` | 加载批处理大小   | 100                    |

**使用环境变量自定义配置：**

**Docker 部署：**

```bash
export BELLA_QUEUE_BATCH_FILE_PATH="/custom/batch/path"
export BATCH_MAX_SPLITTING="200"
export BELLA_QUEUE_LOAD_BATCH_SIZE="1000"

cd docker && ./start.sh --bella-openapi-host https://your-bella-openapi-service.com --bella-openapi-key your_bella_openapi_secret_key
```

**本地开发部署：**
在IDE的Run Configuration中添加以下环境变量：

```
BELLA_QUEUE_BATCH_FILE_PATH=/custom/batch/path
BATCH_MAX_SPLITTING=200
BELLA_QUEUE_LOAD_BATCH_SIZE=1000
```

## 📊 服务管理

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
docker-compose logs -f api      # Docker部署查看API日志

# 停止服务
docker-compose down

# 重新构建并启动（Docker部署）
./start.sh --build

# 完全清理（包括数据）
docker-compose down -v && rm -rf data/

# 重启服务
docker-compose restart
```

## 🔍 健康检查

```bash
# API健康检查
curl http://localhost:${API_PORT}/actuator/health

# 监控指标  
curl http://localhost:${API_PORT}/actuator/prometheus
```

## 🔧 故障排查

**常见问题：**

1. **端口冲突** → `./start.sh --api-port 8090`
2. **服务启动失败** → 检查 `docker-compose logs`
3. **缺少API配置** → 检查 `--bella-openapi-host` 和 `--bella-openapi-key` 参数
4. **内存不足（Docker部署）** → 确保至少4GB可用内存
5. **IDE环境变量配置错误（本地开发）** → 检查IDE Run Configuration中的环境变量设置
6. **数据库连接失败** → 确保Docker容器正常运行

---

**需要帮助？** 查看项目 Issues 或提交 Bug 报告。
