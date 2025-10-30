#!/bin/bash
# Bella Queue 本地开发依赖服务启动脚本
# 启动 MySQL 和 Redis Docker 容器，应用程序请在 IDE 中启动

set -e

show_help() {
    echo "Bella Queue 本地开发依赖服务启动脚本"
    echo "启动 MySQL 和 Redis Docker 容器，应用程序请在 IDE 中启动"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help              显示帮助信息"
    echo "  --db-name NAME          数据库名称 (默认: bella_queue)"
    echo "  --db-username USER      数据库用户名 (默认: bella_user)"
    echo "  --db-password PASS      数据库密码 (默认: 123456)"
    echo "  --db-root-password PASS 数据库root密码 (默认: 123456)"
    echo "  --redis-password PASS   Redis密码 (默认: 123456)"
    echo ""
    echo "启动后在 IDE 中配置以下环境变量："
    echo "  BELLA_OPEN_API_BASE=https://your-bella-openapi-service.com"
    echo "  BELLA_OPEN_CONSOLE_KEY=your_bella_openapi_secret_key"
    echo "  SPRING_PROFILES_ACTIVE=local-dev"
    echo "  BELLA_DB_URL=jdbc:mysql://localhost:3306/\${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true"
    echo "  BELLA_DB_USERNAME=\${DB_USER}"
    echo "  BELLA_DB_PASSWORD=\${DB_PASSWORD}"
    echo "  QUEUE_REDIS_HOST=localhost"
    echo "  QUEUE_REDIS_PORT=6379"
    echo "  QUEUE_REDIS_PASSWORD=\${QUEUE_REDIS_PASSWORD}"
    echo "  QUEUE_SECRET_KEY=0123456789abcdef"
    echo "  BATCH_THREAD_SIZE=1"
    echo ""
    echo "  以下参数可使用默认值："
    echo "  BATCH_FILE_PATH=/tmp/bella-queue/batch"
    echo "  BATCH_MAX_SPLITTING=100"
    echo "  BELLA_QUEUE_LOAD_BATCH_SIZE=100"
}

# 默认配置
DB_NAME="bella_queue"
DB_USER="bella_user"
DB_PASSWORD="123456"
DB_ROOT_PASSWORD="123456"
QUEUE_REDIS_PASSWORD="123456"
QUEUE_SECRET_KEY="0123456789abcdef"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        --db-name)
            DB_NAME="$2"
            shift 2
            ;;
        --db-username)
            DB_USER="$2"
            shift 2
            ;;
        --db-password)
            DB_PASSWORD="$2"
            shift 2
            ;;
        --db-root-password)
            DB_ROOT_PASSWORD="$2"
            shift 2
            ;;
        --redis-password)
            QUEUE_REDIS_PASSWORD="$2"
            shift 2
            ;;
        *)
            echo "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

echo "======================================="
echo "   Bella Queue 本地开发依赖服务"
echo "======================================="

# 检查docker和docker-compose是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ 错误: Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ 错误: Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

echo "启动 MySQL 和 Redis 依赖服务..."

# 设置Docker Compose环境变量
export DB_ROOT_PASSWORD="$DB_ROOT_PASSWORD"
export DB_NAME="$DB_NAME"
export DB_USER="$DB_USER"
export DB_PASSWORD="$DB_PASSWORD"
export QUEUE_REDIS_PASSWORD="$QUEUE_REDIS_PASSWORD"

# 创建数据目录
mkdir -p data/mysql data/redis

# 启动服务
echo "正在启动 MySQL 和 Redis..."
docker-compose up -d

# 等待服务就绪
echo "等待服务启动完成..."
sleep 10

# 检查服务状态
echo "检查服务状态..."
docker-compose ps

echo ""
echo "✅ 依赖服务启动完成！"
echo "  MySQL: localhost:3306 (用户名: $DB_USER, 数据库: $DB_NAME, 密码: $DB_PASSWORD)"
echo "  Redis: localhost:6379 (密码: $QUEUE_REDIS_PASSWORD)"
echo ""
echo "请在 IDE 中配置以下环境变量后启动应用："
echo "BELLA_OPEN_API_BASE=https://your-bella-openapi-service.com;"
echo "BELLA_OPEN_CONSOLE_KEY=your_bella_openapi_secret_key;"
echo "SPRING_PROFILES_ACTIVE=local-dev;"
echo "BELLA_DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true;"
echo "BELLA_DB_USERNAME=$DB_USER;"
echo "BELLA_DB_PASSWORD=$DB_PASSWORD;"
echo "QUEUE_REDIS_HOST=localhost;"
echo "QUEUE_REDIS_PORT=6379;"
echo "QUEUE_REDIS_PASSWORD=$QUEUE_REDIS_PASSWORD;"
echo "QUEUE_SECRET_KEY=$QUEUE_SECRET_KEY;"
echo "BATCH_THREAD_SIZE=1;"
echo "BATCH_FILE_PATH=/tmp/bella-queue/batch;"
echo "BATCH_MAX_SPLITTING=100;"
echo "BELLA_QUEUE_LOAD_BATCH_SIZE=100"
echo ""
echo "提示: 可以直接复制上述环境变量到 IDEA 的 Run Configuration - Environment variables 中"
echo "注意: 需要将 BELLA_OPEN_API_BASE 和 BELLA_OPEN_CONSOLE_KEY 替换成你自己的配置"

